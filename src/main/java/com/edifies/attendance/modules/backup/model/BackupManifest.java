package com.edifies.attendance.modules.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupManifest {

    private String backupId;
    private Instant createdAt;
    private Instant completedAt;
    private BackupType type;
    private BackupStatus status;
    private String databaseName;
    private long databaseSizeBytes;
    private String databaseChecksum;
    private int photoCount;
    private long photoSizeBytes;
    private String photoChecksum;
    private long totalSizeBytes;
    private String totalArchiveChecksum;
    private String archiveFileName;
    private boolean encrypted;
    private String encryptionAlgorithm;
    private long durationMs;
    private Map<String, Long> recordCounts = new HashMap<>();
    private String errorMessage;
    private boolean verified;
    private Instant verifiedAt;
    private String verificationNotes;
    private String createdBy;

    public BackupManifest() {
    }

    public String getBackupId() {
        return backupId;
    }

    public void setBackupId(String backupId) {
        this.backupId = backupId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public BackupType getType() {
        return type;
    }

    public void setType(BackupType type) {
        this.type = type;
    }

    public BackupStatus getStatus() {
        return status;
    }

    public void setStatus(BackupStatus status) {
        this.status = status;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public long getDatabaseSizeBytes() {
        return databaseSizeBytes;
    }

    public void setDatabaseSizeBytes(long databaseSizeBytes) {
        this.databaseSizeBytes = databaseSizeBytes;
    }

    public String getDatabaseChecksum() {
        return databaseChecksum;
    }

    public void setDatabaseChecksum(String databaseChecksum) {
        this.databaseChecksum = databaseChecksum;
    }

    public int getPhotoCount() {
        return photoCount;
    }

    public void setPhotoCount(int photoCount) {
        this.photoCount = photoCount;
    }

    public long getPhotoSizeBytes() {
        return photoSizeBytes;
    }

    public void setPhotoSizeBytes(long photoSizeBytes) {
        this.photoSizeBytes = photoSizeBytes;
    }

    public String getPhotoChecksum() {
        return photoChecksum;
    }

    public void setPhotoChecksum(String photoChecksum) {
        this.photoChecksum = photoChecksum;
    }

    public long getTotalSizeBytes() {
        return totalSizeBytes;
    }

    public void setTotalSizeBytes(long totalSizeBytes) {
        this.totalSizeBytes = totalSizeBytes;
    }

    public String getTotalArchiveChecksum() {
        return totalArchiveChecksum;
    }

    public void setTotalArchiveChecksum(String totalArchiveChecksum) {
        this.totalArchiveChecksum = totalArchiveChecksum;
    }

    public String getArchiveFileName() {
        return archiveFileName;
    }

    public void setArchiveFileName(String archiveFileName) {
        this.archiveFileName = archiveFileName;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public void setEncryptionAlgorithm(String encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public Map<String, Long> getRecordCounts() {
        return recordCounts;
    }

    public void setRecordCounts(Map<String, Long> recordCounts) {
        this.recordCounts = recordCounts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getVerificationNotes() {
        return verificationNotes;
    }

    public void setVerificationNotes(String verificationNotes) {
        this.verificationNotes = verificationNotes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
