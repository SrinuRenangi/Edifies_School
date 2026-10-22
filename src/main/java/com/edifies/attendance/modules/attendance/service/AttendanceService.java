package com.edifies.attendance.modules.attendance.service;

import com.edifies.attendance.common.exception.InvalidOperationException;
import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.modules.attendance.dto.AttendanceResponse;
import com.edifies.attendance.modules.attendance.dto.AttendanceStatusResponse;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.kiosk.service.KioskSessionService;
import com.edifies.attendance.common.exception.ValidationException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.edifies.attendance.modules.photo.model.PhotoType;
import com.edifies.attendance.modules.photo.service.AttendancePhotoService;
import org.springframework.web.multipart.MultipartFile;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final KioskSessionService kioskSessionService;
    private final AttendancePolicyService policyService;
    private final IdempotencyService idempotencyService;
    private final AttendancePhotoService attendancePhotoService;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             EmployeeRepository employeeRepository,
                             KioskSessionService kioskSessionService,
                             AttendancePolicyService policyService,
                             IdempotencyService idempotencyService,
                             AttendancePhotoService attendancePhotoService) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.kioskSessionService = kioskSessionService;
        this.policyService = policyService;
        this.idempotencyService = idempotencyService;
        this.attendancePhotoService = attendancePhotoService;
    }


    /**
     * Retrieves the current attendance status and allowed next action for an authenticated kiosk session.
     */
    @Transactional(readOnly = false)
    public AttendanceStatusResponse getAttendanceStatus(String kioskSessionToken) {
        Employee employee = getActiveEmployeeFromSession(kioskSessionToken);
        Instant now = Instant.now();
        LocalDate workDate = policyService.calculateWorkDate(now);

        // Check for an active open session
        Optional<AttendanceRecord> openOpt = attendanceRepository.findByEmployeeIdAndStatus(employee.getId(), AttendanceStatus.CHECKED_IN);
        if (openOpt.isPresent()) {
            AttendanceRecord openRecord = openOpt.get();
            if (openRecord.getWorkDate().equals(workDate)) {
                // Currently checked in today
                return new AttendanceStatusResponse(
                        employee.getId(),
                        employee.getEmployeeId(),
                        employee.getFullName(),
                        employee.getDepartment(),
                        workDate,
                        "CHECKED_IN",
                        "CHECK_OUT",
                        openRecord.getCheckInTime(),
                        null
                );
            } else {
                // Cut-off passed: Auto-close yesterday's stale session per state-machine specification
                log.info("Auto-closing stale open session for employeeId='{}' from workDate='{}'", employee.getEmployeeId(), openRecord.getWorkDate());
                openRecord.setStatus(AttendanceStatus.AUTO_CLOSED);
                attendanceRepository.save(openRecord);
            }
        }

        // Check if employee has already checked out today
        Optional<AttendanceRecord> todayOpt = attendanceRepository.findByEmployeeIdAndWorkDateAndStatusNot(employee.getId(), workDate, AttendanceStatus.VOIDED);
        if (todayOpt.isPresent()) {
            AttendanceRecord todayRecord = todayOpt.get();
            if (todayRecord.getStatus() == AttendanceStatus.CHECKED_OUT || todayRecord.getStatus() == AttendanceStatus.AUTO_CLOSED) {
                return new AttendanceStatusResponse(
                        employee.getId(),
                        employee.getEmployeeId(),
                        employee.getFullName(),
                        employee.getDepartment(),
                        workDate,
                        todayRecord.getStatus().name(),
                        "NONE",
                        todayRecord.getCheckInTime(),
                        todayRecord.getCheckOutTime()
                );
            }
        }

        // No attendance today yet
        return new AttendanceStatusResponse(
                employee.getId(),
                employee.getEmployeeId(),
                employee.getFullName(),
                employee.getDepartment(),
                workDate,
                "NOT_CHECKED_IN",
                "CHECK_IN",
                null,
                null
        );
    }

    /**
     * Executes check-in for an authenticated employee.
     */
    /**
     * Executes check-in for an authenticated employee without photo.
     */
    public AttendanceResponse checkIn(String kioskSessionToken, String idempotencyKey) {
        return checkIn(kioskSessionToken, null, idempotencyKey);
    }

    /**
     * Executes check-in for an authenticated employee with photo evidence.
     */
    public AttendanceResponse checkIn(String kioskSessionToken, MultipartFile photo, String idempotencyKey) {
        // 1. Idempotency check: if already processed, return original response
        Optional<AttendanceResponse> cached = idempotencyService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. Pre-validate photo if provided (guarantees zero false attendance on invalid/corrupted photo)
        if (photo != null && !photo.isEmpty()) {
            attendancePhotoService.validatePhoto(photo);
        }

        // 3. Resolve employee and verify ACTIVE status
        Employee employee = getActiveEmployeeFromSession(kioskSessionToken);


        // 3. Determine authoritative server timestamp and work date
        Instant now = Instant.now();
        LocalDate workDate = policyService.calculateWorkDate(now);

        // 4. Verify no open session exists for today
        Optional<AttendanceRecord> openOpt = attendanceRepository.findByEmployeeIdAndStatus(employee.getId(), AttendanceStatus.CHECKED_IN);
        if (openOpt.isPresent()) {
            AttendanceRecord openRecord = openOpt.get();
            if (openRecord.getWorkDate().equals(workDate)) {
                throw new InvalidOperationException("Employee is already checked in for work date " + workDate + ".");
            } else {
                // Cut-off passed: Auto-close stale session from previous day
                openRecord.setStatus(AttendanceStatus.AUTO_CLOSED);
                attendanceRepository.save(openRecord);
            }
        }

        // 5. Verify no active completed record exists for today
        Optional<AttendanceRecord> todayOpt = attendanceRepository.findByEmployeeIdAndWorkDateAndStatusNot(employee.getId(), workDate, AttendanceStatus.VOIDED);
        if (todayOpt.isPresent()) {
            throw new InvalidOperationException("Employee has already completed attendance for work date " + workDate + ".");
        }

        // 6. Create and persist attendance record
        AttendanceRecord record = new AttendanceRecord(employee, workDate, now);
        AttendanceRecord saved = attendanceRepository.saveAndFlush(record);

        // 7. Associate check-in photo evidence if provided
        if (photo != null && !photo.isEmpty()) {
            attendancePhotoService.recordPhoto(saved, employee, PhotoType.CHECK_IN, photo);
            saved = attendanceRepository.saveAndFlush(saved);
        }

        log.info("Check-in successful: employeeId='{}', workDate='{}', timestamp='{}', hasPhoto={}",
                employee.getEmployeeId(), workDate, now, saved.isHasCheckInPhoto());

        AttendanceResponse response = AttendanceResponse.from(saved, "Check-in recorded successfully. Welcome!");

        // 8. Store idempotency record
        idempotencyService.recordResponse(idempotencyKey, "/api/kiosk/attendance/check-in", employee.getId(), 200, response);

        return response;
    }

    /**
     * Executes check-out for an authenticated employee without photo.
     */
    public AttendanceResponse checkOut(String kioskSessionToken, String idempotencyKey) {
        return checkOut(kioskSessionToken, null, idempotencyKey);
    }

    /**
     * Executes check-out for an authenticated employee with photo evidence.
     */
    public AttendanceResponse checkOut(String kioskSessionToken, MultipartFile photo, String idempotencyKey) {
        // 1. Idempotency check
        Optional<AttendanceResponse> cached = idempotencyService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. Pre-validate photo if provided (guarantees zero false attendance on invalid/corrupted photo)
        if (photo != null && !photo.isEmpty()) {
            attendancePhotoService.validatePhoto(photo);
        }

        // 3. Resolve employee and verify ACTIVE status
        Employee employee = getActiveEmployeeFromSession(kioskSessionToken);


        // 3. Find open session
        Optional<AttendanceRecord> openOpt = attendanceRepository.findByEmployeeIdAndStatus(employee.getId(), AttendanceStatus.CHECKED_IN);
        if (openOpt.isEmpty()) {
            LocalDate workDate = policyService.calculateWorkDate(Instant.now());
            Optional<AttendanceRecord> todayOpt = attendanceRepository.findByEmployeeIdAndWorkDateAndStatusNot(employee.getId(), workDate, AttendanceStatus.VOIDED);
            if (todayOpt.isPresent() && todayOpt.get().getStatus() == AttendanceStatus.CHECKED_OUT) {
                throw new InvalidOperationException("Employee has already checked out for work date " + workDate + ".");
            }
            throw new InvalidOperationException("No active check-in found. Employee must check in before checking out.");
        }

        AttendanceRecord record = openOpt.get();

        // 4. Determine authoritative server timestamp
        Instant now = Instant.now();
        if (now.isBefore(record.getCheckInTime())) {
            throw new InvalidOperationException("Check-out timestamp cannot be earlier than check-in timestamp.");
        }

        // 5. Update session to closed
        record.setCheckOutTime(now);
        record.setStatus(AttendanceStatus.CHECKED_OUT);

        // 6. Associate check-out photo evidence if provided
        if (photo != null && !photo.isEmpty()) {
            attendancePhotoService.recordPhoto(record, employee, PhotoType.CHECK_OUT, photo);
        }

        AttendanceRecord saved = attendanceRepository.saveAndFlush(record);
        log.info("Check-out successful: employeeId='{}', workDate='{}', timestamp='{}', hasPhoto={}",
                employee.getEmployeeId(), record.getWorkDate(), now, saved.isHasCheckOutPhoto());

        AttendanceResponse response = AttendanceResponse.from(saved, "Check-out recorded successfully. Goodbye!");

        // 7. Store idempotency record
        idempotencyService.recordResponse(idempotencyKey, "/api/kiosk/attendance/check-out", employee.getId(), 200, response);

        return response;
    }


    /**
     * Paginated and filtered attendance history for Administrator Portal.
     */
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> getAdminAttendanceHistory(
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            AttendanceStatus status,
            int page,
            int size) {
        return getAdminAttendanceHistory(search, null, fromDate, toDate, status, page, size);
    }

    /**
     * Paginated and filtered attendance history with optional employee UUID filter.
     */
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> getAdminAttendanceHistory(
            String search,
            UUID employeeId,
            LocalDate fromDate,
            LocalDate toDate,
            AttendanceStatus status,
            int page,
            int size) {

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ValidationException("fromDate cannot be after toDate.");
        }

        int safeSize = Math.min(Math.max(1, size), 100);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "workDate", "checkInTime", "id"));

        Specification<AttendanceRecord> spec = (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("employee", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (employeeId != null) {
                predicates.add(cb.equal(root.get("employee").get("id"), employeeId));
            }

            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase() + "%";
                Predicate codeMatch = cb.like(cb.lower(root.get("employee").get("employeeId")), term);
                Predicate nameMatch = cb.like(cb.lower(root.get("employee").get("fullName")), term);
                Predicate deptMatch = cb.like(cb.lower(root.get("employee").get("department")), term);
                predicates.add(cb.or(codeMatch, nameMatch, deptMatch));
            }

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("workDate"), fromDate));
            }

            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("workDate"), toDate));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return attendanceRepository.findAll(spec, pageable)
                .map(record -> AttendanceResponse.from(record, null));
    }

    /**
     * Retrieves an attendance record by its UUID.
     */
    @Transactional(readOnly = true)
    public AttendanceRecord getAttendanceRecordById(UUID id) {
        return attendanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found with id: " + id));
    }

    /**
     * Paginated attendance history for a single employee.
     */
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> getEmployeeAttendanceHistory(
            UUID employeeId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size) {

        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee not found with id: " + employeeId);
        }

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ValidationException("fromDate cannot be after toDate.");
        }

        int safeSize = Math.min(Math.max(1, size), 100);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "workDate", "checkInTime", "id"));

        if (fromDate != null && toDate != null) {
            return attendanceRepository.findByEmployeeIdAndWorkDateBetween(employeeId, fromDate, toDate, pageable)
                    .map(record -> AttendanceResponse.from(record, null));
        }

        return attendanceRepository.findByEmployeeId(employeeId, pageable)
                .map(record -> AttendanceResponse.from(record, null));
    }

    private Employee getActiveEmployeeFromSession(String token) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Kiosk session token is required.");
        }

        KioskSessionService.KioskSession session = kioskSessionService.validateSession(token.trim());
        if (session == null) {
            throw new BadCredentialsException("Kiosk session expired or invalid. Please authenticate again.");
        }

        Employee employee = employeeRepository.findById(session.employeeUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for session."));

        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            log.warn("Attendance rejected: employeeId='{}' is INACTIVE", employee.getEmployeeId());
            throw new InvalidOperationException("Employee account is inactive. Attendance actions are disabled.");
        }

        return employee;
    }
}
