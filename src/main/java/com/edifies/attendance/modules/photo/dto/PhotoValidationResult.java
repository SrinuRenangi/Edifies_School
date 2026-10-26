package com.edifies.attendance.modules.photo.dto;

public record PhotoValidationResult(
        int width,
        int height,
        long fileSize,
        String format,
        String mimeType,
        byte[] sanitizedBytes
) {
    public PhotoValidationResult(int width, int height, long fileSize, String format, String mimeType) {
        this(width, height, fileSize, format, mimeType, null);
    }
}
