package com.edifies.attendance.modules.backup.service;

import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.modules.backup.dto.*;
import com.edifies.attendance.modules.backup.model.BackupManifest;
import com.edifies.attendance.modules.backup.model.BackupStatus;
import com.edifies.attendance.modules.backup.model.BackupType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DisasterRecoveryCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryCoordinator.class);
    private static final DateTimeFormatter ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.of("UTC"));

    private final DatabaseBackupService databaseBackupService;
    private final PhotoBackupService photoBackupService;
    private final BackupEncryptionService backupEncryptionService;
    private final BackupRetentionService backupRetentionService;
    private final BackupRestoreService backupRestoreService;

    private final Path backupStorageRoot;
    private final int rpoTargetHours;
    private final int rtoTargetMinutes;
    private final ObjectMapper objectMapper;
    private final ReentrantLock backupLock = new ReentrantLock();

    private volatile RestoreValidationResponse lastRestoreTestResponse;

    public DisasterRecoveryCoordinator(
            DatabaseBackupService databaseBackupService,
            PhotoBackupService photoBackupService,
            BackupEncryptionService backupEncryptionService,
            BackupRetentionService backupRetentionService,
            BackupRestoreService backupRestoreService,
            @Value("${attendance.backup.storage-root:backups}") String storageRoot,
            @Value("${attendance.backup.rpo-target-hours:24}") int rpoTargetHours,
            @Value("${attendance.backup.rto-target-minutes:5}") int rtoTargetMinutes
    ) {
        this.databaseBackupService = databaseBackupService;
        this.photoBackupService = photoBackupService;
        this.backupEncryptionService = backupEncryptionService;
        this.backupRetentionService = backupRetentionService;
        this.backupRestoreService = backupRestoreService;
        this.backupStorageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
        this.rpoTargetHours = rpoTargetHours;
        this.rtoTargetMinutes = rtoTargetMinutes;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(backupStorageRoot)) {
                Files.createDirectories(backupStorageRoot);
                log.info("Initialized backup storage root at: {}", backupStorageRoot);
            }
        } catch (IOException e) {
            log.error("Failed to initialize backup storage root: {}", backupStorageRoot, e);
        }
    }

    public BackupManifest triggerBackup(BackupType type, String notes, Boolean encrypt, String performedBy) throws Exception {
        if (!backupLock.tryLock()) {
            throw new IllegalStateException("A backup operation is already actively running. Concurrent backups are rejected.");
        }

        Instant startTime = Instant.now();
        String backupId = "backup_" + ID_FORMATTER.format(startTime);
        Path backupDir = backupStorageRoot.resolve(backupId);
        Files.createDirectories(backupDir);

        BackupManifest manifest = new BackupManifest();
        manifest.setBackupId(backupId);
        manifest.setCreatedAt(startTime);
        manifest.setType(type != null ? type : BackupType.MANUAL);
        manifest.setStatus(BackupStatus.IN_PROGRESS);
        manifest.setCreatedBy(performedBy != null ? performedBy : "SYSTEM");
        manifest.setVerificationNotes(notes);

        try {
            log.info("Starting backup operation: id={}, type={}, createdBy={}", backupId, manifest.getType(), manifest.getCreatedBy());

            // 1. Database snapshot
            Path dbDumpFile = backupDir.resolve("database.sql");
            DatabaseBackupService.DumpResult dbResult = databaseBackupService.createDatabaseDump(dbDumpFile);
            manifest.setDatabaseName("attendance");
            manifest.setDatabaseSizeBytes(dbResult.getSizeBytes());
            manifest.setDatabaseChecksum(dbResult.getSha256());
            manifest.setRecordCounts(dbResult.getRecordCounts());

            // 2. Photos archive
            Path photosZipFile = backupDir.resolve("photos.zip");
            PhotoBackupService.PhotoArchiveResult photoResult = photoBackupService.createPhotoArchive(photosZipFile);
            manifest.setPhotoCount(photoResult.getFileCount());
            manifest.setPhotoSizeBytes(photoResult.getTotalUncompressedBytes());
            manifest.setPhotoChecksum(photoResult.getSha256());

            // 3. Create cohesive bundle archive (.zip)
            String archiveFileName = backupId + ".zip";
            Path archiveFile = backupStorageRoot.resolve(archiveFileName);
            createBundleZip(backupDir, archiveFile);

            // 4. Handle Encryption if enabled
            boolean shouldEncrypt = (encrypt != null && encrypt) || backupEncryptionService.isEncryptionEnabled();
            if (shouldEncrypt) {
                Path encFile = backupStorageRoot.resolve(backupId + ".zip.enc");
                try (InputStream in = Files.newInputStream(archiveFile);
                     OutputStream out = Files.newOutputStream(encFile)) {
                    backupEncryptionService.encrypt(in, out, null);
                }
                Files.deleteIfExists(archiveFile);
                archiveFile = encFile;
                archiveFileName = backupId + ".zip.enc";
                manifest.setEncrypted(true);
                manifest.setEncryptionAlgorithm("AES-256-GCM");
            } else {
                manifest.setEncrypted(false);
                manifest.setEncryptionAlgorithm("NONE");
            }

            long totalArchiveSize = Files.size(archiveFile);
            String archiveChecksum = DatabaseBackupService.computeFileSha256(archiveFile);
            manifest.setArchiveFileName(archiveFileName);
            manifest.setTotalSizeBytes(totalArchiveSize);
            manifest.setTotalArchiveChecksum(archiveChecksum);

            // 5. Verification Check: Confirm archive non-empty and readable
            if (totalArchiveSize > 0) {
                manifest.setStatus(BackupStatus.VERIFIED);
                manifest.setVerified(true);
                manifest.setVerifiedAt(Instant.now());
            } else {
                manifest.setStatus(BackupStatus.FAILED);
                manifest.setErrorMessage("Backup archive is empty (0 bytes).");
            }

            Instant completedTime = Instant.now();
            manifest.setCompletedAt(completedTime);
            manifest.setDurationMs(completedTime.toEpochMilli() - startTime.toEpochMilli());

            // Write manifest into both directory and storage root
            saveManifest(backupDir.resolve("manifest.json"), manifest);
            saveManifest(backupStorageRoot.resolve(backupId + "_manifest.json"), manifest);

            log.info("Backup completed successfully: id={}, size={} bytes, duration={}ms, status={}",
                    backupId, totalArchiveSize, manifest.getDurationMs(), manifest.getStatus());

            // 6. Apply retention pruning
            try {
                List<BackupManifest> allManifests = listAllManifests();
                backupRetentionService.applyRetention(backupStorageRoot, allManifests);
            } catch (Exception re) {
                log.warn("Post-backup retention execution encountered error: {}", re.getMessage());
            }

            return manifest;

        } catch (Exception e) {
            log.error("Backup failed for id={}: {}", backupId, e.getMessage(), e);
            manifest.setStatus(BackupStatus.FAILED);
            manifest.setErrorMessage(e.getMessage());
            manifest.setCompletedAt(Instant.now());
            try {
                saveManifest(backupDir.resolve("manifest.json"), manifest);
                saveManifest(backupStorageRoot.resolve(backupId + "_manifest.json"), manifest);
            } catch (Exception ignored) {}
            throw e;
        } finally {
            backupLock.unlock();
        }
    }

    private void createBundleZip(Path sourceDir, Path targetZip) throws IOException {
        try (OutputStream fos = Files.newOutputStream(targetZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            try (var stream = Files.list(sourceDir)) {
                for (Path p : stream.toList()) {
                    if (Files.isRegularFile(p)) {
                        ZipEntry entry = new ZipEntry(p.getFileName().toString());
                        zos.putNextEntry(entry);
                        try (InputStream is = Files.newInputStream(p)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                zos.write(buffer, 0, read);
                            }
                        }
                        zos.closeEntry();
                    }
                }
            }
            zos.finish();
        }
    }

    public List<BackupSummaryResponse> listBackups() {
        return listAllManifests().stream()
                .map(BackupSummaryResponse::from)
                .toList();
    }

    public List<BackupManifest> listAllManifests() {
        List<BackupManifest> manifests = new ArrayList<>();
        if (!Files.exists(backupStorageRoot)) {
            return manifests;
        }

        try (var stream = Files.list(backupStorageRoot)) {
            for (Path p : stream.toList()) {
                if (Files.isDirectory(p)) {
                    Path manifestFile = p.resolve("manifest.json");
                    if (Files.exists(manifestFile)) {
                        try {
                            BackupManifest m = objectMapper.readValue(manifestFile.toFile(), BackupManifest.class);
                            manifests.add(m);
                        } catch (Exception e) {
                            log.debug("Could not parse manifest at {}: {}", manifestFile, e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to list backups: {}", e.getMessage());
        }

        // Sort newest first
        manifests.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return manifests;
    }

    public BackupManifest getManifest(String backupId) {
        Path manifestFile = backupStorageRoot.resolve(backupId).resolve("manifest.json");
        if (!Files.exists(manifestFile)) {
            manifestFile = backupStorageRoot.resolve(backupId + "_manifest.json");
        }
        if (!Files.exists(manifestFile)) {
            throw new ResourceNotFoundException("Backup not found with ID: " + backupId);
        }
        try {
            return objectMapper.readValue(manifestFile.toFile(), BackupManifest.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read backup manifest: " + e.getMessage(), e);
        }
    }

    public Path getBackupArchiveFile(String backupId) {
        BackupManifest manifest = getManifest(backupId);
        if (manifest.getArchiveFileName() != null) {
            Path file = backupStorageRoot.resolve(manifest.getArchiveFileName());
            if (Files.exists(file)) return file;
        }
        Path fallback = backupStorageRoot.resolve(backupId + ".zip");
        if (Files.exists(fallback)) return fallback;
        throw new ResourceNotFoundException("Backup archive file not found for: " + backupId);
    }

    public RestoreValidationResponse runRestoreVerification(String backupId, RestoreValidationRequest request) throws Exception {
        BackupManifest manifest = (backupId != null && !backupId.isBlank())
                ? getManifest(backupId)
                : getLatestVerifiedManifest();

        if (manifest == null) {
            throw new ResourceNotFoundException("No valid verified backup found to execute restore verification.");
        }

        Path backupBundleDir = backupStorageRoot.resolve(manifest.getBackupId());
        RestoreValidationResponse response = backupRestoreService.executeIsolatedRestore(
                backupBundleDir, manifest, request, null
        );
        this.lastRestoreTestResponse = response;
        return response;
    }

    public BackupManifest getLatestVerifiedManifest() {
        return listAllManifests().stream()
                .filter(m -> m.getStatus() == BackupStatus.VERIFIED || m.getStatus() == BackupStatus.COMPLETED)
                .findFirst()
                .orElse(null);
    }

    public BackupHealthResponse getHealthStatus() {
        List<BackupManifest> manifests = listAllManifests();
        BackupSummaryResponse lastBackup = manifests.isEmpty() ? null : BackupSummaryResponse.from(manifests.get(0));
        BackupManifest latestVerified = getLatestVerifiedManifest();
        BackupSummaryResponse lastVerifiedBackup = latestVerified != null ? BackupSummaryResponse.from(latestVerified) : null;

        List<String> alerts = new ArrayList<>();
        String status = "HEALTHY";

        long usableSpace = 0;
        long totalSpace = 0;
        try {
            FileStore store = Files.getFileStore(backupStorageRoot);
            usableSpace = store.getUsableSpace();
            totalSpace = store.getTotalSpace();
        } catch (Exception ignored) {}

        // Check RPO: Was a verified backup taken within the configured RPO window?
        if (latestVerified == null) {
            status = "CRITICAL";
            alerts.add("No verified backup exists in the system. Disaster recovery is unverified.");
        } else {
            long hoursSinceLastBackup = (Instant.now().toEpochMilli() - latestVerified.getCreatedAt().toEpochMilli()) / (1000 * 3600);
            if (hoursSinceLastBackup > rpoTargetHours) {
                status = "WARNING";
                alerts.add("Recovery Point Objective (RPO) breach: Last verified backup is " + hoursSinceLastBackup +
                        " hours old (Target: < " + rpoTargetHours + "h).");
            }
        }

        // Check Disk Space: Warn if backup volume has < 1 GB
        if (usableSpace > 0 && usableSpace < 1073741824L) {
            if (!status.equals("CRITICAL")) status = "WARNING";
            alerts.add("Low backup storage: Usable disk space is under 1 GB (" + (usableSpace / (1024 * 1024)) + " MB remaining).");
        }

        return new BackupHealthResponse(
                status,
                lastBackup,
                lastVerifiedBackup,
                lastRestoreTestResponse,
                usableSpace,
                totalSpace,
                rpoTargetHours,
                rtoTargetMinutes,
                manifests.size(),
                alerts
        );
    }

    private void saveManifest(Path target, BackupManifest manifest) throws IOException {
        Files.createDirectories(target.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), manifest);
    }

    public Path getBackupStorageRoot() {
        return backupStorageRoot;
    }
}
