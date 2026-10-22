package com.edifies.attendance.modules.security.dto;

public record AdminLoginResponse(
        String username,
        String fullName,
        String role,
        String message
) {
}
