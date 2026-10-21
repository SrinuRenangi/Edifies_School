package com.edifies.attendance.modules.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEmployeeRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 128, message = "Full name must be between 2 and 128 characters")
        String fullName,

        @Size(max = 64, message = "Department name must not exceed 64 characters")
        String department
) {
}
