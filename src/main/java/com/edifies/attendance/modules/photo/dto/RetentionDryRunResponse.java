package com.edifies.attendance.modules.photo.dto;

import java.time.Instant;

public record RetentionDryRunResponse(
        int evaluatedCount,
        int eligibleCount,
        long eligibleBytes,
        int excludedCount,
        int missingOnDiskCount,
        int failedPreviouslyCount,
        int abandonedTempCount,
        long abandonedTempBytes,
        long activePhotoCount,
        long purgedPhotoCount,
        Instant retentionCutoff,
        int retentionDays,
        boolean retentionEnabled
) {
}
