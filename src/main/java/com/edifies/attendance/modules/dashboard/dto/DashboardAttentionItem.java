package com.edifies.attendance.modules.dashboard.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DashboardAttentionItem(
        UUID recordId,
        UUID employeeId,
        String employeeCode,
        String employeeName,
        String department,
        LocalDate workDate,
        Instant checkInTime,
        String status,
        String issueType, // STALE_OPEN_SESSION, AUTO_CLOSED, OVERDUE_OPEN_SESSION
        String issueDescription,
        String recommendation
) {}
