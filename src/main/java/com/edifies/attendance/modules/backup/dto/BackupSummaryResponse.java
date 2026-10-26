package com.edifies.attendance.modules.backup.dto;

import com.edifies.attendance.modules.backup.model.BackupManifest;
import com.edifies.attendance.modules.backup.model.BackupStatus;
import com.edifies.attendance.modules.backup.model.BackupType;
import java.time.Instant;
import java.util.Map;

public record BackupSummaryResponse(
        String backupId,
        Instant createdAt,
        Instant completedAt,
        BackupType type,
        BackupStatus status,
        long totalSizeBytes,
        int photoCount,
        long durationMs,
        boolean encrypted,
        boolean verified,
        String archiveFileName,
        String totalArchiveChecksum,
        Map<String, Long> recordCounts,
        String errorMessage
) {
    public static BackupSummaryResponse from(BackupManifest manifest) {
        return new BackupSummaryResponse(
                manifest.getBackupId(),
                manifest.getCreatedAt(),
                manifest.getCompletedAt(),
                manifest.getType(),
                manifest.getStatus(),
                manifest.getTotalSizeBytes(),
                manifest.getPhotoCount(),
                manifest.getDurationMs(),
                manifest.isEncrypted(),
                manifest.isVerified(),
                manifest.getArchiveFileName(),
                manifest.getTotalArchiveChecksum(),
                manifest.getRecordCounts(),
                manifest.getErrorMessage()
        );
    }
}
