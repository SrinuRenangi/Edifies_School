package com.edifies.attendance.modules.attendance.dto.report;

import java.util.List;
import java.util.UUID;

public record EmployeeMonthlySummary(
        UUID employeeId,
        String employeeCode,
        String employeeName,
        String department,
        String employeeStatus,
        long totalSessions,
        long completedSessions,
        long incompleteSessions,
        long totalDurationMinutes,
        String totalDurationFormatted,
        List<DailyAttendanceSummary> dailyRecords
) {}
