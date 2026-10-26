package com.edifies.attendance.modules.attendance.controller;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.modules.attendance.dto.AttendanceResponse;
import com.edifies.attendance.modules.attendance.dto.correction.AttendanceCorrectionRequest;
import com.edifies.attendance.modules.attendance.dto.correction.AttendanceCorrectionResponse;
import com.edifies.attendance.modules.attendance.dto.correction.ManualAttendanceRequest;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.service.AttendanceCorrectionService;
import com.edifies.attendance.modules.attendance.service.AttendanceReportService;
import com.edifies.attendance.modules.attendance.service.AttendanceService;
import com.edifies.attendance.modules.photo.model.AttendancePhoto;
import com.edifies.attendance.modules.photo.model.PhotoType;
import com.edifies.attendance.modules.photo.service.AttendancePhotoService;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/attendance")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAttendanceController {

    private final AttendanceService attendanceService;
    private final AttendancePhotoService attendancePhotoService;
    private final AttendanceReportService attendanceReportService;
    private final AttendanceCorrectionService attendanceCorrectionService;

    public AdminAttendanceController(AttendanceService attendanceService,
                                     AttendancePhotoService attendancePhotoService,
                                     AttendanceReportService attendanceReportService,
                                     AttendanceCorrectionService attendanceCorrectionService) {
        this.attendanceService = attendanceService;
        this.attendancePhotoService = attendancePhotoService;
        this.attendanceReportService = attendanceReportService;
        this.attendanceCorrectionService = attendanceCorrectionService;
    }

    @GetMapping
    public ResponseEntity<Page<AttendanceResponse>> getAttendanceRecords(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AttendanceResponse> records = attendanceService.getAdminAttendanceHistory(
                search, employeeId, fromDate, toDate, status, page, size);
        return ResponseEntity.ok(records);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AttendanceResponse> getAttendanceRecordById(@PathVariable UUID id) {
        AttendanceRecord record = attendanceService.getAttendanceRecordById(id);
        return ResponseEntity.ok(AttendanceResponse.from(record, null));
    }

    @GetMapping("/employee/{id}")
    public ResponseEntity<Page<AttendanceResponse>> getEmployeeAttendance(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AttendanceResponse> records = attendanceService.getEmployeeAttendanceHistory(
                id, fromDate, toDate, page, size);
        return ResponseEntity.ok(records);
    }

    /**
     * Corrects check-in/check-out timestamps, resolves missed checkouts, or voids an erroneous record.
     */
    @PostMapping("/{id}/corrections")
    public ResponseEntity<AttendanceResponse> correctAttendance(
            @PathVariable UUID id,
            @Valid @RequestBody AttendanceCorrectionRequest request,
            Authentication authentication) {

        String performedBy = (authentication != null && authentication.getName() != null)
                ? authentication.getName()
                : "ADMIN";

        AttendanceResponse response = attendanceCorrectionService.correctAttendance(id, request, performedBy);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates an authorized manual attendance entry.
     */
    @PostMapping("/manual")
    public ResponseEntity<AttendanceResponse> createManualEntry(
            @Valid @RequestBody ManualAttendanceRequest request,
            Authentication authentication) {

        String performedBy = (authentication != null && authentication.getName() != null)
                ? authentication.getName()
                : "ADMIN";

        AttendanceResponse response = attendanceCorrectionService.createManualEntry(request, performedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves the chronological audit history for a specific attendance record.
     */
    @GetMapping("/{id}/audit-history")
    public ResponseEntity<Page<AttendanceCorrectionResponse>> getRecordAuditHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AttendanceCorrectionResponse> history = attendanceCorrectionService.getRecordAuditHistory(id, page, size);
        return ResponseEntity.ok(history);
    }

    /**
     * Searchable and paginated audit query across all attendance corrections.
     */
    @GetMapping("/audit")
    public ResponseEntity<Page<AttendanceCorrectionResponse>> searchAuditHistory(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AttendanceCorrectionResponse> auditLogs = attendanceCorrectionService.searchAuditHistory(employeeId, fromDate, toDate, page, size);
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/export")
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

    @GetMapping("/{id}/photo/{photoType}")
    public ResponseEntity<Resource> getAttendancePhoto(
            @PathVariable UUID id,
            @PathVariable PhotoType photoType) {

        AttendancePhoto metadata = attendancePhotoService.getPhotoMetadata(id, photoType)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found for attendance ID: " + id));

        Resource resource = attendancePhotoService.getAuthorizedPhoto(id, photoType);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(metadata.getMimeType());
        } catch (Exception e) {
            mediaType = MediaType.IMAGE_JPEG;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + metadata.getFileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache, no-store, must-revalidate")
                .body(resource);
    }
}
