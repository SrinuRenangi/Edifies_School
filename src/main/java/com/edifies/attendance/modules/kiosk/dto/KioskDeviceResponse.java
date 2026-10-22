package com.edifies.attendance.modules.kiosk.dto;

import java.time.Instant;
import java.util.UUID;

public record KioskDeviceResponse(
        UUID id,
        String deviceIdentifier,
        String deviceName,
        String status,
        Instant pairedAt,
        Instant lastSeenAt,
        Instant revokedAt,
        String revocationReason,
        String createdBy,
        String revokedBy,
        Instant createdAt
) {}
