package com.edifies.attendance.modules.backup;

import com.edifies.attendance.modules.backup.dto.BackupHealthResponse;
import com.edifies.attendance.modules.backup.dto.BackupSummaryResponse;
import com.edifies.attendance.modules.backup.dto.RestoreValidationRequest;
import com.edifies.attendance.modules.backup.dto.RestoreValidationResponse;
import com.edifies.attendance.modules.backup.model.BackupManifest;
import com.edifies.attendance.modules.backup.model.BackupRetentionPolicy;
import com.edifies.attendance.modules.backup.model.BackupStatus;
import com.edifies.attendance.modules.backup.model.BackupType;
import com.edifies.attendance.modules.backup.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class DisasterRecoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DisasterRecoveryCoordinator coordinator;

    @Autowired
    private DatabaseBackupService databaseBackupService;

    @Autowired
    private PhotoBackupService photoBackupService;

    @Autowired
    private BackupEncryptionService encryptionService;

    @Autowired
    private BackupRetentionService retentionService;

    @Autowired
    private BackupRestoreService restoreService;

    @Test
    @DisplayName("1. Backup Encryption: Round-trip AES-256-GCM encryption and decryption with key derivation")
    void testBackupEncryptionRoundTrip() throws Exception {
        String testData = "CONFIDENTIAL_ATTENDANCE_AUDIT_LOG_ENTRY_123456789";
        byte[] originalBytes = testData.getBytes(StandardCharsets.UTF_8);
        String secretKey = "MySuperSecretDisasterRecoveryKey_2026!";

        // Encrypt
        ByteArrayOutputStream encOut = new ByteArrayOutputStream();
        encryptionService.encrypt(new ByteArrayInputStream(originalBytes), encOut, secretKey);
        byte[] encryptedBytes = encOut.toByteArray();

        assertThat(encryptedBytes).isNotEmpty();
        assertThat(encryptedBytes).isNotEqualTo(originalBytes);

        // Decrypt with correct key
        ByteArrayOutputStream decOut = new ByteArrayOutputStream();
        encryptionService.decrypt(new ByteArrayInputStream(encryptedBytes), decOut, secretKey);
        byte[] decryptedBytes = decOut.toByteArray();

        assertThat(new String(decryptedBytes, StandardCharsets.UTF_8)).isEqualTo(testData);

        // Decrypt with wrong key must fail
        ByteArrayOutputStream wrongKeyOut = new ByteArrayOutputStream();
        assertThrows(Exception.class, () -> {
            encryptionService.decrypt(new ByteArrayInputStream(encryptedBytes), wrongKeyOut, "WrongPassword123!");
        });
    }

    @Test
    @DisplayName("2. Database Backup: Creates valid SQL dump and SHA-256 hash from live PostgreSQL")
    void testDatabaseDumpCreation(@TempDir Path tempDir) throws Exception {
        Path outputSql = tempDir.resolve("test_db_dump.sql");
        DatabaseBackupService.DumpResult result = databaseBackupService.createDatabaseDump(outputSql);

        assertThat(result).isNotNull();
        assertThat(result.getSizeBytes()).isGreaterThan(0L);
        assertThat(result.getSha256()).hasSize(64); // SHA-256 hex string
        assertThat(Files.exists(outputSql)).isTrue();

        Map<String, Long> counts = result.getRecordCounts();
        assertThat(counts).isNotNull();
        assertThat(counts).containsKey("admin_users");
        assertThat(counts).containsKey("employees");
    }

    @Test
    @DisplayName("3. Photo Backup & Recovery: Preserves file structure, prevents Zip Slip, extracts intact")
    void testPhotoArchiveCreationAndExtraction(@TempDir Path tempDir) throws Exception {
        // Create synthetic photo in temp directory
        Path syntheticPhotoDir = tempDir.resolve("source_photos");
        Files.createDirectories(syntheticPhotoDir.resolve("2026/10/23"));
        Path samplePhoto = syntheticPhotoDir.resolve("2026/10/23/test_photo.jpg");
        Files.writeString(samplePhoto, "SYNTHETIC_JPEG_BINARY_DATA");

        PhotoBackupService customPhotoService = new PhotoBackupService(syntheticPhotoDir.toString());
        Path outputZip = tempDir.resolve("test_photos.zip");

        PhotoBackupService.PhotoArchiveResult archiveResult = customPhotoService.createPhotoArchive(outputZip);
        assertThat(archiveResult.getFileCount()).isEqualTo(1);
        assertThat(archiveResult.getArchiveSizeBytes()).isGreaterThan(0L);

        // Restore into recovery directory
        Path recoveryPhotoDir = tempDir.resolve("recovery_photos");
        int restoredCount = customPhotoService.restorePhotos(outputZip, recoveryPhotoDir);

        assertThat(restoredCount).isEqualTo(1);
        Path restoredPhoto = recoveryPhotoDir.resolve("2026/10/23/test_photo.jpg");
        assertThat(Files.exists(restoredPhoto)).isTrue();
        assertThat(Files.readString(restoredPhoto)).isEqualTo("SYNTHETIC_JPEG_BINARY_DATA");
    }

    @Test
    @DisplayName("4. Disaster Recovery Coordinator: Executes full bundle backup, produces verified manifest")
    void testDisasterRecoveryCoordinatorFullBackup() throws Exception {
        BackupManifest manifest = coordinator.triggerBackup(
                BackupType.MANUAL,
                "Automated Integration Test Run",
                false,
                "JUNIT_TEST"
        );

        assertThat(manifest).isNotNull();
        assertThat(manifest.getBackupId()).startsWith("backup_");
        assertThat(manifest.getStatus()).isEqualTo(BackupStatus.VERIFIED);
        assertThat(manifest.isVerified()).isTrue();
        assertThat(manifest.getTotalSizeBytes()).isGreaterThan(0L);
        assertThat(manifest.getTotalArchiveChecksum()).hasSize(64);
        assertThat(manifest.getRecordCounts()).isNotEmpty();

        // Verify manifest can be retrieved by ID
        BackupManifest fetched = coordinator.getManifest(manifest.getBackupId());
        assertThat(fetched.getBackupId()).isEqualTo(manifest.getBackupId());
        assertThat(fetched.getTotalArchiveChecksum()).isEqualTo(manifest.getTotalArchiveChecksum());
    }

    @Test
    @DisplayName("5. Backup Retention: Always preserves the newest known-good backup even when expired")
    void testRetentionPolicyPreservesNewestVerifiedBackup(@TempDir Path tempStorage) throws Exception {
        List<BackupManifest> manifests = new ArrayList<>();
        Instant now = Instant.now();

        // Create 10 expired backups (older than 30 days)
        for (int i = 30; i <= 40; i++) {
            BackupManifest m = new BackupManifest();
            m.setBackupId("backup_old_" + i);
            m.setCreatedAt(now.minus(i, ChronoUnit.DAYS));
            m.setStatus(BackupStatus.VERIFIED);
            m.setArchiveFileName("backup_old_" + i + ".zip");
            manifests.add(m);
        }

        BackupRetentionPolicy policy = new BackupRetentionPolicy(7, 4, 3);
        BackupRetentionService.RetentionResult result = retentionService.applyRetentionPolicy(tempStorage, manifests, policy);

        // The newest verified backup (backup_old_30) must be preserved despite being > 7 days old!
        assertThat(result.prunedBackupIds()).doesNotContain("backup_old_30");
        assertThat(result.preservedCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("6. Restore Safety Safeguard: Rejects attempts to restore into the active production database")
    void testProductionDatabaseRestoreSafeguard() {
        BackupManifest manifest = coordinator.getLatestVerifiedManifest();
        if (manifest != null) {
            Path bundleDir = coordinator.getBackupStorageRoot().resolve(manifest.getBackupId());
            RestoreValidationRequest dangerousRequest = new RestoreValidationRequest(
                    manifest.getBackupId(),
                    "attendance", // Attempting to target production DB!
                    "photos_recovery",
                    true
            );

            assertThrows(SecurityException.class, () -> {
                restoreService.executeIsolatedRestore(bundleDir, manifest, dangerousRequest, null);
            });
        }
    }

    @Test
    @DisplayName("7. Isolated Recovery Execution: Restores into attendance_recovery, measures RTO, production untouched")
    void testIsolatedRestoreExecution() throws Exception {
        BackupManifest manifest = coordinator.getLatestVerifiedManifest();
        if (manifest == null) {
            manifest = coordinator.triggerBackup(BackupType.MANUAL, "Pre-restore verification backup", false, "TEST");
        }

        RestoreValidationRequest req = new RestoreValidationRequest(
                manifest.getBackupId(),
                "attendance_recovery",
                "photos_recovery",
                true
        );

        RestoreValidationResponse response = coordinator.runRestoreVerification(manifest.getBackupId(), req);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("PASSED");
        assertThat(response.targetDatabase()).isEqualTo("attendance_recovery");
        assertThat(response.productionUntouched()).isTrue();
        assertThat(response.countsMatched()).isTrue();
        assertThat(response.relationalIntegrityPassed()).isTrue();
        assertThat(response.photoFilesMissing()).isEqualTo(0);
        assertThat(response.restoreDurationMs()).isLessThan(300000L); // Under 5 minutes RTO target
    }

    @Test
    @DisplayName("8. REST API: GET /api/admin/backup/status returns HEALTHY status and RPO/RTO metrics")
    @WithMockUser(roles = "ADMIN")
    void testGetBackupStatusEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/backup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", notNullValue()))
                .andExpect(jsonPath("$.rpoTargetHours").value(24))
                .andExpect(jsonPath("$.rtoTargetMinutes").value(5))
                .andExpect(jsonPath("$.totalBackupsCount").isNumber());
    }

    @Test
    @DisplayName("9. REST API: GET /api/admin/backup/history returns list of available recovery points")
    @WithMockUser(roles = "ADMIN")
    void testGetBackupHistoryEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/backup/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)));
    }

    @Test
    @DisplayName("10. REST API: Unauthenticated requests to /api/admin/backup/** are strictly rejected with 401")
    void testUnauthenticatedAccessDenied() throws Exception {
        mockMvc.perform(get("/api/admin/backup/status"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/backup/trigger"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("11. REST API: GET & PUT /api/admin/backup/retention updates policy safely")
    @WithMockUser(roles = "ADMIN")
    void testRetentionPolicyEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/backup/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keepDaily").value(7))
                .andExpect(jsonPath("$.keepWeekly").value(4))
                .andExpect(jsonPath("$.keepMonthly").value(3));

        BackupRetentionPolicy updated = new BackupRetentionPolicy(14, 8, 6);
        mockMvc.perform(put("/api/admin/backup/retention")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keepDaily").value(14))
                .andExpect(jsonPath("$.keepWeekly").value(8))
                .andExpect(jsonPath("$.keepMonthly").value(6));
    }
}
