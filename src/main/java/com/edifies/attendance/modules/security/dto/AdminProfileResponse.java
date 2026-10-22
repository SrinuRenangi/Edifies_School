package com.edifies.attendance.modules.security.dto;

public record AdminProfileResponse(
        String username,
        String fullName,
        String role
) {
}
