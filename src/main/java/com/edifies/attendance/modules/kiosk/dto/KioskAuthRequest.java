package com.edifies.attendance.modules.kiosk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record KioskAuthRequest(
        @NotBlank(message = "Employee ID is required")
        String employeeId,

        @NotBlank(message = "PIN is required")
        @Pattern(regexp = "^[0-9]{4,6}$", message = "PIN must be between 4 and 6 numeric digits")
        String pin
) {
}
