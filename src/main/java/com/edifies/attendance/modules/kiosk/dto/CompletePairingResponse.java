package com.edifies.attendance.modules.kiosk.dto;

import java.time.Instant;
import java.util.UUID;

public record CompletePairingResponse(
        UUID deviceId,
        String deviceIdentifier,
        String deviceName,
        String deviceToken,
        String status,
        Instant pairedAt
) {}
