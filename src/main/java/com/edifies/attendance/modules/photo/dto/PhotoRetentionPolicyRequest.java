package com.edifies.attendance.modules.photo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PhotoRetentionPolicyRequest(
        @NotNull(message = "retentionEnabled flag is mandatory.")
        Boolean retentionEnabled,

        @NotNull(message = "retentionDays is mandatory.")
        @Min(value = 1, message = "retentionDays must be at least 1 day.")
        @Max(value = 3650, message = "retentionDays cannot exceed 3650 days (10 years).")
        Integer retentionDays,

        String autoCleanupCron,

        @Min(value = 1, message = "batchSize must be at least 1.")
        @Max(value = 1000, message = "batchSize cannot exceed 1000.")
        Integer batchSize,

        @Min(value = 15, message = "tempExpiryMinutes must be at least 15 minutes.")
        Integer tempExpiryMinutes,

        String reason
) {
}
