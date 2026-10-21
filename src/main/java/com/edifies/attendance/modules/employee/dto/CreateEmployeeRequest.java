package com.edifies.attendance.modules.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEmployeeRequest(
        @NotBlank(message = "Employee ID is required")
        @Size(min = 2, max = 32, message = "Employee ID must be between 2 and 32 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Employee ID must contain only alphanumeric characters, dashes, or underscores")
        String employeeId,

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 128, message = "Full name must be between 2 and 128 characters")
        String fullName,

        @Size(max = 64, message = "Department name must not exceed 64 characters")
        String department
) {
}
