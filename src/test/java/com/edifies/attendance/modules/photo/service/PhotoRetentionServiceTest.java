package com.edifies.attendance.modules.photo.service;

import com.edifies.attendance.common.exception.ValidationException;
import com.edifies.attendance.modules.photo.dto.*;
import com.edifies.attendance.modules.photo.model.*;
import com.edifies.attendance.modules.photo.model.StorageStatus;
import com.edifies.attendance.modules.photo.repository.AttendancePhotoRepository;
import com.edifies.attendance.modules.photo.repository.PhotoCleanupHistoryRepository;
import com.edifies.attendance.modules.photo.repository.PhotoRetentionPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PhotoRetentionServiceTest {

    @TempDir
    Path tempStorageDir;

    private LocalFilePhotoStorageService storageService;
    private AttendancePhotoRepository photoRepository;
    private PhotoRetentionPolicyRepository policyRepository;
    private PhotoCleanupHistoryRepository historyRepository;
    private PhotoRetentionService retentionService;

    @BeforeEach
    void setUp() {
        storageService = new LocalFilePhotoStorageService(tempStorageDir.toString());
        storageService.init();

        photoRepository = mock(AttendancePhotoRepository.class);
        policyRepository = mock(PhotoRetentionPolicyRepository.class);
        historyRepository = mock(PhotoCleanupHistoryRepository.class);

        retentionService = new PhotoRetentionService(
                policyRepository,
                photoRepository,
                historyRepository,
                storageService
        );
    }

    @Test
    @DisplayName("Default retention policy has automatic deletion disabled")
    void defaultPolicyHasAutoDeletionDisabled() {
        when(policyRepository.findById(PhotoRetentionPolicy.DEFAULT_POLICY_ID))
                .thenReturn(Optional.empty());
        when(policyRepository.save(any(PhotoRetentionPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PhotoRetentionPolicy policy = retentionService.getPolicy();
        assertThat(policy.isRetentionEnabled()).isFalse();
        assertThat(policy.getRetentionDays()).isEqualTo(90);
    }

    @Test
    @DisplayName("Dry-run preview identifies eligible photos without modifying DB or deleting files")
    void dryRunIdentifiesEligiblePhotosWithoutModification() {
        PhotoRetentionPolicy policy = new PhotoRetentionPolicy();
        policy.setRetentionEnabled(true);
        policy.setRetentionDays(30);
        policy.setBatchSize(100);
        when(policyRepository.findById(PhotoRetentionPolicy.DEFAULT_POLICY_ID)).thenReturn(Optional.of(policy));

        when(photoRepository.countByStorageStatusAndCreatedAtBefore(eq(StorageStatus.ACTIVE), any(Instant.class)))
                .thenReturn(5L);
        when(photoRepository.sumFileSizeByStorageStatusAndCreatedAtBefore(eq(StorageStatus.ACTIVE), any(Instant.class)))
                .thenReturn(1024000L);
        when(photoRepository.countByStorageStatus(StorageStatus.ACTIVE)).thenReturn(10L);
        when(photoRepository.countByStorageStatus(StorageStatus.PURGED)).thenReturn(2L);
        when(photoRepository.countByStorageStatus(StorageStatus.MISSING)).thenReturn(0L);
        when(photoRepository.countByStorageStatus(StorageStatus.CLEANUP_FAILED)).thenReturn(0L);

        RetentionDryRunResponse dryRun = retentionService.performDryRun("admin");

        assertThat(dryRun.eligibleCount()).isEqualTo(5);
        assertThat(dryRun.eligibleBytes()).isEqualTo(1024000L);
        assertThat(dryRun.retentionEnabled()).isTrue();
        assertThat(dryRun.retentionDays()).isEqualTo(30);

        // Verify no deletes or save operations occurred
        verify(photoRepository, never()).save(any());
        verify(photoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Safe two-phase deletion purges photo file and marks DB record PURGED")
    void safeDeletionPurgesFileAndUpdatesMetadata() {
        PhotoRetentionPolicy policy = new PhotoRetentionPolicy();
        policy.setRetentionEnabled(true);
        policy.setRetentionDays(30);
        policy.setBatchSize(50);
        when(policyRepository.findById(PhotoRetentionPolicy.DEFAULT_POLICY_ID)).thenReturn(Optional.of(policy));

        // Create synthetic photo file on disk
        String relativeDir = "2026/08/01/session-1";
        String fileName = "check-in.jpg";
        String storageKey = storageService.store(
                new ByteArrayInputStream("SYNTHETIC_DATA".getBytes(StandardCharsets.UTF_8)),
                relativeDir,
                fileName
        );
        assertThat(storageService.exists(storageKey)).isTrue();

        AttendancePhoto photo = new AttendancePhoto();
        photo.setId(UUID.randomUUID());
        photo.setStoragePath(storageKey);
        photo.setStorageStatus(StorageStatus.ACTIVE);
        photo.setFileSize(14L);
        photo.setCreatedAt(Instant.now().minus(40, ChronoUnit.DAYS));

        when(photoRepository.findByStorageStatus(StorageStatus.PENDING_DELETION)).thenReturn(Collections.emptyList());
        when(photoRepository.findByStorageStatus(StorageStatus.CLEANUP_FAILED)).thenReturn(Collections.emptyList());
        when(photoRepository.findByStorageStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq(StorageStatus.ACTIVE), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(photo));
        when(photoRepository.saveAndFlush(any(AttendancePhoto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PhotoCleanupHistory history = new PhotoCleanupHistory(CleanupRunType.MANUAL, "admin");
        when(historyRepository.saveAndFlush(any(PhotoCleanupHistory.class))).thenReturn(history);

        CleanupExecutionResponse result = retentionService.executeCleanup("admin", CleanupRunType.MANUAL);

        assertThat(result.photosDeleted()).isEqualTo(1);
        assertThat(result.bytesReclaimed()).isEqualTo(14L);

        // Verify physical file was deleted
        assertThat(storageService.exists(storageKey)).isFalse();

        // Verify metadata was transitioned to PURGED with timestamp
        assertThat(photo.getStorageStatus()).isEqualTo(StorageStatus.PURGED);
        assertThat(photo.getPurgedAt()).isNotNull();
    }

    @Test
    @DisplayName("Already-missing photo file is detected, marked MISSING, and logged as inconsistency")
    void alreadyMissingFileTransitionsToMissingState() {
        PhotoRetentionPolicy policy = new PhotoRetentionPolicy();
        policy.setRetentionEnabled(true);
        policy.setRetentionDays(30);
        policy.setBatchSize(50);
        when(policyRepository.findById(PhotoRetentionPolicy.DEFAULT_POLICY_ID)).thenReturn(Optional.of(policy));

        String missingStorageKey = "2026/01/01/ghost-session/check-in.jpg";
        assertThat(storageService.exists(missingStorageKey)).isFalse();

        AttendancePhoto photo = new AttendancePhoto();
        photo.setId(UUID.randomUUID());
        photo.setStoragePath(missingStorageKey);
        photo.setStorageStatus(StorageStatus.ACTIVE);
        photo.setFileSize(2048L);
        photo.setCreatedAt(Instant.now().minus(60, ChronoUnit.DAYS));

        when(photoRepository.findByStorageStatus(StorageStatus.PENDING_DELETION)).thenReturn(Collections.emptyList());
        when(photoRepository.findByStorageStatus(StorageStatus.CLEANUP_FAILED)).thenReturn(Collections.emptyList());
        when(photoRepository.findByStorageStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq(StorageStatus.ACTIVE), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(photo));
        when(photoRepository.saveAndFlush(any(AttendancePhoto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PhotoCleanupHistory history = new PhotoCleanupHistory(CleanupRunType.MANUAL, "admin");
        when(historyRepository.saveAndFlush(any(PhotoCleanupHistory.class))).thenReturn(history);

        CleanupExecutionResponse result = retentionService.executeCleanup("admin", CleanupRunType.MANUAL);

        assertThat(result.photosDeleted()).isEqualTo(0);
        assertThat(result.photosMissing()).isEqualTo(1);
        assertThat(photo.getStorageStatus()).isEqualTo(StorageStatus.MISSING);
        assertThat(photo.getCleanupError()).contains("absent from disk storage");
    }

    @Test
    @DisplayName("Interrupted cleanup in PENDING_DELETION or CLEANUP_FAILED is resumed on next run")
    void interruptedCleanupIsRecoveredOnNextRun() {
        PhotoRetentionPolicy policy = new PhotoRetentionPolicy();
        policy.setRetentionEnabled(true);
        policy.setRetentionDays(30);
        policy.setBatchSize(50);
        when(policyRepository.findById(PhotoRetentionPolicy.DEFAULT_POLICY_ID)).thenReturn(Optional.of(policy));

        String storageKey = storageService.store(
                new ByteArrayInputStream("RECOVERY_TEST".getBytes(StandardCharsets.UTF_8)),
                "2026/05/10/session-recovery",
                "check-in.jpg"
        );

        AttendancePhoto pendingPhoto = new AttendancePhoto();
        pendingPhoto.setId(UUID.randomUUID());
        pendingPhoto.setStoragePath(storageKey);
        pendingPhoto.setStorageStatus(StorageStatus.PENDING_DELETION); // Interrupted previous state
        pendingPhoto.setFileSize(13L);
        pendingPhoto.setCreatedAt(Instant.now().minus(50, ChronoUnit.DAYS));

        when(photoRepository.findByStorageStatus(StorageStatus.PENDING_DELETION)).thenReturn(List.of(pendingPhoto));
        when(photoRepository.findByStorageStatus(StorageStatus.CLEANUP_FAILED)).thenReturn(Collections.emptyList());
        when(photoRepository.findByStorageStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq(StorageStatus.ACTIVE), any(Instant.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(photoRepository.saveAndFlush(any(AttendancePhoto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PhotoCleanupHistory history = new PhotoCleanupHistory(CleanupRunType.MANUAL, "admin");
        when(historyRepository.saveAndFlush(any(PhotoCleanupHistory.class))).thenReturn(history);

        CleanupExecutionResponse result = retentionService.executeCleanup("admin", CleanupRunType.MANUAL);

        assertThat(result.photosDeleted()).isEqualTo(1);
        assertThat(storageService.exists(storageKey)).isFalse();
        assertThat(pendingPhoto.getStorageStatus()).isEqualTo(StorageStatus.PURGED);
    }

    @Test
    @DisplayName("Abandoned temporary files older than 2 hours are purged; recent ones preserved")
    void abandonedTempFilesCleanedWhileRecentPreserved() throws Exception {
        Path tempDir = tempStorageDir.resolve("temp");
        Files.createDirectories(tempDir);

        Path oldTempFile = tempDir.resolve("upload_abandoned.tmp");
        Files.writeString(oldTempFile, "ABANDONED_STAGING_CONTENT");
        FileTime fourHoursAgo = FileTime.from(Instant.now().minus(4, ChronoUnit.HOURS));
        Files.setLastModifiedTime(oldTempFile, fourHoursAgo);

        Path recentTempFile = tempDir.resolve("upload_active.tmp");
        Files.writeString(recentTempFile, "ACTIVE_UPLOAD_IN_PROGRESS");

        PhotoRetentionPolicy policy = new PhotoRetentionPolicy();
        policy.setTempExpiryMinutes(120);
        when(policyRepository.findById(PhotoRetentionPolicy.DEFAULT_POLICY_ID)).thenReturn(Optional.of(policy));
        when(photoRepository.findByStorageStatus(StorageStatus.ACTIVE)).thenReturn(Collections.emptyList());

        StorageOrphanScanResponse scan = retentionService.scanOrphans();

        // Old abandoned temp file was identified
        assertThat(scan.abandonedTempFilesCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Unregistered permanent files are flagged for review and NEVER auto-deleted")
    void unregisteredPermanentFilesFlaggedAndNotDeleted() {
        // Store an untracked permanent file
        String untrackedKey = storageService.store(
                new ByteArrayInputStream("ORPHAN_IMG".getBytes(StandardCharsets.UTF_8)),
                "2026/09/15/unregistered-session",
                "check-in.jpg"
        );

        PhotoRetentionPolicy policy = new PhotoRetentionPolicy();
        when(policyRepository.findById(PhotoRetentionPolicy.DEFAULT_POLICY_ID)).thenReturn(Optional.of(policy));
        when(photoRepository.findByStorageStatus(StorageStatus.ACTIVE)).thenReturn(Collections.emptyList());

        StorageOrphanScanResponse scan = retentionService.scanOrphans();

        // Flagged for inspection
        assertThat(scan.unregisteredDiskFilesCount()).isEqualTo(1);
        assertThat(scan.unregisteredDiskFilesSample()).contains(untrackedKey);

        // Safety guarantee: NOT deleted!
        assertThat(storageService.exists(untrackedKey)).isTrue();
    }

    @Test
    @DisplayName("Invalid retention policy settings are rejected with ValidationException")
    void invalidPolicySettingsRejected() {
        // Negative retention days
        PhotoRetentionPolicyRequest negativeDays = new PhotoRetentionPolicyRequest(true, -5, null, 100, 120, null);
        assertThatThrownBy(() -> retentionService.updatePolicy(negativeDays, "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("between 1 and 3650");

        // Excessive retention days (> 10 years)
        PhotoRetentionPolicyRequest excessiveDays = new PhotoRetentionPolicyRequest(true, 5000, null, 100, 120, null);
        assertThatThrownBy(() -> retentionService.updatePolicy(excessiveDays, "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("between 1 and 3650");

        // Invalid batch size (< 1)
        PhotoRetentionPolicyRequest zeroBatch = new PhotoRetentionPolicyRequest(true, 90, null, 0, 120, null);
        assertThatThrownBy(() -> retentionService.updatePolicy(zeroBatch, "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("between 1 and 1000");
    }

    @Test
    @DisplayName("Path traversal attempts in delete and exists operations are safely blocked")
    void pathTraversalInStorageBlocked() {
        assertThatThrownBy(() -> storageService.delete("../../../escape.jpg"))
                .isInstanceOf(com.edifies.attendance.common.exception.StorageException.class)
                .hasMessageContaining("path traversal");

        assertThat(storageService.exists("../../../escape.jpg")).isFalse();
    }
}
