package com.edifies.attendance.modules.dashboard.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DashboardSummaryResponse(
        LocalDate workDate,
        String timezone,
        Instant generatedAt,
        long activeEmployeeCount,
        long totalEmployeeCount,
        long currentlyCheckedInCount,
        long completedSessionCount,
        long incompleteSessionCount,
        long notCheckedInCount,
        List<DashboardActivityItem> recentActivities,
        List<DashboardAttentionItem> attentionRequiredSessions
) {}
