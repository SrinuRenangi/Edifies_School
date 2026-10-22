package com.edifies.attendance.modules.photo.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "photo_retention_policies")
public class PhotoRetentionPolicy {

    public static final UUID DEFAULT_POLICY_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Id
    private UUID id = DEFAULT_POLICY_ID;

    @Column(name = "retention_enabled", nullable = false)
    private boolean retentionEnabled = false;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays = 90;

    @Column(name = "auto_cleanup_cron", nullable = false, length = 64)
    private String autoCleanupCron = "0 0 2 * * *";

    @Column(name = "batch_size", nullable = false)
    private int batchSize = 50;

    @Column(name = "temp_expiry_minutes", nullable = false)
    private int tempExpiryMinutes = 120;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy = "SYSTEM";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public PhotoRetentionPolicy() {
    }

    public PhotoRetentionPolicy(boolean retentionEnabled, int retentionDays, String autoCleanupCron, int batchSize, int tempExpiryMinutes, String updatedBy) {
        this.id = DEFAULT_POLICY_ID;
        this.retentionEnabled = retentionEnabled;
        this.retentionDays = retentionDays;
        this.autoCleanupCron = autoCleanupCron != null ? autoCleanupCron : "0 0 2 * * *";
        this.batchSize = batchSize > 0 ? batchSize : 50;
        this.tempExpiryMinutes = tempExpiryMinutes > 0 ? tempExpiryMinutes : 120;
        this.updatedBy = updatedBy != null ? updatedBy : "SYSTEM";
        this.updatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public boolean isRetentionEnabled() {
        return retentionEnabled;
    }

    public void setRetentionEnabled(boolean retentionEnabled) {
        this.retentionEnabled = retentionEnabled;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public String getAutoCleanupCron() {
        return autoCleanupCron;
    }

    public void setAutoCleanupCron(String autoCleanupCron) {
        this.autoCleanupCron = autoCleanupCron;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getTempExpiryMinutes() {
        return tempExpiryMinutes;
    }

    public void setTempExpiryMinutes(int tempExpiryMinutes) {
        this.tempExpiryMinutes = tempExpiryMinutes;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
