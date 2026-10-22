package com.edifies.attendance.modules.photo.service;

import com.edifies.attendance.common.exception.ValidationException;
import com.edifies.attendance.modules.photo.dto.*;
import com.edifies.attendance.modules.photo.model.*;
import com.edifies.attendance.modules.photo.repository.AttendancePhotoRepository;
import com.edifies.attendance.modules.photo.repository.PhotoCleanupHistoryRepository;
import com.edifies.attendance.modules.photo.repository.PhotoRetentionPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PhotoRetentionService {

    private static final Logger log = LoggerFactory.getLogger(PhotoRetentionService.class);

    private final PhotoRetentionPolicyRepository policyRepository;
    private final AttendancePhotoRepository attendancePhotoRepository;
    private final PhotoCleanupHistoryRepository historyRepository;
    private final PhotoStorageService photoStorageService;

    @Value("${attendance.photo.low-storage-warning-threshold-bytes:1073741824}")
    private long lowStorageWarningThresholdBytes; // Default: 1 GB

    @Value("${attendance.photo.low-storage-critical-threshold-bytes:262144000}")
    private long lowStorageCriticalThresholdBytes; // Default: 250 MB

    public PhotoRetentionService(PhotoRetentionPolicyRepository policyRepository,
                                 AttendancePhotoRepository attendancePhotoRepository,
                                 PhotoCleanupHistoryRepository historyRepository,
                                 PhotoStorageService photoStorageService) {
        this.policyRepository = policyRepository;
        this.attendancePhotoRepository = attendancePhotoRepository;
        this.historyRepository = historyRepository;
        this.photoStorageService = photoStorageService;
    }

    /**
     * Retrieves the current photo retention policy.
     * Seeds initial safe policy if none exists.
     */
    @Transactional(readOnly = true)
    public PhotoRetentionPolicy getPolicy() {
        return policyRepository.findById(PhotoRetentionPolicy.DEFAULT_POLICY_ID)
                .orElseGet(() -> {
                    PhotoRetentionPolicy defaultPolicy = new PhotoRetentionPolicy(false, 90, "0 0 2 * * *", 50, 120, "SYSTEM_DEFAULT");
                    return policyRepository.save(defaultPolicy);
                });
    }

    /**
     * Updates the photo retention policy with server-side validation.
     */
    @Transactional
    public PhotoRetentionPolicyResponse updatePolicy(PhotoRetentionPolicyRequest request, String performedBy) {
        if (request.retentionDays() == null || request.retentionDays() < 1 || request.retentionDays() > 3650) {
            throw new ValidationException("Retention duration must be between 1 and 3650 days.");
        }
        if (request.batchSize() != null && (request.batchSize() < 1 || request.batchSize() > 1000)) {
            throw new ValidationException("Batch size must be between 1 and 1000.");
        }
        if (request.tempExpiryMinutes() != null && request.tempExpiryMinutes() < 15) {
            throw new ValidationException("Temporary file expiry must be at least 15 minutes.");
        }

        PhotoRetentionPolicy policy = getPolicy();
        policy.setRetentionEnabled(Boolean.TRUE.equals(request.retentionEnabled()));
        policy.setRetentionDays(request.retentionDays());

        if (request.autoCleanupCron() != null && !request.autoCleanupCron().isBlank()) {
            policy.setAutoCleanupCron(request.autoCleanupCron().trim());
        }
        if (request.batchSize() != null) {
            policy.setBatchSize(request.batchSize());
        }
        if (request.tempExpiryMinutes() != null) {
            policy.setTempExpiryMinutes(request.tempExpiryMinutes());
        }

        policy.setUpdatedBy(performedBy != null ? performedBy : "ADMIN");
        policy.setUpdatedAt(Instant.now());

        PhotoRetentionPolicy saved = policyRepository.save(policy);
        log.info("Photo retention policy updated by '{}': enabled={}, days={}, batchSize={}, tempExpiryMinutes={}",
                performedBy, saved.isRetentionEnabled(), saved.getRetentionDays(), saved.getBatchSize(), saved.getTempExpiryMinutes());

        return PhotoRetentionPolicyResponse.from(saved);
    }

    /**
     * Performs a dry-run evaluation of the retention policy without mutating records or deleting files.
     */
    @Transactional(readOnly = true)
    public RetentionDryRunResponse performDryRun(String performedBy) {
        PhotoRetentionPolicy policy = getPolicy();
        Instant cutoff = Instant.now().minus(policy.getRetentionDays(), ChronoUnit.DAYS);

        int eligibleCount = (int) attendancePhotoRepository.countByStorageStatusAndCreatedAtBefore(StorageStatus.ACTIVE, cutoff);
        long eligibleBytes = attendancePhotoRepository.sumFileSizeByStorageStatusAndCreatedAtBefore(StorageStatus.ACTIVE, cutoff);

        long activeCount = attendancePhotoRepository.countByStorageStatus(StorageStatus.ACTIVE);
        long purgedCount = attendancePhotoRepository.countByStorageStatus(StorageStatus.PURGED);
        long missingCount = attendancePhotoRepository.countByStorageStatus(StorageStatus.MISSING);
        long failedCount = attendancePhotoRepository.countByStorageStatus(StorageStatus.CLEANUP_FAILED);

        int excludedCount = (int) Math.max(0, activeCount - eligibleCount);

        // Scan temporary staging directory for abandoned files
        List<TempFileInfo> tempFiles = photoStorageService.listTempFiles();
        Instant tempCutoff = Instant.now().minus(policy.getTempExpiryMinutes(), ChronoUnit.MINUTES);
        int abandonedTempCount = 0;
        long abandonedTempBytes = 0;
        for (TempFileInfo temp : tempFiles) {
            if (temp.lastModified() != null && temp.lastModified().isBefore(tempCutoff)) {
                abandonedTempCount++;
                abandonedTempBytes += temp.fileSize();
            }
        }

        log.info("Retention dry-run performed by '{}': eligible={}, eligibleBytes={}, active={}, tempAbandoned={}",
                performedBy, eligibleCount, eligibleBytes, activeCount, abandonedTempCount);

        return new RetentionDryRunResponse(
                eligibleCount + (int) failedCount,
                eligibleCount,
                eligibleBytes,
                excludedCount,
                (int) missingCount,
                (int) failedCount,
                abandonedTempCount,
                abandonedTempBytes,
                activeCount,
                purgedCount,
                cutoff,
                policy.getRetentionDays(),
                policy.isRetentionEnabled()
        );
    }

    /**
     * Executes safe, bounded batch cleanup of expired photos and abandoned temporary uploads.
     */
    @Transactional
    public CleanupExecutionResponse executeCleanup(String performedBy, CleanupRunType runType) {
        PhotoRetentionPolicy policy = getPolicy();

        if (runType == CleanupRunType.SCHEDULED && !policy.isRetentionEnabled()) {
            log.info("Scheduled photo cleanup skipped: automatic cleanup is currently disabled by administrator policy.");
            PhotoCleanupHistory skipped = new PhotoCleanupHistory(runType, performedBy);
            skipped.complete(0, 0, 0, 0, 0, 0, 0);
            return CleanupExecutionResponse.from(skipped);
        }

        PhotoCleanupHistory history = new PhotoCleanupHistory(runType, performedBy);
        history = historyRepository.saveAndFlush(history);

        Instant cutoff = Instant.now().minus(policy.getRetentionDays(), ChronoUnit.DAYS);
        Pageable batchPage = PageRequest.of(0, policy.getBatchSize());

        int evaluated = 0;
        int deleted = 0;
        int failed = 0;
        int missing = 0;
        long bytesReclaimed = 0;

        try {
            // 1. Recover any incomplete/failed items first (PENDING_DELETION or CLEANUP_FAILED)
            List<AttendancePhoto> recoverablePhotos = new ArrayList<>();
            recoverablePhotos.addAll(attendancePhotoRepository.findByStorageStatus(StorageStatus.PENDING_DELETION));
            recoverablePhotos.addAll(attendancePhotoRepository.findByStorageStatus(StorageStatus.CLEANUP_FAILED));

            // 2. Fetch new eligible photos for this bounded batch
            int remainingBatch = Math.max(0, policy.getBatchSize() - recoverablePhotos.size());
            List<AttendancePhoto> newEligible = remainingBatch > 0
                    ? attendancePhotoRepository.findByStorageStatusAndCreatedAtBeforeOrderByCreatedAtAsc(StorageStatus.ACTIVE, cutoff, PageRequest.of(0, remainingBatch))
                    : Collections.emptyList();

            List<AttendancePhoto> targetBatch = new ArrayList<>(recoverablePhotos);
            targetBatch.addAll(newEligible);

            evaluated = targetBatch.size();

            // 3. Process each photo with claim-then-delete workflow
            for (AttendancePhoto photo : targetBatch) {
                // Step A: Claim photo for deletion
                photo.setStorageStatus(StorageStatus.PENDING_DELETION);
                photo.setCleanupAttempts(photo.getCleanupAttempts() + 1);
                photo = attendancePhotoRepository.saveAndFlush(photo);

                // Step B: Check file existence on disk
                String storagePath = photo.getStoragePath();
                boolean fileExists = photoStorageService.exists(storagePath);

                if (!fileExists) {
                    // File was unexpectedly missing before cleanup
                    photo.setStorageStatus(StorageStatus.MISSING);
                    photo.setCleanupError("File was absent from disk storage prior to cleanup.");
                    attendancePhotoRepository.saveAndFlush(photo);
                    missing++;
                    log.warn("Photo file missing during cleanup: photoId={}, storagePath='{}'", photo.getId(), storagePath);
                    continue;
                }

                // Step C: Execute deletion through storage abstraction
                try {
                    boolean wasDeleted = photoStorageService.delete(storagePath);
                    if (wasDeleted) {
                        photo.setStorageStatus(StorageStatus.PURGED);
                        photo.setPurgedAt(Instant.now());
                        photo.setCleanupError(null);
                        attendancePhotoRepository.saveAndFlush(photo);
                        deleted++;
                        bytesReclaimed += photo.getFileSize();
                    } else {
                        photo.setStorageStatus(StorageStatus.CLEANUP_FAILED);
                        photo.setCleanupError("Storage deletion returned false.");
                        attendancePhotoRepository.saveAndFlush(photo);
                        failed++;
                    }
                } catch (Exception ex) {
                    log.error("Failed to delete photo from storage: photoId={}, storagePath='{}'", photo.getId(), storagePath, ex);
                    photo.setStorageStatus(StorageStatus.CLEANUP_FAILED);
                    photo.setCleanupError(ex.getMessage() != null && ex.getMessage().length() > 500 ? ex.getMessage().substring(0, 500) : ex.getMessage());
                    attendancePhotoRepository.saveAndFlush(photo);
                    failed++;
                }
            }

            // 4. Clean abandoned temporary uploads older than threshold
            int tempCleaned = 0;
            long tempBytesReclaimed = 0;
            Instant tempCutoff = Instant.now().minus(policy.getTempExpiryMinutes(), ChronoUnit.MINUTES);
            List<TempFileInfo> tempFiles = photoStorageService.listTempFiles();

            for (TempFileInfo temp : tempFiles) {
                if (temp.lastModified() != null && temp.lastModified().isBefore(tempCutoff)) {
                    try {
                        boolean del = photoStorageService.deleteTempFile(temp.fileName());
                        if (del) {
                            tempCleaned++;
                            tempBytesReclaimed += temp.fileSize();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to delete expired temp file: '{}'", temp.fileName(), e);
                    }
                }
            }

            history.complete(evaluated, deleted, failed, missing, bytesReclaimed, tempCleaned, tempBytesReclaimed);
            history = historyRepository.saveAndFlush(history);

            log.info("Photo cleanup run completed (runId={}): evaluated={}, deleted={}, failed={}, missing={}, bytesReclaimed={}, tempCleaned={}",
                    history.getId(), evaluated, deleted, failed, missing, bytesReclaimed, tempCleaned);

            return CleanupExecutionResponse.from(history);
        } catch (Exception ex) {
            log.error("Photo cleanup run failed with exception", ex);
            history.fail(ex.getMessage());
            historyRepository.saveAndFlush(history);
            throw ex;
        }
    }

    /**
     * Reports live storage health, capacity measurements, warning levels, and counts.
     */
    @Transactional(readOnly = true)
    public StorageHealthResponse getStorageHealth() {
        PhotoRetentionPolicy policy = getPolicy();

        long totalSpace = photoStorageService.getTotalSpaceBytes();
        long usableSpace = photoStorageService.getUsableSpaceBytes();
        long usedSpace = Math.max(0, totalSpace - usableSpace);
        double usablePercent = totalSpace > 0 ? (usableSpace * 100.0) / totalSpace : 0.0;

        boolean accessible = totalSpace > 0;
        boolean writable = photoStorageService.isStorageWritable();

        long managedCount = attendancePhotoRepository.countByStorageStatus(StorageStatus.ACTIVE);
        long managedBytes = attendancePhotoRepository.sumFileSizeByStorageStatus(StorageStatus.ACTIVE);
        long purgedCount = attendancePhotoRepository.countByStorageStatus(StorageStatus.PURGED);
        long missingCount = attendancePhotoRepository.countByStorageStatus(StorageStatus.MISSING);
        long failedCount = attendancePhotoRepository.countByStorageStatus(StorageStatus.CLEANUP_FAILED);

        List<TempFileInfo> tempFiles = photoStorageService.listTempFiles();
        int tempCount = tempFiles.size();
        long tempBytes = tempFiles.stream().mapToLong(TempFileInfo::fileSize).sum();

        StorageWarningLevel warningLevel;
        if (usableSpace <= lowStorageCriticalThresholdBytes) {
            warningLevel = StorageWarningLevel.CRITICAL;
        } else if (usableSpace <= lowStorageWarningThresholdBytes) {
            warningLevel = StorageWarningLevel.WARNING;
        } else {
            warningLevel = StorageWarningLevel.NORMAL;
        }

        Optional<PhotoCleanupHistory> lastRunOpt = historyRepository.findTopByOrderByStartedAtDesc();
        Instant lastCleanupAt = lastRunOpt.map(PhotoCleanupHistory::getCompletedAt).orElse(null);
        String lastCleanupStatus = lastRunOpt.map(h -> h.getStatus().name()).orElse("NEVER_RUN");

        return new StorageHealthResponse(
                accessible,
                writable,
                totalSpace,
                usableSpace,
                usedSpace,
                usablePercent,
                managedCount,
                managedBytes,
                purgedCount,
                missingCount,
                failedCount,
                tempCount,
                tempBytes,
                lowStorageWarningThresholdBytes,
                lowStorageCriticalThresholdBytes,
                warningLevel,
                lastCleanupAt,
                lastCleanupStatus,
                policy.isRetentionEnabled(),
                policy.getRetentionDays()
        );
    }

    /**
     * Scans storage for orphans (unregistered disk files) and missing database references.
     */
    @Transactional(readOnly = true)
    public StorageOrphanScanResponse scanOrphans() {
        List<String> diskFiles = photoStorageService.listStoredPhotoKeys();
        Set<String> diskFileSet = new HashSet<>(diskFiles);

        List<AttendancePhoto> allActivePhotos = attendancePhotoRepository.findByStorageStatus(StorageStatus.ACTIVE);
        Set<String> dbFileSet = new HashSet<>();
        List<String> missingDbPhotos = new ArrayList<>();

        for (AttendancePhoto p : allActivePhotos) {
            dbFileSet.add(p.getStoragePath());
            if (!diskFileSet.contains(p.getStoragePath())) {
                missingDbPhotos.add(String.format("photoId=%s, recordId=%s, path=%s", p.getId(), p.getAttendance().getId(), p.getStoragePath()));
            }
        }

        List<String> unregisteredDiskFiles = new ArrayList<>();
        for (String diskFile : diskFiles) {
            if (!dbFileSet.contains(diskFile)) {
                unregisteredDiskFiles.add(diskFile);
            }
        }

        List<TempFileInfo> tempFiles = photoStorageService.listTempFiles();
        PhotoRetentionPolicy policy = getPolicy();
        Instant tempCutoff = Instant.now().minus(policy.getTempExpiryMinutes(), ChronoUnit.MINUTES);
        List<String> abandonedTemps = new ArrayList<>();
        for (TempFileInfo temp : tempFiles) {
            if (temp.lastModified() != null && temp.lastModified().isBefore(tempCutoff)) {
                abandonedTemps.add(temp.fileName());
            }
        }

        // Bounded samples (up to 50 items) for safe admin inspection
        List<String> unregisteredSample = unregisteredDiskFiles.stream().limit(50).toList();
        List<String> missingSample = missingDbPhotos.stream().limit(50).toList();
        List<String> tempSample = abandonedTemps.stream().limit(50).toList();

        return new StorageOrphanScanResponse(
                unregisteredDiskFiles.size(),
                unregisteredSample,
                missingDbPhotos.size(),
                missingSample,
                abandonedTemps.size(),
                tempSample,
                Instant.now()
        );
    }

    /**
     * Retrieves paginated cleanup history for administrator auditing.
     */
    @Transactional(readOnly = true)
    public Page<CleanupExecutionResponse> getCleanupHistory(Pageable pageable) {
        return historyRepository.findAllByOrderByStartedAtDesc(pageable)
                .map(CleanupExecutionResponse::from);
    }
}
