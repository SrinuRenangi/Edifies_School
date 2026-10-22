package com.edifies.attendance.modules.attendance.dto;

import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceResponse(
        UUID id,
        UUID employeeId,
        String employeeCode,
        String employeeName,
        String department,
        LocalDate workDate,
        AttendanceStatus status,
        Instant checkInTime,
        Instant checkOutTime,
        boolean hasCheckInPhoto,
        boolean hasCheckOutPhoto,
        Long durationMinutes,
        String durationFormatted,
        boolean isIncomplete,
        String correctionIndicator,
        Long version,
        String message
) {
    public AttendanceResponse(UUID id, UUID employeeId, String employeeCode, String employeeName, String department,
                              LocalDate workDate, AttendanceStatus status, Instant checkInTime, Instant checkOutTime,
                              boolean hasCheckInPhoto, boolean hasCheckOutPhoto,
                              Long durationMinutes, String durationFormatted, boolean isIncomplete,
                              String correctionIndicator, String message) {
        this(id, employeeId, employeeCode, employeeName, department, workDate, status, checkInTime, checkOutTime,
                hasCheckInPhoto, hasCheckOutPhoto, durationMinutes, durationFormatted, isIncomplete, correctionIndicator, 0L, message);
    }

    public AttendanceResponse(UUID id, UUID employeeId, String employeeCode, String employeeName, String department,
                              LocalDate workDate, AttendanceStatus status, Instant checkInTime, Instant checkOutTime,
                              boolean hasCheckInPhoto, boolean hasCheckOutPhoto, String message) {
        this(id, employeeId, employeeCode, employeeName, department, workDate, status, checkInTime, checkOutTime,
                hasCheckInPhoto, hasCheckOutPhoto,
                calculateDurationMinutes(checkInTime, checkOutTime),
                formatDuration(checkInTime, checkOutTime, status),
                isIncompleteSession(status, checkOutTime),
                "Original",
                0L,
                message);
    }

    public AttendanceResponse(UUID id, UUID employeeId, String employeeCode, String employeeName, String department,
                              LocalDate workDate, AttendanceStatus status, Instant checkInTime, Instant checkOutTime, String message) {
        this(id, employeeId, employeeCode, employeeName, department, workDate, status, checkInTime, checkOutTime, false, false, message);
    }

    public static Long calculateDurationMinutes(Instant in, Instant out) {
        if (in != null && out != null && !out.isBefore(in)) {
            return Duration.between(in, out).toMinutes();
        }
        return null;
    }

    public static String formatDuration(Instant in, Instant out, AttendanceStatus status) {
        if (status == AttendanceStatus.VOIDED) {
            return "—";
        }
        Long minutes = calculateDurationMinutes(in, out);
        if (minutes != null) {
            long hours = minutes / 60;
            long mins = minutes % 60;
            return String.format("%dh %02dm", hours, mins);
        }
        if (status == AttendanceStatus.CHECKED_IN) {
            return "In Progress";
        }
        return "—";
    }

    public static boolean isIncompleteSession(AttendanceStatus status, Instant checkOutTime) {
        if (status == AttendanceStatus.VOIDED) {
            return false;
        }
        return status == AttendanceStatus.CHECKED_IN || status == AttendanceStatus.AUTO_CLOSED || checkOutTime == null;
    }

    public static AttendanceResponse from(AttendanceRecord record, String message) {
        Long durationMinutes = calculateDurationMinutes(record.getCheckInTime(), record.getCheckOutTime());
        String durationFormatted = formatDuration(record.getCheckInTime(), record.getCheckOutTime(), record.getStatus());
        boolean isIncomplete = isIncompleteSession(record.getStatus(), record.getCheckOutTime());

        String indicator;
        if (record.isManualEntry()) {
            indicator = "Manual Entry";
        } else if (record.isCorrected()) {
            indicator = "Corrected";
        } else {
            indicator = "Original";
        }

        return new AttendanceResponse(
                record.getId(),
                record.getEmployee().getId(),
                record.getEmployee().getEmployeeId(),
                record.getEmployee().getFullName(),
                record.getEmployee().getDepartment(),
                record.getWorkDate(),
                record.getStatus(),
                record.getCheckInTime(),
                record.getCheckOutTime(),
                record.isHasCheckInPhoto(),
                record.isHasCheckOutPhoto(),
                durationMinutes,
                durationFormatted,
                isIncomplete,
                indicator,
                record.getVersion(),
                message
        );
    }
}
