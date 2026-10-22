package com.edifies.attendance.modules.attendance.service;

import com.edifies.attendance.common.exception.ConflictException;
import com.edifies.attendance.common.exception.DuplicateResourceException;
import com.edifies.attendance.common.exception.InvalidOperationException;
import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.common.exception.ValidationException;
import com.edifies.attendance.modules.attendance.dto.AttendanceResponse;
import com.edifies.attendance.modules.attendance.dto.correction.AttendanceCorrectionRequest;
import com.edifies.attendance.modules.attendance.dto.correction.AttendanceCorrectionResponse;
import com.edifies.attendance.modules.attendance.dto.correction.ManualAttendanceRequest;
import com.edifies.attendance.modules.attendance.model.AttendanceCorrection;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.model.CorrectionActionType;
import com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class AttendanceCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceCorrectionService.class);

    private final AttendanceRepository attendanceRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final EmployeeRepository employeeRepository;

    public AttendanceCorrectionService(AttendanceRepository attendanceRepository,
                                       AttendanceCorrectionRepository correctionRepository,
                                       EmployeeRepository employeeRepository) {
        this.attendanceRepository = attendanceRepository;
        this.correctionRepository = correctionRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Atomically corrects an attendance record and appends an immutable audit event.
     */
    public AttendanceResponse correctAttendance(UUID recordId, AttendanceCorrectionRequest request, String performedBy) {
        if (recordId == null) {
            throw new ValidationException("Attendance record ID cannot be null.");
        }
        if (performedBy == null || performedBy.isBlank()) {
            throw new ValidationException("Authenticated administrator identity is required.");
        }
        if (request.reason() == null || request.reason().trim().isBlank()) {
            throw new ValidationException("Correction reason is mandatory and cannot be empty.");
        }
        String cleanReason = request.reason().trim();
        if (cleanReason.length() > 500) {
            throw new ValidationException("Correction reason cannot exceed 500 characters.");
        }

        // 1. Fetch target attendance record
        AttendanceRecord record = attendanceRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found with id: " + recordId));

        // 2. Concurrency & Optimistic Lock verification
        if (request.recordVersion() != null && !Objects.equals(request.recordVersion(), record.getVersion())) {
            log.warn("Correction rejected due to version mismatch on record {}: submitted={}, current={}",
                    recordId, request.recordVersion(), record.getVersion());
            throw new ConflictException("The attendance record was modified by another administrator (version conflict). Please reload and try again.");
        }

        // 3. Capture before-state snapshots
        Instant prevCheckIn = record.getCheckInTime();
        Instant prevCheckOut = record.getCheckOutTime();
        AttendanceStatus prevStatus = record.getStatus();

        // 4. Resolve target values and action type
        CorrectionActionType actionType = request.actionType();
        AttendanceStatus targetStatus = request.status();
        Instant targetCheckIn = request.checkInTime() != null ? request.checkInTime() : prevCheckIn;
        Instant targetCheckOut = request.checkOutTime() != null ? request.checkOutTime() : prevCheckOut;

        if (actionType == CorrectionActionType.VOID_RECORD || targetStatus == AttendanceStatus.VOIDED) {
            if (prevStatus == AttendanceStatus.VOIDED) {
                throw new InvalidOperationException("Attendance record is already voided.");
            }
            targetStatus = AttendanceStatus.VOIDED;
            actionType = CorrectionActionType.VOID_RECORD;
        } else if (actionType == CorrectionActionType.RESOLVE_MISSED_CHECKOUT
                || (actionType == null && prevCheckOut == null && targetCheckOut != null && (prevStatus == AttendanceStatus.CHECKED_IN || prevStatus == AttendanceStatus.AUTO_CLOSED))) {
            if (targetCheckOut == null) {
                throw new ValidationException("Check-out time is required to resolve a missed checkout.");
            }
            if (targetCheckOut.isBefore(targetCheckIn)) {
                throw new ValidationException("Check-out time cannot precede check-in time.");
            }
            targetStatus = AttendanceStatus.CHECKED_OUT;
            actionType = CorrectionActionType.RESOLVE_MISSED_CHECKOUT;
        } else {
            // General punch correction
            if (targetCheckIn == null) {
                throw new ValidationException("Check-in time cannot be null.");
            }
            if (targetCheckOut != null && targetCheckOut.isBefore(targetCheckIn)) {
                throw new ValidationException("Check-out time cannot precede check-in time.");
            }

            if (targetStatus == null) {
                targetStatus = targetCheckOut != null ? AttendanceStatus.CHECKED_OUT : AttendanceStatus.CHECKED_IN;
            }

            if (actionType == null) {
                boolean inChanged = !Objects.equals(prevCheckIn, targetCheckIn);
                boolean outChanged = !Objects.equals(prevCheckOut, targetCheckOut);
                if (inChanged && outChanged) {
                    actionType = CorrectionActionType.CORRECT_BOTH;
                } else if (inChanged) {
                    actionType = CorrectionActionType.CORRECT_CHECK_IN;
                } else if (outChanged) {
                    actionType = CorrectionActionType.CORRECT_CHECK_OUT;
                } else {
                    actionType = CorrectionActionType.CORRECT_BOTH;
                }
            }
        }

        // 5. Apply changes to AttendanceRecord
        record.setCheckInTime(targetCheckIn);
        record.setCheckOutTime(targetCheckOut);
        record.setStatus(targetStatus);
        record.setCorrected(true);
        record.setLastCorrectionAt(Instant.now());

        AttendanceRecord savedRecord = attendanceRepository.save(record);

        // 6. Create immutable audit event within the exact same database transaction
        AttendanceCorrection auditEvent = new AttendanceCorrection(
                savedRecord,
                savedRecord.getEmployee(),
                actionType,
                prevCheckIn,
                targetCheckIn,
                prevCheckOut,
                targetCheckOut,
                prevStatus,
                targetStatus,
                cleanReason,
                performedBy,
                savedRecord.getVersion()
        );
        correctionRepository.save(auditEvent);

        log.info("Attendance record {} successfully corrected by '{}'. Action: {}, Reason: '{}'",
                recordId, performedBy, actionType, cleanReason);

        return AttendanceResponse.from(savedRecord, "Attendance record successfully corrected.");
    }

    /**
     * Atomically creates a manual attendance entry and writes an immutable audit record.
     */
    public AttendanceResponse createManualEntry(ManualAttendanceRequest request, String performedBy) {
        if (performedBy == null || performedBy.isBlank()) {
            throw new ValidationException("Authenticated administrator identity is required.");
        }
        if (request.reason() == null || request.reason().trim().isBlank()) {
            throw new ValidationException("Reason for manual entry is mandatory and cannot be empty.");
        }
        String cleanReason = request.reason().trim();
        if (cleanReason.length() > 500) {
            throw new ValidationException("Reason cannot exceed 500 characters.");
        }

        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + request.employeeId()));

        if (request.checkOutTime() != null && request.checkOutTime().isBefore(request.checkInTime())) {
            throw new ValidationException("Check-out time cannot precede check-in time.");
        }

        // Verify that no active (non-voided) attendance record exists for this employee on the requested work date
        if (attendanceRepository.existsByEmployeeIdAndWorkDateAndStatusNot(employee.getId(), request.workDate(), AttendanceStatus.VOIDED)) {
            throw new DuplicateResourceException("An attendance record already exists for " + employee.getFullName() +
                    " on " + request.workDate() + ". Please correct the existing record instead.");
        }

        AttendanceStatus status;
        if (request.checkOutTime() != null) {
            status = AttendanceStatus.CHECKED_OUT;
        } else {
            // Check that employee does not already have an active open session
            if (attendanceRepository.findByEmployeeIdAndStatus(employee.getId(), AttendanceStatus.CHECKED_IN).isPresent()) {
                throw new InvalidOperationException("Employee already has an active open session. Cannot create a second open session.");
            }
            status = AttendanceStatus.CHECKED_IN;
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setWorkDate(request.workDate());
        record.setCheckInTime(request.checkInTime());
        record.setCheckOutTime(request.checkOutTime());
        record.setStatus(status);
        record.setCorrected(false);
        record.setManualEntry(true);
        record.setLastCorrectionAt(Instant.now());

        AttendanceRecord savedRecord = attendanceRepository.save(record);

        // Create immutable audit event
        AttendanceCorrection auditEvent = new AttendanceCorrection(
                savedRecord,
                employee,
                CorrectionActionType.MANUAL_ENTRY,
                null,
                request.checkInTime(),
                null,
                request.checkOutTime(),
                null,
                status,
                cleanReason,
                performedBy,
                savedRecord.getVersion()
        );
        correctionRepository.save(auditEvent);

        log.info("Manual attendance entry created for employee '{}' on date {} by '{}'",
                employee.getEmployeeId(), request.workDate(), performedBy);

        return AttendanceResponse.from(savedRecord, "Manual attendance entry created successfully.");
    }

    /**
     * Retrieves the chronological audit history for a specific attendance record.
     */
    @Transactional(readOnly = true)
    public Page<AttendanceCorrectionResponse> getRecordAuditHistory(UUID recordId, int page, int size) {
        if (!attendanceRepository.existsById(recordId)) {
            throw new ResourceNotFoundException("Attendance record not found with id: " + recordId);
        }

        int safeSize = Math.min(Math.max(1, size), 50);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        return correctionRepository.findByAttendanceRecordIdOrderByCreatedAtDesc(recordId, pageable)
                .map(AttendanceCorrectionResponse::from);
    }

    /**
     * General administrator audit timeline search across all records.
     */
    @Transactional(readOnly = true)
    public Page<AttendanceCorrectionResponse> searchAuditHistory(UUID employeeId, LocalDate fromDate, LocalDate toDate, int page, int size) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ValidationException("fromDate cannot be after toDate.");
        }

        int safeSize = Math.min(Math.max(1, size), 50);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<AttendanceCorrection> spec = (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("employee", JoinType.LEFT);
                root.fetch("attendanceRecord", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (employeeId != null) {
                predicates.add(cb.equal(root.get("employee").get("id"), employeeId));
            }

            if (fromDate != null) {
                Instant fromInstant = fromDate.atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant));
            }

            if (toDate != null) {
                Instant toInstant = toDate.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), toInstant));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return correctionRepository.findAll(spec, pageable)
                .map(AttendanceCorrectionResponse::from);
    }
}
