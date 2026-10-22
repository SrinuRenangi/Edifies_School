package com.edifies.attendance.modules.photo.dto;

import com.edifies.attendance.modules.photo.model.CleanupRunStatus;
import com.edifies.attendance.modules.photo.model.CleanupRunType;
import com.edifies.attendance.modules.photo.model.PhotoCleanupHistory;

import java.time.Instant;
import java.util.UUID;

public record CleanupExecutionResponse(
        UUID runId,
        CleanupRunType runType,
        Instant startedAt,
        Instant completedAt,
        int photosEvaluated,
        int photosDeleted,
        int photosFailed,
        int photosMissing,
        long bytesReclaimed,
        int tempFilesCleaned,
        long tempBytesReclaimed,
        CleanupRunStatus status,
        String errorMessage,
        String performedBy
) {
    public static CleanupExecutionResponse from(PhotoCleanupHistory history) {
        return new CleanupExecutionResponse(
                history.getId(),
                history.getRunType(),
                history.getStartedAt(),
                history.getCompletedAt(),
                history.getPhotosEvaluated(),
                history.getPhotosDeleted(),
                history.getPhotosFailed(),
                history.getPhotosMissing(),
                history.getBytesReclaimed(),
                history.getTempFilesCleaned(),
                history.getTempBytesReclaimed(),
                history.getStatus(),
                history.getErrorMessage(),
                history.getPerformedBy()
        );
    }
}
