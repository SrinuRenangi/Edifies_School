package com.edifies.attendance.modules.kiosk.dto;

import jakarta.validation.constraints.Size;

public record RevokeDeviceRequest(
        @Size(max = 255, message = "Reason cannot exceed 255 characters")
        String reason
) {}
