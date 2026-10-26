package com.edifies.attendance.modules.attendance.controller;

import com.edifies.attendance.modules.attendance.dto.AttendanceResponse;
import com.edifies.attendance.modules.attendance.dto.AttendanceStatusResponse;
import com.edifies.attendance.modules.attendance.service.AttendanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kiosk/attendance")
public class KioskAttendanceController {

    private final AttendanceService attendanceService;

    public KioskAttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/status")
    public ResponseEntity<AttendanceStatusResponse> getAttendanceStatus(
            @RequestHeader(value = "X-Kiosk-Session", required = false) String sessionHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = resolveSessionToken(sessionHeader, authHeader);
        AttendanceStatusResponse status = attendanceService.getAttendanceStatus(token);
        return ResponseEntity.ok(status);
    }

    @PostMapping("/check-in")
    public ResponseEntity<AttendanceResponse> checkIn(
            @RequestHeader(value = "X-Kiosk-Session", required = false) String sessionHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(value = "photo", required = false) org.springframework.web.multipart.MultipartFile photo) {

        String token = resolveSessionToken(sessionHeader, authHeader);
        AttendanceResponse response = attendanceService.checkIn(token, photo, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/check-out")
    public ResponseEntity<AttendanceResponse> checkOut(
            @RequestHeader(value = "X-Kiosk-Session", required = false) String sessionHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(value = "photo", required = false) org.springframework.web.multipart.MultipartFile photo) {

        String token = resolveSessionToken(sessionHeader, authHeader);
        AttendanceResponse response = attendanceService.checkOut(token, photo, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    private String resolveSessionToken(String sessionHeader, String authHeader) {
        if (sessionHeader != null && !sessionHeader.isBlank()) {
            return sessionHeader.trim();
        }
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authHeader.substring(7).trim();
        }
        return null;
    }
}
