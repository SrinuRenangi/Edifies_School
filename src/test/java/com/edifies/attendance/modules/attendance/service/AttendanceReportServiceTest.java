package com.edifies.attendance.modules.attendance.service;

import com.edifies.attendance.common.exception.ValidationException;
import com.edifies.attendance.modules.attendance.dto.report.DailyAttendanceSummary;
import com.edifies.attendance.modules.attendance.dto.report.MonthlyReportResponse;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceReportServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private AttendanceReportService reportService;

    private Employee activeEmp;
    private Employee inactiveEmp;

    @BeforeEach
    void setUp() {
        reportService = new AttendanceReportService(attendanceRepository, employeeRepository);

        activeEmp = new Employee("EMP-101", "Alice Smith", "Science", "$2a$12$fakeHash");
        activeEmp.setId(UUID.randomUUID());

        inactiveEmp = new Employee("EMP-102", "Bob Jones", "Math", "$2a$12$fakeHash");
        inactiveEmp.setId(UUID.randomUUID());
        inactiveEmp.setStatus(EmployeeStatus.INACTIVE);
    }

    @Test
    @DisplayName("Monthly report aggregates completed, incomplete, and durations accurately")
    void testGenerateMonthlyReport_aggregatesCorrectly() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);

        when(employeeRepository.findAll(any(Sort.class))).thenReturn(List.of(activeEmp));

        // Shift 1: Completed (8 hours)
        AttendanceRecord r1 = new AttendanceRecord(activeEmp, LocalDate.of(2026, 10, 5), Instant.parse("2026-10-05T03:30:00Z"));
        r1.setId(UUID.randomUUID());
        r1.setCheckOutTime(Instant.parse("2026-10-05T11:30:00Z")); // 8 hours
        r1.setStatus(AttendanceStatus.CHECKED_OUT);

        // Shift 2: Incomplete (open session)
        AttendanceRecord r2 = new AttendanceRecord(activeEmp, LocalDate.of(2026, 10, 6), Instant.parse("2026-10-06T03:30:00Z"));
        r2.setId(UUID.randomUUID());
        r2.setStatus(AttendanceStatus.CHECKED_IN);

        when(attendanceRepository.findAllWithEmployeeByWorkDateBetween(eq(start), eq(end)))
                .thenReturn(List.of(r1, r2));

        MonthlyReportResponse report = reportService.generateMonthlyReport(10, 2026, null, null);

        assertThat(report).isNotNull();
        assertThat(report.month()).isEqualTo(10);
        assertThat(report.year()).isEqualTo(2026);
        assertThat(report.totalDaysInMonth()).isEqualTo(31);
        assertThat(report.totalEmployees()).isEqualTo(1);
        assertThat(report.totalRecordedSessions()).isEqualTo(2);
        assertThat(report.totalCompletedSessions()).isEqualTo(1);
        assertThat(report.totalIncompleteSessions()).isEqualTo(1);
        assertThat(report.overallDurationMinutes()).isEqualTo(480); // 8h = 480m
        assertThat(report.overallDurationFormatted()).isEqualTo("8h 00m");

        assertThat(report.employeeSummaries()).hasSize(1);
        var empSumm = report.employeeSummaries().get(0);
        assertThat(empSumm.employeeCode()).isEqualTo("EMP-101");
        assertThat(empSumm.totalSessions()).isEqualTo(2);
        assertThat(empSumm.completedSessions()).isEqualTo(1);
        assertThat(empSumm.incompleteSessions()).isEqualTo(1);
        assertThat(empSumm.totalDurationMinutes()).isEqualTo(480);
        assertThat(empSumm.dailyRecords()).hasSize(31);
    }

    @Test
    @DisplayName("Unrecorded dates in monthly breakdown are neutral 'No attendance record', not 'Absent'")
    void testGenerateMonthlyReport_unrecordedDaysAreNeutral() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);

        when(employeeRepository.findAll(any(Sort.class))).thenReturn(List.of(activeEmp));
        when(attendanceRepository.findAllWithEmployeeByWorkDateBetween(eq(start), eq(end)))
                .thenReturn(List.of());

        MonthlyReportResponse report = reportService.generateMonthlyReport(10, 2026, null, null);

        assertThat(report.employeeSummaries()).hasSize(1);
        DailyAttendanceSummary day1 = report.employeeSummaries().get(0).dailyRecords().get(0);
        assertThat(day1.hasRecord()).isFalse();
        assertThat(day1.note()).isEqualTo("No attendance record");
        assertThat(day1.durationFormatted()).isEqualTo("—");
        assertThat(day1.status()).isNull();
    }

    @Test
    @DisplayName("Historical monthly reporting retains inactive employees")
    void testGenerateMonthlyReport_retainsInactiveEmployees() {
        when(employeeRepository.findAll(any(Sort.class))).thenReturn(List.of(activeEmp, inactiveEmp));
        when(attendanceRepository.findAllWithEmployeeByWorkDateBetween(any(), any())).thenReturn(List.of());

        MonthlyReportResponse report = reportService.generateMonthlyReport(10, 2026, null, null);

        assertThat(report.totalEmployees()).isEqualTo(2);
        assertThat(report.employeeSummaries())
                .extracting(s -> s.employeeStatus())
                .containsExactlyInAnyOrder("ACTIVE", "INACTIVE");
    }

    @Test
    @DisplayName("Invalid month or year rejects with ValidationException")
    void testGenerateMonthlyReport_invalidBounds_throwsValidationException() {
        assertThatThrownBy(() -> reportService.generateMonthlyReport(0, 2026, null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Month must be between 1 and 12");

        assertThatThrownBy(() -> reportService.generateMonthlyReport(13, 2026, null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Month must be between 1 and 12");

        assertThatThrownBy(() -> reportService.generateMonthlyReport(10, 2018, null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Year must be between 2020 and 2100");
    }

    @Test
    @DisplayName("Formula injection defense neutralizes cells starting with =, +, -, @, \\t, \\r")
    void testEscapeCsvCell_neutralizesFormulaInjection() {
        assertThat(AttendanceReportService.escapeCsvCell("=SUM(A1:A10)")).isEqualTo("'=SUM(A1:A10)");
        assertThat(AttendanceReportService.escapeCsvCell("+123456789")).isEqualTo("'+123456789");
        assertThat(AttendanceReportService.escapeCsvCell("-CMD|' /C calc'!A0")).isEqualTo("'-CMD|' /C calc'!A0");
        assertThat(AttendanceReportService.escapeCsvCell("@hyperlink")).isEqualTo("'@hyperlink");
        assertThat(AttendanceReportService.escapeCsvCell("Normal Text")).isEqualTo("Normal Text");
    }

    @Test
    @DisplayName("CSV escaping quotes cells containing commas, double quotes, and newlines")
    void testEscapeCsvCell_rfc4180Delimiters() {
        assertThat(AttendanceReportService.escapeCsvCell("Doe, John")).isEqualTo("\"Doe, John\"");
        assertThat(AttendanceReportService.escapeCsvCell("Quotes \"Here\"")).isEqualTo("\"Quotes \"\"Here\"\"\"");
        assertThat(AttendanceReportService.escapeCsvCell("Line1\nLine2")).isEqualTo("\"Line1\nLine2\"");
    }

    @Test
    @DisplayName("Duration calculation accurately handles shifts crossing midnight")
    void testCalculateDuration_handlesMidnightCrossing() {
        Instant in = Instant.parse("2026-10-22T17:00:00Z"); // 22:30 IST
        Instant out = Instant.parse("2026-10-23T01:30:00Z"); // 07:00 IST next day (8.5 hours)

        Duration duration = Duration.between(in, out);
        assertThat(duration.toMinutes()).isEqualTo(510); // 8h 30m

        long hours = duration.toHours();
        long mins = duration.toMinutesPart();
        String formatted = String.format("%dh %02dm", hours, mins);
        assertThat(formatted).isEqualTo("8h 30m");
    }

    @Test
    @DisplayName("exportAttendanceCsv rejects invalid date range where fromDate is after toDate")
    void testExportAttendanceCsv_invalidDateRange_throwsValidationException() {
        LocalDate from = LocalDate.of(2026, 10, 25);
        LocalDate to = LocalDate.of(2026, 10, 20);

        assertThatThrownBy(() -> reportService.exportAttendanceCsv(null, null, from, to, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("fromDate cannot be after toDate");
    }
}
