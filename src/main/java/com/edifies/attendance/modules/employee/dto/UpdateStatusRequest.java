package com.edifies.attendance.modules.employee.dto;

import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "Status is required (ACTIVE or INACTIVE)")
        EmployeeStatus status
) {
}
