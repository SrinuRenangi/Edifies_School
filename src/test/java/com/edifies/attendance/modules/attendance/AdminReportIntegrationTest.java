package com.edifies.attendance.modules.attendance;

import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminReportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AttendanceCorrectionRepository attendanceCorrectionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Employee employee1;
    private Employee inactiveEmployee;

    @BeforeEach
    void setUp() {
        attendanceCorrectionRepository.deleteAll();
        attendanceRepository.deleteAll();
        employeeRepository.deleteAll();

        String hashedPin = passwordEncoder.encode("1234");
        employee1 = new Employee("EMP-REP-01", "Isaac Newton", "Physics", hashedPin);
        employee1 = employeeRepository.saveAndFlush(employee1);

        inactiveEmployee = new Employee("EMP-REP-02", "Marie Curie", "Chemistry", hashedPin);
        inactiveEmployee.setStatus(EmployeeStatus.INACTIVE);
        inactiveEmployee = employeeRepository.saveAndFlush(inactiveEmployee);
    }

    @AfterEach
    void tearDown() {
        attendanceCorrectionRepository.deleteAll();
        attendanceRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Daily attendance: filters by exact date and excludes adjacent dates")
    void dailyAttendance_filtersByExactDate_excludesAdjacentDates() throws Exception {
        // Record on 2026-10-15
        AttendanceRecord r1 = new AttendanceRecord(employee1, LocalDate.of(2026, 10, 15), Instant.parse("2026-10-15T03:30:00Z"));
        r1.setCheckOutTime(Instant.parse("2026-10-15T11:30:00Z"));
        r1.setStatus(AttendanceStatus.CHECKED_OUT);
        attendanceRepository.saveAndFlush(r1);

        // Record on 2026-10-16 (adjacent date)
        AttendanceRecord r2 = new AttendanceRecord(employee1, LocalDate.of(2026, 10, 16), Instant.parse("2026-10-16T03:30:00Z"));
        r2.setStatus(AttendanceStatus.CHECKED_IN);
        attendanceRepository.saveAndFlush(r2);

        // Query only for 2026-10-15
        mockMvc.perform(get("/api/admin/attendance")
                        .param("fromDate", "2026-10-15")
                        .param("toDate", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].workDate", is("2026-10-15")))
                .andExpect(jsonPath("$.content[0].status", is("CHECKED_OUT")))
                .andExpect(jsonPath("$.content[0].durationMinutes", is(480)))
                .andExpect(jsonPath("$.content[0].durationFormatted", is("8h 00m")))
                .andExpect(jsonPath("$.content[0].isIncomplete", is(false)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Employee history: searches by code and retains inactive staff in historical queries")
    void employeeHistory_searchesByCodeAndRetainsInactiveStaff() throws Exception {
        AttendanceRecord hist = new AttendanceRecord(inactiveEmployee, LocalDate.of(2026, 10, 10), Instant.parse("2026-10-10T03:30:00Z"));
        hist.setCheckOutTime(Instant.parse("2026-10-10T11:00:00Z"));
        hist.setStatus(AttendanceStatus.CHECKED_OUT);
        attendanceRepository.saveAndFlush(hist);

        mockMvc.perform(get("/api/admin/attendance")
                        .param("search", "Marie")
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].employeeCode", is("EMP-REP-02")))
                .andExpect(jsonPath("$.content[0].employeeName", is("Marie Curie")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Monthly report: returns aggregated PostgreSQL session totals and daily breakdown")
    void monthlyReport_returnsAggregatedTotals() throws Exception {
        // Shift 1: Completed
        AttendanceRecord r1 = new AttendanceRecord(employee1, LocalDate.of(2026, 10, 12), Instant.parse("2026-10-12T03:30:00Z"));
        r1.setCheckOutTime(Instant.parse("2026-10-12T11:30:00Z"));
        r1.setStatus(AttendanceStatus.CHECKED_OUT);
        attendanceRepository.saveAndFlush(r1);

        // Shift 2: Incomplete (open)
        AttendanceRecord r2 = new AttendanceRecord(employee1, LocalDate.of(2026, 10, 13), Instant.parse("2026-10-13T03:30:00Z"));
        r2.setStatus(AttendanceStatus.CHECKED_IN);
        attendanceRepository.saveAndFlush(r2);

        mockMvc.perform(get("/api/admin/reports/monthly")
                        .param("month", "10")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month", is(10)))
                .andExpect(jsonPath("$.year", is(2026)))
                .andExpect(jsonPath("$.totalRecordedSessions", is(2)))
                .andExpect(jsonPath("$.totalCompletedSessions", is(1)))
                .andExpect(jsonPath("$.totalIncompleteSessions", is(1)))
                .andExpect(jsonPath("$.employeeSummaries", hasSize(2))); // includes both employees
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("CSV export: generates RFC 4180 compliant CSV file with correct headers and data")
    void exportAttendanceCsv_returnsValidCsvFile() throws Exception {
        AttendanceRecord r1 = new AttendanceRecord(employee1, LocalDate.of(2026, 10, 15), Instant.parse("2026-10-15T03:30:00Z"));
        r1.setCheckOutTime(Instant.parse("2026-10-15T11:30:00Z"));
        r1.setStatus(AttendanceStatus.CHECKED_OUT);
        attendanceRepository.saveAndFlush(r1);

        MvcResult result = mockMvc.perform(get("/api/admin/reports/attendance/export")
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=")))
                .andExpect(header().string("X-Content-Type-Options", is("nosniff")))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes.length).isGreaterThan(10);
        String csvContent = new String(bytes);
        assertThat(csvContent).contains("Employee ID,Employee Name,Department,Work Date,Check-In Time (IST)");
        assertThat(csvContent).contains("EMP-REP-01");
        assertThat(csvContent).contains("Isaac Newton");
        assertThat(csvContent).contains("CHECKED_OUT");
        assertThat(csvContent).contains("8h 00m");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Monthly summary CSV export: generates valid monthly summary file")
    void exportMonthlySummaryCsv_returnsValidCsv() throws Exception {
        mockMvc.perform(get("/api/admin/reports/monthly/export")
                        .param("month", "10")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attendance-monthly-summary-2026-10.csv")));
    }

    @Test
    @DisplayName("Security: Anonymous request to report endpoints is rejected with 401")
    void security_anonymousRequest_rejectedWith401() throws Exception {
        mockMvc.perform(get("/api/admin/reports/monthly")
                        .param("month", "10")
                        .param("year", "2026"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/reports/attendance/export"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("Security: Non-admin employee role cannot access administrator report endpoints")
    void security_employeeRole_cannotAccessAdminReports() throws Exception {
        mockMvc.perform(get("/api/admin/reports/monthly")
                        .param("month", "10")
                        .param("year", "2026"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Validation: Invalid date range (fromDate > toDate) is rejected with 400 Bad Request")
    void validation_invalidDateRange_rejectedWith400() throws Exception {
        mockMvc.perform(get("/api/admin/attendance")
                        .param("fromDate", "2026-10-25")
                        .param("toDate", "2026-10-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("fromDate cannot be after toDate")));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("Security: Manager role cannot access administrator report or export endpoints")
    void security_managerRole_cannotAccessAdminReports() throws Exception {
        mockMvc.perform(get("/api/admin/reports/monthly")
                        .param("month", "10")
                        .param("year", "2026"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/reports/monthly/export")
                        .param("month", "10")
                        .param("year", "2026"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/reports/attendance/export"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("CSV Export: Protects against formula injection by neutralizing dangerous prefixes")
    void exportCsv_protectsAgainstFormulaInjection() throws Exception {
        String hashedPin = passwordEncoder.encode("1234");
        Employee injectionStaff = new Employee("EMP-INJ-01", "=cmd|' /C calc'!A0", "+Finance", hashedPin);
        injectionStaff = employeeRepository.saveAndFlush(injectionStaff);

        AttendanceRecord r = new AttendanceRecord(injectionStaff, LocalDate.of(2026, 10, 18), Instant.parse("2026-10-18T03:30:00Z"));
        r.setCheckOutTime(Instant.parse("2026-10-18T11:30:00Z"));
        r.setStatus(AttendanceStatus.CHECKED_OUT);
        attendanceRepository.saveAndFlush(r);

        MvcResult result = mockMvc.perform(get("/api/admin/reports/monthly/export")
                        .param("month", "10")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andReturn();

        String csv = new String(result.getResponse().getContentAsByteArray());
        // Cell should be prepended with single quote to prevent spreadsheet code execution
        assertThat(csv).contains("'=cmd|' /C calc'!A0");
        assertThat(csv).contains("'+Finance");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Validation: Invalid month or year query parameters are rejected with 400 Bad Request")
    void validation_invalidMonthOrYear_rejectedWith400() throws Exception {
        // Month 13
        mockMvc.perform(get("/api/admin/reports/monthly")
                        .param("month", "13")
                        .param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Month must be between 1 and 12")));

        // Month 0
        mockMvc.perform(get("/api/admin/reports/monthly")
                        .param("month", "0")
                        .param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Month must be between 1 and 12")));

        // Year 2019
        mockMvc.perform(get("/api/admin/reports/monthly")
                        .param("month", "10")
                        .param("year", "2019"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Year must be between 2020 and 2100")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Monthly report: Voided attendance records are excluded from session counts and marked voided")
    void monthlyReport_voidedRecords_excludedFromSessionCounts() throws Exception {
        AttendanceRecord voidedRecord = new AttendanceRecord(employee1, LocalDate.of(2026, 10, 20), Instant.parse("2026-10-20T03:30:00Z"));
        voidedRecord.setStatus(AttendanceStatus.VOIDED);
        attendanceRepository.saveAndFlush(voidedRecord);

        mockMvc.perform(get("/api/admin/reports/monthly")
                        .param("month", "10")
                        .param("year", "2026")
                        .param("search", "EMP-REP-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecordedSessions", is(0)))
                .andExpect(jsonPath("$.totalCompletedSessions", is(0)))
                .andExpect(jsonPath("$.totalIncompleteSessions", is(0)))
                .andExpect(jsonPath("$.employeeSummaries[0].dailyRecords[19].status", is("VOIDED")))
                .andExpect(jsonPath("$.employeeSummaries[0].dailyRecords[19].hasRecord", is(false)));
    }
}
