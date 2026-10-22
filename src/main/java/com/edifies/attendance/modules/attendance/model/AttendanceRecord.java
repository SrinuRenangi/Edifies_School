package com.edifies.attendance.modules.attendance.model;

import com.edifies.attendance.modules.employee.model.Employee;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in_time", nullable = false)
    private Instant checkInTime;

    @Column(name = "check_out_time")
    private Instant checkOutTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AttendanceStatus status;

    @Column(name = "has_check_in_photo", nullable = false)
    private boolean hasCheckInPhoto = false;

    @Column(name = "has_check_out_photo", nullable = false)
    private boolean hasCheckOutPhoto = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "is_corrected", nullable = false)
    private boolean isCorrected = false;

    @Column(name = "is_manual_entry", nullable = false)
    private boolean isManualEntry = false;

    @Column(name = "last_correction_at")
    private Instant lastCorrectionAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    public AttendanceRecord() {
    }

    public AttendanceRecord(Employee employee, LocalDate workDate, Instant checkInTime) {
        this.employee = employee;
        this.workDate = workDate;
        this.checkInTime = checkInTime;
        this.status = AttendanceStatus.CHECKED_IN;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.version == null) {
            this.version = 0L;
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

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public Instant getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(Instant checkInTime) {
        this.checkInTime = checkInTime;
    }

    public Instant getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(Instant checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public boolean isHasCheckInPhoto() {
        return hasCheckInPhoto;
    }

    public void setHasCheckInPhoto(boolean hasCheckInPhoto) {
        this.hasCheckInPhoto = hasCheckInPhoto;
    }

    public boolean isHasCheckOutPhoto() {
        return hasCheckOutPhoto;
    }

    public void setHasCheckOutPhoto(boolean hasCheckOutPhoto) {
        this.hasCheckOutPhoto = hasCheckOutPhoto;
    }

    public boolean isCorrected() {
        return isCorrected;
    }

    public void setCorrected(boolean corrected) {
        isCorrected = corrected;
    }

    public boolean isManualEntry() {
        return isManualEntry;
    }

    public void setManualEntry(boolean manualEntry) {
        isManualEntry = manualEntry;
    }

    public Instant getLastCorrectionAt() {
        return lastCorrectionAt;
    }

    public void setLastCorrectionAt(Instant lastCorrectionAt) {
        this.lastCorrectionAt = lastCorrectionAt;
    }

    public Long getVersion() {
        return version;
    }


    public void setVersion(Long version) {
        this.version = version;
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
}
