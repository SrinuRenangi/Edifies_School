package com.edifies.attendance.modules.dashboard.dto;

import com.edifies.attendance.modules.employee.model.EmployeeStatus;

import java.time.Instant;
import java.util.UUID;

public record DashboardQuickSearchEmployee(
        UUID id,
        String employeeId,
        String fullName,
        String department,
        EmployeeStatus status,
        String todayStatus, // CHECKED_IN, CHECKED_OUT, AUTO_CLOSED, NO_RECORD
        Instant lastCheckInTime,
        UUID lastAttendanceId
) {}
