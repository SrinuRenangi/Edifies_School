package com.edifies.attendance.modules.kiosk.dto;

import java.time.Instant;
import java.util.UUID;

public record InitiatePairingResponse(
        UUID pairingId,
        String deviceName,
        String pairingCode,
        Instant expiresAt,
        long expiresInSeconds
) {}
