package com.edifies.attendance.modules.backup.dto;

public record BackupTriggerRequest(
        String type,
        String notes,
        Boolean encrypt
) {
}
