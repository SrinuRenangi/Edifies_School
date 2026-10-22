package com.edifies.attendance.modules.dashboard.controller;

import com.edifies.attendance.modules.dashboard.dto.DashboardQuickSearchEmployee;
import com.edifies.attendance.modules.dashboard.dto.DashboardSummaryResponse;
import com.edifies.attendance.modules.dashboard.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    public AdminDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Retrieves the authoritative overview and metrics for the administrator dashboard.
     */
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate) {
        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(workDate);
        return ResponseEntity.ok(summary);
    }

    /**
     * Fast employee search with today's attendance status for dashboard quick navigation.
     */
    @GetMapping("/employee-search")
    public ResponseEntity<List<DashboardQuickSearchEmployee>> searchActiveEmployees(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate) {
        List<DashboardQuickSearchEmployee> results = dashboardService.searchActiveEmployees(query, workDate);
        return ResponseEntity.ok(results);
    }
}
