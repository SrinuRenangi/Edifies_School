package com.edifies.attendance.modules.backup.dto;

import java.time.Instant;
import java.util.Map;

public record RestoreValidationResponse(
        String backupId,
        String status,
        String targetDatabase,
        String targetPhotoDir,
        long restoreDurationMs,
        Map<String, Long> sourceRecordCounts,
        Map<String, Long> restoredRecordCounts,
        boolean countsMatched,
        int photoFilesChecked,
        int photoFilesMissing,
        boolean relationalIntegrityPassed,
        String message,
        Instant validatedAt,
        boolean productionUntouched
) {
}
