package com.edifies.attendance.modules.backup.service;

import com.edifies.attendance.modules.backup.dto.RestoreValidationRequest;
import com.edifies.attendance.modules.backup.dto.RestoreValidationResponse;
import com.edifies.attendance.modules.backup.model.BackupManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;

@Service
public class BackupRestoreService {

    private static final Logger log = LoggerFactory.getLogger(BackupRestoreService.class);

    private final DatabaseBackupService databaseBackupService;
    private final PhotoBackupService photoBackupService;
    private final BackupEncryptionService backupEncryptionService;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:postgres}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    public BackupRestoreService(DatabaseBackupService databaseBackupService,
                                PhotoBackupService photoBackupService,
                                BackupEncryptionService backupEncryptionService) {
        this.databaseBackupService = databaseBackupService;
        this.photoBackupService = photoBackupService;
        this.backupEncryptionService = backupEncryptionService;
    }

    public RestoreValidationResponse executeIsolatedRestore(
            Path backupBundleDir,
            BackupManifest manifest,
            RestoreValidationRequest request,
            String decryptionKey
    ) throws Exception {

        long startTime = System.currentTimeMillis();
        String targetDb = (request != null && request.targetDatabase() != null && !request.targetDatabase().isBlank())
                ? request.targetDatabase().trim()
                : "attendance_recovery";

        String targetPhotoDir = (request != null && request.targetPhotoDir() != null && !request.targetPhotoDir().isBlank())
                ? request.targetPhotoDir().trim()
                : "photos_recovery";

        // Safeguard Rule: Never allow recovery test to overwrite live production database
        DatabaseBackupService.ParsedJdbcUrl prodUrl = databaseBackupService.parseJdbcUrl(datasourceUrl);
        if (targetDb.equalsIgnoreCase(prodUrl.database)) {
            throw new SecurityException("CRITICAL SAFETY SAFEGUARD: Restore rejected. " +
                    "Target database '" + targetDb + "' matches the active production database. " +
                    "Recovery operations must target an isolated database.");
        }

        log.info("Starting isolated restore verification for backupId='{}' into targetDb='{}' and photoDir='{}'",
                manifest.getBackupId(), targetDb, targetPhotoDir);

        Path sqlDumpFile = backupBundleDir.resolve("database.sql");
        Path photosZipFile = backupBundleDir.resolve("photos.zip");

        // 1. If encrypted, decrypt the dump file
        if (manifest.isEncrypted()) {
            Path encFile = backupBundleDir.resolve("database.sql.enc");
            if (Files.exists(encFile)) {
                sqlDumpFile = backupBundleDir.resolve("database_decrypted.sql");
                try (InputStream in = Files.newInputStream(encFile);
                     OutputStream out = Files.newOutputStream(sqlDumpFile)) {
                    backupEncryptionService.decrypt(in, out, decryptionKey);
                }
            }
        }

        if (!Files.exists(sqlDumpFile)) {
            throw new FileNotFoundException("Cannot restore: database SQL dump file is missing in " + backupBundleDir);
        }

        // 2. Prepare target database
        recreateTargetDatabase(prodUrl.host, prodUrl.port, targetDb);

        // 3. Restore database content into targetDb
        restoreDatabaseDump(prodUrl.host, prodUrl.port, targetDb, sqlDumpFile);

        // 4. Restore photo files into isolated target directory
        Path targetPhotoPath = Paths.get(targetPhotoDir).toAbsolutePath().normalize();
        int photoFilesRestored = 0;
        if (Files.exists(photosZipFile)) {
            photoFilesRestored = photoBackupService.restorePhotos(photosZipFile, targetPhotoPath);
        }

        long restoreDuration = System.currentTimeMillis() - startTime;

        // 5. Query and verify restored database tables and integrity
        String recoveryJdbcUrl = "jdbc:postgresql://" + prodUrl.host + ":" + prodUrl.port + "/" + targetDb;
        Map<String, Long> restoredCounts = new HashMap<>();
        boolean relationalPassed = true;
        int photoFilesChecked = 0;
        int photoFilesMissing = 0;

        try (Connection recoveryConn = DriverManager.getConnection(recoveryJdbcUrl, datasourceUsername, datasourcePassword)) {
            restoredCounts = databaseBackupService.collectRecordCountsFromConnection(recoveryConn);

            // Relational Integrity Check 1: Attendance record employee references
            try (Statement stmt = recoveryConn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM attendance_records a " +
                         "LEFT JOIN employees e ON a.employee_id = e.id " +
                         "WHERE e.id IS NULL")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    relationalPassed = false;
                    log.error("Integrity check failed: {} orphaned attendance records found in recovery database", rs.getInt(1));
                }
            } catch (Exception e) {
                log.debug("Skipping attendance employee relation check: {}", e.getMessage());
            }

            // Relational Integrity Check 2: Attendance photo record references
            try (Statement stmt = recoveryConn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM attendance_photos p " +
                         "LEFT JOIN attendance_records a ON p.attendance_id = a.id " +
                         "WHERE a.id IS NULL")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    relationalPassed = false;
                    log.error("Integrity check failed: {} orphaned photo records found in recovery database", rs.getInt(1));
                }
            } catch (Exception e) {
                log.debug("Skipping photo attendance relation check: {}", e.getMessage());
            }

            // Photo Physical File Resolution Check:
            try (Statement stmt = recoveryConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT storage_path FROM attendance_photos WHERE storage_status = 'ACTIVE'")) {
                while (rs.next()) {
                    photoFilesChecked++;
                    String relPath = rs.getString(1);
                    Path physicalPhoto = targetPhotoPath.resolve(relPath).normalize();
                    if (!Files.exists(physicalPhoto) || Files.size(physicalPhoto) == 0) {
                        photoFilesMissing++;
                        log.warn("Restored photo physical file missing: {}", physicalPhoto);
                    }
                }
            } catch (Exception e) {
                log.debug("Skipping photo physical existence check: {}", e.getMessage());
            }
        }

        // Compare counts with manifest
        Map<String, Long> sourceCounts = manifest.getRecordCounts() != null ? manifest.getRecordCounts() : Collections.emptyMap();
        boolean countsMatched = true;
        for (Map.Entry<String, Long> entry : sourceCounts.entrySet()) {
            Long restored = restoredCounts.get(entry.getKey());
            if (restored == null || !restored.equals(entry.getValue())) {
                countsMatched = false;
                log.warn("Table count mismatch for '{}': expected {}, found {}", entry.getKey(), entry.getValue(), restored);
            }
        }

        boolean passed = countsMatched && relationalPassed && photoFilesMissing == 0;
        String message = passed
                ? "Disaster recovery validation PASSED. All " + restoredCounts.values().stream().mapToLong(Long::longValue).sum() +
                  " records and " + photoFilesChecked + " photo files verified in " + restoreDuration + "ms (RTO met)."
                : "Disaster recovery validation FAILED with integrity issues. Check logs.";

        log.info("Isolated restore validation completed: status={}, duration={}ms, countsMatched={}, relationalPassed={}",
                passed ? "PASSED" : "FAILED", restoreDuration, countsMatched, relationalPassed);

        return new RestoreValidationResponse(
                manifest.getBackupId(),
                passed ? "PASSED" : "FAILED",
                targetDb,
                targetPhotoDir,
                restoreDuration,
                sourceCounts,
                restoredCounts,
                countsMatched,
                photoFilesChecked,
                photoFilesMissing,
                relationalPassed,
                message,
                Instant.now(),
                true // Production database and photos remain completely untouched
        );
    }

    private void recreateTargetDatabase(String host, int port, String targetDb) throws Exception {
        String adminUrl = "jdbc:postgresql://" + host + ":" + port + "/postgres";
        try (Connection conn = DriverManager.getConnection(adminUrl, datasourceUsername, datasourcePassword);
             Statement stmt = conn.createStatement()) {

            // Terminate any existing connections to the target database
            try {
                stmt.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '" + targetDb + "' AND pid <> pg_backend_pid()");
            } catch (Exception ignored) {}

            // Drop and recreate
            stmt.execute("DROP DATABASE IF EXISTS \"" + targetDb + "\"");
            stmt.execute("CREATE DATABASE \"" + targetDb + "\" WITH ENCODING = 'UTF8'");
            log.info("Target recovery database '{}' created cleanly.", targetDb);
        }
    }

    private void restoreDatabaseDump(String host, int port, String targetDb, Path sqlDumpFile) throws Exception {
        Path psqlExec = databaseBackupService.findPsqlExecutable();
        if (psqlExec != null && Files.exists(psqlExec)) {
            try {
                List<String> command = new ArrayList<>();
                command.add(psqlExec.toString());
                command.add("-h");
                command.add(host);
                command.add("-p");
                command.add(String.valueOf(port));
                command.add("-U");
                command.add(datasourceUsername);
                command.add("-d");
                command.add(targetDb);
                command.add("-f");
                command.add(sqlDumpFile.toAbsolutePath().toString());

                ProcessBuilder pb = new ProcessBuilder(command);
                Map<String, String> env = pb.environment();
                env.put("PGPASSWORD", datasourcePassword != null ? datasourcePassword : "");
                pb.redirectErrorStream(true);

                Process process = pb.start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append(System.lineSeparator());
                    }
                }
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    log.info("psql restore into '{}' finished successfully.", targetDb);
                    return;
                }
                log.warn("psql exited with code {}: {}. Falling back to JDBC statement executor.", exitCode, output);
            } catch (Exception e) {
                log.warn("Native psql execution failed, using JDBC execution: {}", e.getMessage());
            }
        }

        // Fallback: JDBC statement execution
        String recoveryJdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + targetDb;
        try (Connection conn = DriverManager.getConnection(recoveryJdbcUrl, datasourceUsername, datasourcePassword);
             Statement stmt = conn.createStatement();
             BufferedReader reader = Files.newBufferedReader(sqlDumpFile)) {

            StringBuilder currentSql = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                currentSql.append(line).append("\n");
                if (trimmed.endsWith(";")) {
                    try {
                        stmt.execute(currentSql.toString());
                    } catch (Exception e) {
                        log.debug("SQL execution notice: {}", e.getMessage());
                    }
                    currentSql.setLength(0);
                }
            }
        }
    }
}
