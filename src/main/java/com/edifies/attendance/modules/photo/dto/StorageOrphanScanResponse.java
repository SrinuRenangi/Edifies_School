package com.edifies.attendance.modules.photo.dto;

import java.time.Instant;
import java.util.List;

public record StorageOrphanScanResponse(
        int unregisteredDiskFilesCount,
        List<String> unregisteredDiskFilesSample,
        int missingDbFilesCount,
        List<String> missingDbPhotosSample,
        int abandonedTempFilesCount,
        List<String> abandonedTempFilesSample,
        Instant scannedAt
) {
}
