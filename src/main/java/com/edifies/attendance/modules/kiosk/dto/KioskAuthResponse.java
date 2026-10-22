package com.edifies.attendance.modules.kiosk.dto;

public record KioskAuthResponse(
        String token,
        String employeeId,
        String fullName,
        String department,
        long expiresInSeconds
) {
}
