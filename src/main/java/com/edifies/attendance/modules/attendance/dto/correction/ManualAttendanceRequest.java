package com.edifies.attendance.modules.attendance.dto.correction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ManualAttendanceRequest(
        @NotNull(message = "Employee ID is mandatory.")
        UUID employeeId,

        @NotNull(message = "Work date is mandatory.")
        LocalDate workDate,

        @NotNull(message = "Check-in time is mandatory.")
        Instant checkInTime,

        Instant checkOutTime,

        @NotBlank(message = "Reason for manual entry is mandatory.")
        @Size(max = 500, message = "Reason cannot exceed 500 characters.")
        String reason
) {
}
