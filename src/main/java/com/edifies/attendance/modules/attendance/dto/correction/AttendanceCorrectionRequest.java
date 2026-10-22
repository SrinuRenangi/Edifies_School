package com.edifies.attendance.modules.attendance.dto.correction;

import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.model.CorrectionActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AttendanceCorrectionRequest(
        Instant checkInTime,
        Instant checkOutTime,
        AttendanceStatus status,
        CorrectionActionType actionType,
        @NotBlank(message = "Correction reason is mandatory.")
        @Size(max = 500, message = "Reason cannot exceed 500 characters.")
        String reason,
        Long recordVersion
) {
}
