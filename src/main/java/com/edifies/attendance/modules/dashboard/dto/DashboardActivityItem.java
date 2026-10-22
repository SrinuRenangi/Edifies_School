package com.edifies.attendance.modules.dashboard.dto;

import java.time.Instant;
import java.util.UUID;

public record DashboardActivityItem(
        UUID recordId,
        UUID employeeId,
        String employeeCode,
        String employeeName,
        String department,
        String activityType, // CHECK_IN, CHECK_OUT, CORRECTION, AUTO_CLOSED
        Instant timestamp,
        String description,
        String status,
        boolean hasPhoto
) {}
