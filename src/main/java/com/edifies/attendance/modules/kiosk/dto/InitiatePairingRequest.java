package com.edifies.attendance.modules.kiosk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InitiatePairingRequest(
        @NotBlank(message = "Device name is required")
        @Size(min = 2, max = 100, message = "Device name must be between 2 and 100 characters")
        String deviceName
) {}
