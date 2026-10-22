package com.edifies.attendance.modules.attendance.dto.report;

import com.edifies.attendance.modules.attendance.model.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyAttendanceSummary(
        LocalDate workDate,
        UUID attendanceId,
        AttendanceStatus status,
        Instant checkInTime,
        Instant checkOutTime,
        Long durationMinutes,
        String durationFormatted,
        boolean hasCheckInPhoto,
        boolean hasCheckOutPhoto,
        boolean hasRecord,
        String note
) {
    public static DailyAttendanceSummary noRecord(LocalDate workDate) {
        return new DailyAttendanceSummary(
                workDate,
                null,
                null,
                null,
                null,
                null,
                "—",
                false,
                false,
                false,
                "No attendance record"
        );
    }
}
