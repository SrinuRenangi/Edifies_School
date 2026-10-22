package com.edifies.attendance.modules.attendance.model;

import com.edifies.attendance.modules.employee.model.Employee;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_corrections")
public class AttendanceCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_record_id", nullable = false)
    private AttendanceRecord attendanceRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 64)
    private CorrectionActionType actionType;

    @Column(name = "previous_check_in_time")
    private Instant previousCheckInTime;

    @Column(name = "corrected_check_in_time")
    private Instant correctedCheckInTime;

    @Column(name = "previous_check_out_time")
    private Instant previousCheckOutTime;

    @Column(name = "corrected_check_out_time")
    private Instant correctedCheckOutTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 32)
    private AttendanceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "corrected_status", nullable = false, length = 32)
    private AttendanceStatus correctedStatus;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "performed_by", nullable = false, length = 128)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "record_version", nullable = false)
    private Long recordVersion;

    public AttendanceCorrection() {
    }

    public AttendanceCorrection(AttendanceRecord attendanceRecord,
                                Employee employee,
                                CorrectionActionType actionType,
                                Instant previousCheckInTime,
                                Instant correctedCheckInTime,
                                Instant previousCheckOutTime,
                                Instant correctedCheckOutTime,
                                AttendanceStatus previousStatus,
                                AttendanceStatus correctedStatus,
                                String reason,
                                String performedBy,
                                Long recordVersion) {
        this.attendanceRecord = attendanceRecord;
        this.employee = employee;
        this.actionType = actionType;
        this.previousCheckInTime = previousCheckInTime;
        this.correctedCheckInTime = correctedCheckInTime;
        this.previousCheckOutTime = previousCheckOutTime;
        this.correctedCheckOutTime = correctedCheckOutTime;
        this.previousStatus = previousStatus;
        this.correctedStatus = correctedStatus;
        this.reason = reason;
        this.performedBy = performedBy;
        this.recordVersion = recordVersion;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AttendanceRecord getAttendanceRecord() {
        return attendanceRecord;
    }

    public void setAttendanceRecord(AttendanceRecord attendanceRecord) {
        this.attendanceRecord = attendanceRecord;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public CorrectionActionType getActionType() {
        return actionType;
    }

    public void setActionType(CorrectionActionType actionType) {
        this.actionType = actionType;
    }

    public Instant getPreviousCheckInTime() {
        return previousCheckInTime;
    }

    public void setPreviousCheckInTime(Instant previousCheckInTime) {
        this.previousCheckInTime = previousCheckInTime;
    }

    public Instant getCorrectedCheckInTime() {
        return correctedCheckInTime;
    }

    public void setCorrectedCheckInTime(Instant correctedCheckInTime) {
        this.correctedCheckInTime = correctedCheckInTime;
    }

    public Instant getPreviousCheckOutTime() {
        return previousCheckOutTime;
    }

    public void setPreviousCheckOutTime(Instant previousCheckOutTime) {
        this.previousCheckOutTime = previousCheckOutTime;
    }

    public Instant getCorrectedCheckOutTime() {
        return correctedCheckOutTime;
    }

    public void setCorrectedCheckOutTime(Instant correctedCheckOutTime) {
        this.correctedCheckOutTime = correctedCheckOutTime;
    }

    public AttendanceStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(AttendanceStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public AttendanceStatus getCorrectedStatus() {
        return correctedStatus;
    }

    public void setCorrectedStatus(AttendanceStatus correctedStatus) {
        this.correctedStatus = correctedStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getRecordVersion() {
        return recordVersion;
    }

    public void setRecordVersion(Long recordVersion) {
        this.recordVersion = recordVersion;
    }
}
