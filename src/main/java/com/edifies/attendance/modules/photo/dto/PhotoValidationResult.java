package com.edifies.attendance.modules.photo.dto;

public record PhotoValidationResult(
        int width,
        int height,
        long fileSize,
        String format,
        String mimeType
) {
}
