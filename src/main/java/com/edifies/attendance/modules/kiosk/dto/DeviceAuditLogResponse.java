package com.edifies.attendance.modules.kiosk.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceAuditLogResponse(
        UUID id,
        UUID deviceId,
        String deviceName,
        String eventType,
        String actor,
        String ipAddress,
        String userAgent,
        String details,
        Instant createdAt
) {}
