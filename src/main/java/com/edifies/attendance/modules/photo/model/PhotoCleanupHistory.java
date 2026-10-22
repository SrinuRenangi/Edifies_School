package com.edifies.attendance.modules.photo.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "photo_cleanup_history")
public class PhotoCleanupHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 32)
    private CleanupRunType runType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "photos_evaluated", nullable = false)
    private int photosEvaluated = 0;

    @Column(name = "photos_deleted", nullable = false)
    private int photosDeleted = 0;

    @Column(name = "photos_failed", nullable = false)
    private int photosFailed = 0;

    @Column(name = "photos_missing", nullable = false)
    private int photosMissing = 0;

    @Column(name = "bytes_reclaimed", nullable = false)
    private long bytesReclaimed = 0;

    @Column(name = "temp_files_cleaned", nullable = false)
    private int tempFilesCleaned = 0;

    @Column(name = "temp_bytes_reclaimed", nullable = false)
    private long tempBytesReclaimed = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CleanupRunStatus status = CleanupRunStatus.IN_PROGRESS;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "performed_by", nullable = false, length = 128)
    private String performedBy;

    public PhotoCleanupHistory() {
    }

    public PhotoCleanupHistory(CleanupRunType runType, String performedBy) {
        this.runType = runType;
        this.performedBy = performedBy != null ? performedBy : "SYSTEM";
        this.startedAt = Instant.now();
        this.status = CleanupRunStatus.IN_PROGRESS;
    }

    public void complete(int evaluated, int deleted, int failed, int missing, long bytesReclaimed, int tempCleaned, long tempBytes) {
        this.completedAt = Instant.now();
        this.photosEvaluated = evaluated;
        this.photosDeleted = deleted;
        this.photosFailed = failed;
        this.photosMissing = missing;
        this.bytesReclaimed = bytesReclaimed;
        this.tempFilesCleaned = tempCleaned;
        this.tempBytesReclaimed = tempBytes;
        if (failed > 0) {
            this.status = CleanupRunStatus.COMPLETED_WITH_ERRORS;
        } else {
            this.status = CleanupRunStatus.COMPLETED;
        }
    }

    public void fail(String errorMessage) {
        this.completedAt = Instant.now();
        this.status = CleanupRunStatus.FAILED;
        this.errorMessage = errorMessage != null && errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CleanupRunType getRunType() {
        return runType;
    }

    public void setRunType(CleanupRunType runType) {
        this.runType = runType;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getPhotosEvaluated() {
        return photosEvaluated;
    }

    public void setPhotosEvaluated(int photosEvaluated) {
        this.photosEvaluated = photosEvaluated;
    }

    public int getPhotosDeleted() {
        return photosDeleted;
    }

    public void setPhotosDeleted(int photosDeleted) {
        this.photosDeleted = photosDeleted;
    }

    public int getPhotosFailed() {
        return photosFailed;
    }

    public void setPhotosFailed(int photosFailed) {
        this.photosFailed = photosFailed;
    }

    public int getPhotosMissing() {
        return photosMissing;
    }

    public void setPhotosMissing(int photosMissing) {
        this.photosMissing = photosMissing;
    }

    public long getBytesReclaimed() {
        return bytesReclaimed;
    }

    public void setBytesReclaimed(long bytesReclaimed) {
        this.bytesReclaimed = bytesReclaimed;
    }

    public int getTempFilesCleaned() {
        return tempFilesCleaned;
    }

    public void setTempFilesCleaned(int tempFilesCleaned) {
        this.tempFilesCleaned = tempFilesCleaned;
    }

    public long getTempBytesReclaimed() {
        return tempBytesReclaimed;
    }

    public void setTempBytesReclaimed(long tempBytesReclaimed) {
        this.tempBytesReclaimed = tempBytesReclaimed;
    }

    public CleanupRunStatus getStatus() {
        return status;
    }

    public void setStatus(CleanupRunStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }
}
