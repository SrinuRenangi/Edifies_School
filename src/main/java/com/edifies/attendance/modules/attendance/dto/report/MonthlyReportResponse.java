package com.edifies.attendance.modules.attendance.dto.report;

import java.util.List;

public record MonthlyReportResponse(
        int month,
        int year,
        int totalDaysInMonth,
        long totalEmployees,
        long totalRecordedSessions,
        long totalCompletedSessions,
        long totalIncompleteSessions,
        long overallDurationMinutes,
        String overallDurationFormatted,
        List<EmployeeMonthlySummary> employeeSummaries
) {}
