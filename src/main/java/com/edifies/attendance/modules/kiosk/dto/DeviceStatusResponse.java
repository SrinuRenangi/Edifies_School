package com.edifies.attendance.modules.kiosk.dto;

import java.util.UUID;

public record DeviceStatusResponse(
        boolean registered,
        UUID deviceId,
        String deviceIdentifier,
        String deviceName,
        String status
) {}
