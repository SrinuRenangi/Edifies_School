package com.edifies.attendance.modules.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPinRequest(
        @NotBlank(message = "New PIN is required")
        @Pattern(regexp = "^[0-9]{4,6}$", message = "PIN must be between 4 and 6 numeric digits")
        String newPin
) {
}
