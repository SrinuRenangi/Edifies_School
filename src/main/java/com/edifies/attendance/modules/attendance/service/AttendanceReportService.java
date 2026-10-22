package com.edifies.attendance.modules.attendance.service;

import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.common.exception.ValidationException;
import com.edifies.attendance.modules.attendance.dto.AttendanceResponse;
import com.edifies.attendance.modules.attendance.dto.report.DailyAttendanceSummary;
import com.edifies.attendance.modules.attendance.dto.report.EmployeeMonthlySummary;
import com.edifies.attendance.modules.attendance.dto.report.MonthlyReportResponse;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AttendanceReportService {

    private static final ZoneId COMPANY_TIMEZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(COMPANY_TIMEZONE);
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    public AttendanceReportService(AttendanceRepository attendanceRepository, EmployeeRepository employeeRepository) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Generates a monthly attendance summary aggregated directly from PostgreSQL.
     */
    public MonthlyReportResponse generateMonthlyReport(int month, int year, UUID employeeId, String search) {
        if (month < 1 || month > 12) {
            throw new ValidationException("Month must be between 1 and 12.");
        }
        if (year < 2020 || year > 2100) {
            throw new ValidationException("Year must be between 2020 and 2100.");
        }

        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());
        int totalDaysInMonth = startOfMonth.lengthOfMonth();

        // 1. Resolve employees matching filter (including inactive staff for historical integrity)
        List<Employee> targetEmployees;
        if (employeeId != null) {
            Employee emp = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
            targetEmployees = List.of(emp);
        } else {
            List<Employee> allEmployees = employeeRepository.findAll(Sort.by(Sort.Direction.ASC, "employeeId"));
            if (search != null && !search.isBlank()) {
                String term = search.trim().toLowerCase();
                targetEmployees = allEmployees.stream()
                        .filter(e -> (e.getEmployeeId() != null && e.getEmployeeId().toLowerCase().contains(term))
                                || (e.getFullName() != null && e.getFullName().toLowerCase().contains(term))
                                || (e.getDepartment() != null && e.getDepartment().toLowerCase().contains(term)))
                        .collect(Collectors.toList());
            } else {
                targetEmployees = allEmployees;
            }
        }

        // 2. Query attendance records for the month with eagerly fetched employee associations
        List<AttendanceRecord> monthRecords;
        if (employeeId != null) {
            monthRecords = attendanceRepository.findAllWithEmployeeByEmployeeIdAndWorkDateBetween(employeeId, startOfMonth, endOfMonth);
        } else {
            monthRecords = attendanceRepository.findAllWithEmployeeByWorkDateBetween(startOfMonth, endOfMonth);
        }

        // Group attendance records by employee UUID and work date (preferring non-voided active records)
        Map<UUID, Map<LocalDate, AttendanceRecord>> employeeRecordsMap = new HashMap<>();
        for (AttendanceRecord record : monthRecords) {
            UUID empUuid = record.getEmployee().getId();
            Map<LocalDate, AttendanceRecord> dateMap = employeeRecordsMap.computeIfAbsent(empUuid, k -> new HashMap<>());
            if (!dateMap.containsKey(record.getWorkDate()) || dateMap.get(record.getWorkDate()).getStatus() == AttendanceStatus.VOIDED) {
                dateMap.put(record.getWorkDate(), record);
            }
        }

        // 3. Construct summaries for each employee
        List<EmployeeMonthlySummary> employeeSummaries = new ArrayList<>();
        long grandTotalRecordedSessions = 0;
        long grandTotalCompletedSessions = 0;
        long grandTotalIncompleteSessions = 0;
        long grandTotalDurationMinutes = 0;

        for (Employee emp : targetEmployees) {
            Map<LocalDate, AttendanceRecord> empDayMap = employeeRecordsMap.getOrDefault(emp.getId(), Collections.emptyMap());

            List<DailyAttendanceSummary> dailyList = new ArrayList<>(totalDaysInMonth);
            long empSessions = 0;
            long empCompleted = 0;
            long empIncomplete = 0;
            long empDurationMinutes = 0;

            for (int day = 1; day <= totalDaysInMonth; day++) {
                LocalDate date = LocalDate.of(year, month, day);
                AttendanceRecord record = empDayMap.get(date);

                if (record != null) {
                    if (record.getStatus() == AttendanceStatus.VOIDED) {
                        dailyList.add(new DailyAttendanceSummary(
                                date,
                                record.getId(),
                                record.getStatus(),
                                record.getCheckInTime(),
                                record.getCheckOutTime(),
                                null,
                                "—",
                                record.isHasCheckInPhoto(),
                                record.isHasCheckOutPhoto(),
                                false,
                                "Voided / Erroneous record"
                        ));
                    } else {
                        empSessions++;
                        boolean isCompleted = record.getStatus() == AttendanceStatus.CHECKED_OUT;
                        if (isCompleted) {
                            empCompleted++;
                        } else {
                            empIncomplete++;
                        }

                        Long durationMinutes = AttendanceResponse.calculateDurationMinutes(record.getCheckInTime(), record.getCheckOutTime());
                        if (durationMinutes != null) {
                            empDurationMinutes += durationMinutes;
                        }
                        String durationFormatted = AttendanceResponse.formatDuration(record.getCheckInTime(), record.getCheckOutTime(), record.getStatus());

                        String note = isCompleted ? "Completed" : (record.getStatus() == AttendanceStatus.CHECKED_IN ? "Active / Open" : "Auto Closed");

                        dailyList.add(new DailyAttendanceSummary(
                                date,
                                record.getId(),
                                record.getStatus(),
                                record.getCheckInTime(),
                                record.getCheckOutTime(),
                                durationMinutes,
                                durationFormatted,
                                record.isHasCheckInPhoto(),
                                record.isHasCheckOutPhoto(),
                                true,
                                note
                        ));
                    }
                } else {
                    dailyList.add(DailyAttendanceSummary.noRecord(date));
                }
            }

            grandTotalRecordedSessions += empSessions;
            grandTotalCompletedSessions += empCompleted;
            grandTotalIncompleteSessions += empIncomplete;
            grandTotalDurationMinutes += empDurationMinutes;

            String empDurationFormatted = String.format("%dh %02dm", empDurationMinutes / 60, empDurationMinutes % 60);

            employeeSummaries.add(new EmployeeMonthlySummary(
                    emp.getId(),
                    emp.getEmployeeId(),
                    emp.getFullName(),
                    emp.getDepartment(),
                    emp.getStatus().name(),
                    empSessions,
                    empCompleted,
                    empIncomplete,
                    empDurationMinutes,
                    empDurationFormatted,
                    dailyList
            ));
        }

        String overallDurationFormatted = String.format("%dh %02dm", grandTotalDurationMinutes / 60, grandTotalDurationMinutes % 60);

        return new MonthlyReportResponse(
                month,
                year,
                totalDaysInMonth,
                targetEmployees.size(),
                grandTotalRecordedSessions,
                grandTotalCompletedSessions,
                grandTotalIncompleteSessions,
                grandTotalDurationMinutes,
                overallDurationFormatted,
                employeeSummaries
        );
    }

    /**
     * Exports filtered attendance sessions to RFC 4180 compliant CSV with formula-injection neutralization.
     */
    public byte[] exportAttendanceCsv(String search, UUID employeeId, LocalDate fromDate, LocalDate toDate, AttendanceStatus status) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ValidationException("fromDate cannot be after toDate.");
        }

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

        List<AttendanceRecord> records = attendanceRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "workDate", "checkInTime", "id"));

        StringBuilder sb = new StringBuilder();
        // Header row
        sb.append("Employee ID,Employee Name,Department,Work Date,Check-In Time (IST),Check-Out Time (IST),Status,Duration,Check-In Photo,Check-Out Photo,Correction Indicator\r\n");

        for (AttendanceRecord r : records) {
            String empId = escapeCsvCell(r.getEmployee().getEmployeeId());
            String empName = escapeCsvCell(r.getEmployee().getFullName());
            String dept = escapeCsvCell(r.getEmployee().getDepartment());
            String workDate = escapeCsvCell(r.getWorkDate().toString());

            String checkInStr = r.getCheckInTime() != null ? TIME_FORMATTER.format(r.getCheckInTime()) : "";
            String checkOutStr;
            if (r.getStatus() == AttendanceStatus.VOIDED) {
                checkOutStr = r.getCheckOutTime() != null ? TIME_FORMATTER.format(r.getCheckOutTime()) : "Voided";
            } else {
                checkOutStr = r.getCheckOutTime() != null ? TIME_FORMATTER.format(r.getCheckOutTime()) : "Missing / Open";
            }

            String statusStr = escapeCsvCell(r.getStatus().name());
            String durationStr = escapeCsvCell(AttendanceResponse.formatDuration(r.getCheckInTime(), r.getCheckOutTime(), r.getStatus()));

            String checkInPhoto = r.isHasCheckInPhoto() ? "Yes" : "No";
            String checkOutPhoto = r.isHasCheckOutPhoto() ? "Yes" : "No";
            String correction;
            if (r.isManualEntry()) {
                correction = "Manual Entry";
            } else if (r.isCorrected()) {
                correction = "Corrected";
            } else {
                correction = "Original";
            }

            sb.append(empId).append(',')
                    .append(empName).append(',')
                    .append(dept).append(',')
                    .append(workDate).append(',')
                    .append(escapeCsvCell(checkInStr)).append(',')
                    .append(escapeCsvCell(checkOutStr)).append(',')
                    .append(statusStr).append(',')
                    .append(durationStr).append(',')
                    .append(escapeCsvCell(checkInPhoto)).append(',')
                    .append(escapeCsvCell(checkOutPhoto)).append(',')
                    .append(escapeCsvCell(correction)).append("\r\n");
        }

        byte[] contentBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + contentBytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(contentBytes, 0, result, UTF8_BOM.length, contentBytes.length);
        return result;
    }

    /**
     * Exports monthly summary statistics by employee to CSV.
     */
    public byte[] exportMonthlySummaryCsv(int month, int year, UUID employeeId, String search) {
        MonthlyReportResponse report = generateMonthlyReport(month, year, employeeId, search);

        StringBuilder sb = new StringBuilder();
        sb.append("Employee ID,Employee Name,Department,Status,Month,Year,Total Sessions,Completed Sessions,Incomplete Sessions,Total Duration\r\n");

        for (EmployeeMonthlySummary s : report.employeeSummaries()) {
            sb.append(escapeCsvCell(s.employeeCode())).append(',')
                    .append(escapeCsvCell(s.employeeName())).append(',')
                    .append(escapeCsvCell(s.department())).append(',')
                    .append(escapeCsvCell(s.employeeStatus())).append(',')
                    .append(month).append(',')
                    .append(year).append(',')
                    .append(s.totalSessions()).append(',')
                    .append(s.completedSessions()).append(',')
                    .append(s.incompleteSessions()).append(',')
                    .append(escapeCsvCell(s.totalDurationFormatted())).append("\r\n");
        }

        byte[] contentBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + contentBytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(contentBytes, 0, result, UTF8_BOM.length, contentBytes.length);
        return result;
    }

    /**
     * Neutralizes spreadsheet formula injection risks and quotes fields containing commas, quotes, or newlines.
     */
    public static String escapeCsvCell(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();

        // 1. Prevent Spreadsheet Formula Injection (CSV Injection)
        if (!text.isEmpty()) {
            char first = text.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                text = "'" + text;
            }
        }

        // 2. RFC 4180 Escaping for delimiters and line breaks
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
