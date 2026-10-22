package com.edifies.attendance.modules.attendance.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceStatusResponse(
        UUID employeeUuid,
        String employeeId,
        String fullName,
        String department,
        LocalDate workDate,
        String currentStatus,
        String allowedAction,
        Instant checkInTime,
        Instant checkOutTime
) {
}
