package com.edifies.attendance.modules.attendance.controller;

import com.edifies.attendance.modules.attendance.dto.report.MonthlyReportResponse;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.service.AttendanceReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/reports")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final AttendanceReportService attendanceReportService;

    public AdminReportController(AttendanceReportService attendanceReportService) {
        this.attendanceReportService = attendanceReportService;
    }

    @GetMapping("/monthly")
    public ResponseEntity<MonthlyReportResponse> getMonthlyReport(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) String search) {

        MonthlyReportResponse response = attendanceReportService.generateMonthlyReport(month, year, employeeId, search);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/attendance/export")
    public ResponseEntity<byte[]> exportAttendanceCsv(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) AttendanceStatus status) {

        byte[] csvData = attendanceReportService.exportAttendanceCsv(search, employeeId, fromDate, toDate, status);
        String filename = String.format("attendance-report-%s-to-%s.csv",
                fromDate != null ? fromDate : "all",
                toDate != null ? toDate : "all");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache, no-store, must-revalidate")
                .body(csvData);
    }

    @GetMapping("/monthly/export")
    public ResponseEntity<byte[]> exportMonthlySummaryCsv(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) String search) {

        byte[] csvData = attendanceReportService.exportMonthlySummaryCsv(month, year, employeeId, search);
        String filename = String.format("attendance-monthly-summary-%04d-%02d.csv", year, month);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache, no-store, must-revalidate")
                .body(csvData);
    }
}
