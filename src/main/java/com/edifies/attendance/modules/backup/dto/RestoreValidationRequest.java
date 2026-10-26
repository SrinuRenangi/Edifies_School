package com.edifies.attendance.modules.backup.dto;

public record RestoreValidationRequest(
        String backupId,
        String targetDatabase,
        String targetPhotoDir,
        Boolean confirmNonProductionTarget
) {
}
