package com.edifies.attendance.modules.dashboard.service;

import com.edifies.attendance.modules.attendance.model.AttendanceCorrection;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.attendance.service.AttendancePolicyService;
import com.edifies.attendance.modules.dashboard.dto.DashboardActivityItem;
import com.edifies.attendance.modules.dashboard.dto.DashboardAttentionItem;
import com.edifies.attendance.modules.dashboard.dto.DashboardQuickSearchEmployee;
import com.edifies.attendance.modules.dashboard.dto.DashboardSummaryResponse;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final AttendanceRepository attendanceRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendancePolicyService policyService;

    public DashboardService(AttendanceRepository attendanceRepository,
                            AttendanceCorrectionRepository correctionRepository,
                            EmployeeRepository employeeRepository,
                            AttendancePolicyService policyService) {
        this.attendanceRepository = attendanceRepository;
        this.correctionRepository = correctionRepository;
        this.employeeRepository = employeeRepository;
        this.policyService = policyService;
    }

    /**
     * Retrieves the comprehensive administrator dashboard summary for the specified work date.
     * All numbers are computed authoritatively via PostgreSQL database aggregate queries.
     */
    public DashboardSummaryResponse getDashboardSummary(LocalDate requestedDate) {
        Instant now = Instant.now();
        LocalDate effectiveWorkDate = (requestedDate != null)
                ? requestedDate
                : policyService.calculateWorkDate(now);

        log.debug("Generating dashboard summary for workDate='{}'", effectiveWorkDate);

        // 1. Employee Counts
        long activeEmployeeCount = employeeRepository.countByStatus(EmployeeStatus.ACTIVE);
        long totalEmployeeCount = employeeRepository.count();

        // 2. Authoritative Attendance Metric Counts for effectiveWorkDate
        long currentlyCheckedInCount = attendanceRepository.countDistinctEmployeesByWorkDateAndStatusAndEmployeeStatus(
                effectiveWorkDate, AttendanceStatus.CHECKED_IN, EmployeeStatus.ACTIVE);

        long completedSessionCount = attendanceRepository.countByWorkDateAndStatus(
                effectiveWorkDate, AttendanceStatus.CHECKED_OUT);

        long incompleteSessionCount = attendanceRepository.countIncompleteSessions(
                effectiveWorkDate, AttendanceStatus.AUTO_CLOSED, AttendanceStatus.CHECKED_IN);

        long distinctEmployeesWithAttendance = attendanceRepository.countDistinctEmployeesWithAttendanceOnWorkDate(
                effectiveWorkDate, AttendanceStatus.VOIDED, EmployeeStatus.ACTIVE);

        long notCheckedInCount = Math.max(0, activeEmployeeCount - distinctEmployeesWithAttendance);

        // 3. Recent Attendance Activity Feed (Bounded to 15 items, combining punches & corrections)
        List<DashboardActivityItem> recentActivities = buildRecentActivityList(effectiveWorkDate);

        // 4. Incomplete & Attention-Required Sessions (Bounded to 20 items)
        List<DashboardAttentionItem> attentionRequiredSessions = buildAttentionRequiredList(effectiveWorkDate, now);

        return new DashboardSummaryResponse(
                effectiveWorkDate,
                policyService.getZoneId().getId(),
                now,
                activeEmployeeCount,
                totalEmployeeCount,
                currentlyCheckedInCount,
                completedSessionCount,
                incompleteSessionCount,
                notCheckedInCount,
                recentActivities,
                attentionRequiredSessions
        );
    }

    /**
     * Fast, lightweight employee search for administrator dashboard quick navigation.
     */
    public List<DashboardQuickSearchEmployee> searchActiveEmployees(String query, LocalDate workDate) {
        LocalDate effectiveWorkDate = (workDate != null)
                ? workDate
                : policyService.calculateWorkDate(Instant.now());

        String safeQuery = (query != null) ? query.trim() : "";
        List<Employee> employees = employeeRepository.searchActiveEmployees(
                EmployeeStatus.ACTIVE, safeQuery, PageRequest.of(0, 15));

        return employees.stream().map(emp -> {
            Optional<AttendanceRecord> recOpt = attendanceRepository.findByEmployeeIdAndWorkDateAndStatusNot(
                    emp.getId(), effectiveWorkDate, AttendanceStatus.VOIDED);

            String todayStatus = "NO_RECORD";
            Instant lastCheckIn = null;
            UUID attendanceId = null;

            if (recOpt.isPresent()) {
                AttendanceRecord r = recOpt.get();
                todayStatus = r.getStatus().name();
                lastCheckIn = r.getCheckInTime();
                attendanceId = r.getId();
            }

            return new DashboardQuickSearchEmployee(
                    emp.getId(),
                    emp.getEmployeeId(),
                    emp.getFullName(),
                    emp.getDepartment(),
                    emp.getStatus(),
                    todayStatus,
                    lastCheckIn,
                    attendanceId
            );
        }).collect(Collectors.toList());
    }

    private List<DashboardActivityItem> buildRecentActivityList(LocalDate workDate) {
        List<DashboardActivityItem> items = new ArrayList<>();

        // Fetch latest 10 attendance records up to workDate
        List<AttendanceRecord> records = attendanceRepository.findRecentActivityRecords(
                workDate, PageRequest.of(0, 10));

        for (AttendanceRecord r : records) {
            if (r.getStatus() == AttendanceStatus.CHECKED_OUT && r.getCheckOutTime() != null) {
                items.add(new DashboardActivityItem(
                        r.getId(),
                        r.getEmployee().getId(),
                        r.getEmployee().getEmployeeId(),
                        r.getEmployee().getFullName(),
                        r.getEmployee().getDepartment(),
                        "CHECK_OUT",
                        r.getCheckOutTime(),
                        "Checked out for " + r.getWorkDate(),
                        r.getStatus().name(),
                        r.isHasCheckOutPhoto()
                ));
            } else if (r.getStatus() == AttendanceStatus.CHECKED_IN) {
                items.add(new DashboardActivityItem(
                        r.getId(),
                        r.getEmployee().getId(),
                        r.getEmployee().getEmployeeId(),
                        r.getEmployee().getFullName(),
                        r.getEmployee().getDepartment(),
                        "CHECK_IN",
                        r.getCheckInTime(),
                        "Checked in for " + r.getWorkDate(),
                        r.getStatus().name(),
                        r.isHasCheckInPhoto()
                ));
            } else if (r.getStatus() == AttendanceStatus.AUTO_CLOSED) {
                items.add(new DashboardActivityItem(
                        r.getId(),
                        r.getEmployee().getId(),
                        r.getEmployee().getEmployeeId(),
                        r.getEmployee().getFullName(),
                        r.getEmployee().getDepartment(),
                        "AUTO_CLOSED",
                        r.getUpdatedAt(),
                        "Session auto-closed at cutoff for " + r.getWorkDate(),
                        r.getStatus().name(),
                        r.isHasCheckInPhoto()
                ));
            }
        }

        // Fetch latest 5 corrections
        List<AttendanceCorrection> corrections = correctionRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5));
        for (AttendanceCorrection c : corrections) {
            items.add(new DashboardActivityItem(
                    c.getAttendanceRecord() != null ? c.getAttendanceRecord().getId() : null,
                    c.getEmployee() != null ? c.getEmployee().getId() : null,
                    c.getEmployee() != null ? c.getEmployee().getEmployeeId() : "N/A",
                    c.getEmployee() != null ? c.getEmployee().getFullName() : "Staff",
                    c.getEmployee() != null ? c.getEmployee().getDepartment() : "General",
                    "CORRECTION",
                    c.getCreatedAt(),
                    "Admin correction: " + c.getReason(),
                    c.getActionType() != null ? c.getActionType().name() : "CORRECTED",
                    false
            ));
        }

        // Sort combined list descending by timestamp, limited to 15 items
        return items.stream()
                .filter(i -> i.timestamp() != null)
                .sorted(Comparator.comparing(DashboardActivityItem::timestamp).reversed())
                .limit(15)
                .collect(Collectors.toList());
    }

    private List<DashboardAttentionItem> buildAttentionRequiredList(LocalDate effectiveWorkDate, Instant now) {
        Instant overdueThreshold = now.minus(12, ChronoUnit.HOURS);

        List<AttendanceRecord> records = attendanceRepository.findAttentionRequiredRecords(
                effectiveWorkDate,
                AttendanceStatus.AUTO_CLOSED,
                AttendanceStatus.CHECKED_IN,
                overdueThreshold,
                PageRequest.of(0, 20)
        );

        return records.stream().map(r -> {
            String issueType;
            String issueDesc;
            String recommendation;

            if (r.getStatus() == AttendanceStatus.AUTO_CLOSED) {
                issueType = "AUTO_CLOSED";
                issueDesc = "Session auto-closed at cutoff without employee checkout.";
                recommendation = "Review and apply manual checkout correction if employee worked.";
            } else if (r.getWorkDate().isBefore(effectiveWorkDate)) {
                issueType = "STALE_OPEN_SESSION";
                issueDesc = "Session remains open from previous work date (" + r.getWorkDate() + ").";
                recommendation = "Close or correct checkout timestamp.";
            } else {
                issueType = "OVERDUE_OPEN_SESSION";
                issueDesc = "Open session exceeded 12 hours since check-in.";
                recommendation = "Verify if employee is still on duty or forgot to punch out.";
            }

            return new DashboardAttentionItem(
                    r.getId(),
                    r.getEmployee().getId(),
                    r.getEmployee().getEmployeeId(),
                    r.getEmployee().getFullName(),
                    r.getEmployee().getDepartment(),
                    r.getWorkDate(),
                    r.getCheckInTime(),
                    r.getStatus().name(),
                    issueType,
                    issueDesc,
                    recommendation
            );
        }).collect(Collectors.toList());
    }
}
