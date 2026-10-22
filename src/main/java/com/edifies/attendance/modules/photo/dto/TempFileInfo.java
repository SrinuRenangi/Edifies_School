package com.edifies.attendance.modules.photo.dto;

import java.time.Instant;

public record TempFileInfo(
        String fileName,
        long fileSize,
        Instant lastModified
) {
}
