package com.edifies.attendance.modules.photo.dto;

import com.edifies.attendance.modules.photo.model.PhotoRetentionPolicy;

import java.time.Instant;
import java.util.UUID;

public record PhotoRetentionPolicyResponse(
        UUID id,
        boolean retentionEnabled,
        int retentionDays,
        String autoCleanupCron,
        int batchSize,
        int tempExpiryMinutes,
        String updatedBy,
        Instant updatedAt
) {
    public static PhotoRetentionPolicyResponse from(PhotoRetentionPolicy policy) {
        return new PhotoRetentionPolicyResponse(
                policy.getId(),
                policy.isRetentionEnabled(),
                policy.getRetentionDays(),
                policy.getAutoCleanupCron(),
                policy.getBatchSize(),
                policy.getTempExpiryMinutes(),
                policy.getUpdatedBy(),
                policy.getUpdatedAt()
        );
    }
}
