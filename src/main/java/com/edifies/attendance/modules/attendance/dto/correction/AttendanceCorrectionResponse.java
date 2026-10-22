package com.edifies.attendance.modules.attendance.dto.correction;

import com.edifies.attendance.modules.attendance.model.AttendanceCorrection;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.model.CorrectionActionType;

import java.time.Instant;
import java.util.UUID;

public record AttendanceCorrectionResponse(
        UUID id,
        UUID attendanceRecordId,
        UUID employeeId,
        String employeeCode,
        String employeeName,
        CorrectionActionType actionType,
        Instant previousCheckInTime,
        Instant correctedCheckInTime,
        Instant previousCheckOutTime,
        Instant correctedCheckOutTime,
        AttendanceStatus previousStatus,
        AttendanceStatus correctedStatus,
        String reason,
        String performedBy,
        Instant createdAt,
        Long recordVersion
) {
    public static AttendanceCorrectionResponse from(AttendanceCorrection c) {
        String employeeCode = c.getEmployee() != null ? c.getEmployee().getEmployeeId() : null;
        String employeeName = c.getEmployee() != null ? c.getEmployee().getFullName() : null;
        UUID employeeId = c.getEmployee() != null ? c.getEmployee().getId() : null;
        UUID recordId = c.getAttendanceRecord() != null ? c.getAttendanceRecord().getId() : null;

        return new AttendanceCorrectionResponse(
                c.getId(),
                recordId,
                employeeId,
                employeeCode,
                employeeName,
                c.getActionType(),
                c.getPreviousCheckInTime(),
                c.getCorrectedCheckInTime(),
                c.getPreviousCheckOutTime(),
                c.getCorrectedCheckOutTime(),
                c.getPreviousStatus(),
                c.getCorrectedStatus(),
                c.getReason(),
                c.getPerformedBy(),
                c.getCreatedAt(),
                c.getRecordVersion()
        );
    }
}
