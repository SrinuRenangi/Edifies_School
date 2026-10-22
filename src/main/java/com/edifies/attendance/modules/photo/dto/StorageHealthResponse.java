package com.edifies.attendance.modules.photo.dto;

import com.edifies.attendance.modules.photo.model.StorageWarningLevel;

import java.time.Instant;

public record StorageHealthResponse(
        boolean storageRootAccessible,
        boolean storageWritable,
        long totalSpaceBytes,
        long usableSpaceBytes,
        long usedSpaceBytes,
        double usableSpacePercent,
        long managedPhotoCount,
        long managedPhotoBytes,
        long purgedPhotoCount,
        long missingPhotoCount,
        long cleanupFailedCount,
        int tempFileCount,
        long tempFileBytes,
        long warningThresholdBytes,
        long criticalThresholdBytes,
        StorageWarningLevel warningLevel,
        Instant lastCleanupAt,
        String lastCleanupStatus,
        boolean retentionEnabled,
        int retentionDays
) {
}
