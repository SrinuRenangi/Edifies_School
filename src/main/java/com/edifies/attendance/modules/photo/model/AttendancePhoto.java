package com.edifies.attendance.modules.photo.model;

import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.employee.model.Employee;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_photos", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attendance_photo_type", columnNames = {"attendance_id", "photo_type"})
})
public class AttendancePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_id", nullable = false)
    private AttendanceRecord attendance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_type", nullable = false, length = 16)
    private PhotoType photoType;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "file_name", nullable = false, length = 128)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 64)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "image_width", nullable = false)
    private int imageWidth;

    @Column(name = "image_height", nullable = false)
    private int imageHeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_status", nullable = false, length = 32)
    private StorageStatus storageStatus = StorageStatus.ACTIVE;

    @Column(name = "purged_at")
    private Instant purgedAt;

    @Column(name = "cleanup_error", length = 500)
    private String cleanupError;

    @Column(name = "cleanup_attempts", nullable = false)
    private int cleanupAttempts = 0;

    @Column(name = "retention_deadline")
    private Instant retentionDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AttendancePhoto() {
    }

    public AttendancePhoto(AttendanceRecord attendance,
                           Employee employee,
                           PhotoType photoType,
                           String storagePath,
                           String fileName,
                           String mimeType,
                           long fileSize,
                           int imageWidth,
                           int imageHeight) {
        this.attendance = attendance;
        this.employee = employee;
        this.photoType = photoType;
        this.storagePath = storagePath;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.storageStatus = StorageStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.storageStatus == null) {
            this.storageStatus = StorageStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AttendanceRecord getAttendance() {
        return attendance;
    }

    public void setAttendance(AttendanceRecord attendance) {
        this.attendance = attendance;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public PhotoType getPhotoType() {
        return photoType;
    }

    public void setPhotoType(PhotoType photoType) {
        this.photoType = photoType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(int imageHeight) {
        this.imageHeight = imageHeight;
    }

    public StorageStatus getStorageStatus() {
        return storageStatus;
    }

    public void setStorageStatus(StorageStatus storageStatus) {
        this.storageStatus = storageStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getPurgedAt() {
        return purgedAt;
    }

    public void setPurgedAt(Instant purgedAt) {
        this.purgedAt = purgedAt;
    }

    public String getCleanupError() {
        return cleanupError;
    }

    public void setCleanupError(String cleanupError) {
        this.cleanupError = cleanupError;
    }

    public int getCleanupAttempts() {
        return cleanupAttempts;
    }

    public void setCleanupAttempts(int cleanupAttempts) {
        this.cleanupAttempts = cleanupAttempts;
    }

    public Instant getRetentionDeadline() {
        return retentionDeadline;
    }

    public void setRetentionDeadline(Instant retentionDeadline) {
        this.retentionDeadline = retentionDeadline;
    }
}
