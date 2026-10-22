package com.edifies.attendance.modules.kiosk.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kiosk_pairing_codes")
public class KioskPairingCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "pairing_code_hash", nullable = false, unique = true, length = 128)
    private String pairingCodeHash;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PairingCodeStatus status = PairingCodeStatus.PENDING;

    @Column(name = "paired_device_id")
    private UUID pairedDeviceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public KioskPairingCode() {
    }

    public KioskPairingCode(String deviceName, String pairingCodeHash, String createdBy, Instant expiresAt) {
        this.deviceName = deviceName;
        this.pairingCodeHash = pairingCodeHash;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
        this.status = PairingCodeStatus.PENDING;
        this.failedAttempts = 0;
        this.maxAttempts = 5;
        this.createdAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return status == PairingCodeStatus.PENDING && !isExpired() && failedAttempts < maxAttempts;
    }

    public void incrementFailedAttempts() {
        this.failedAttempts++;
        if (this.failedAttempts >= this.maxAttempts) {
            this.status = PairingCodeStatus.EXPIRED;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getPairingCodeHash() {
        return pairingCodeHash;
    }

    public void setPairingCodeHash(String pairingCodeHash) {
        this.pairingCodeHash = pairingCodeHash;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public PairingCodeStatus getStatus() {
        return status;
    }

    public void setStatus(PairingCodeStatus status) {
        this.status = status;
    }

    public UUID getPairedDeviceId() {
        return pairedDeviceId;
    }

    public void setPairedDeviceId(UUID pairedDeviceId) {
        this.pairedDeviceId = pairedDeviceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
