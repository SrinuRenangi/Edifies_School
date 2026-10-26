package com.edifies.attendance.modules.backup.dto;

import java.util.List;

public record BackupHealthResponse(
        String status,
        BackupSummaryResponse lastBackup,
        BackupSummaryResponse lastVerifiedBackup,
        RestoreValidationResponse lastRestoreTest,
        long availableDiskSpaceBytes,
        long totalDiskSpaceBytes,
        int rpoTargetHours,
        int rtoTargetMinutes,
        int totalBackupsCount,
        List<String> activeAlerts
) {
}
