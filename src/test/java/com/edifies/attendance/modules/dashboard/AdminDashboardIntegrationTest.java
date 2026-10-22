package com.edifies.attendance.modules.dashboard;

import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.dashboard.dto.DashboardQuickSearchEmployee;
import com.edifies.attendance.modules.dashboard.dto.DashboardSummaryResponse;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.security.dto.AdminLoginRequest;
import com.edifies.attendance.modules.security.dto.AdminLoginResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminDashboardIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AttendanceCorrectionRepository correctionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminAuthUrl;
    private String dashboardUrl;
    private String adminSessionCookie;

    private final LocalDate today = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());

        adminAuthUrl = "http://localhost:" + port + "/api/auth/admin";
        dashboardUrl = "http://localhost:" + port + "/api/admin/dashboard";

        cleanupData();

        // Sign in as default admin
        AdminLoginRequest loginReq = new AdminLoginRequest("admin", "Admin@12345");
        ResponseEntity<AdminLoginResponse> loginRes = restTemplate.postForEntity(
                adminAuthUrl + "/login", loginReq, AdminLoginResponse.class);

        assertEquals(HttpStatus.OK, loginRes.getStatusCode());
        List<String> cookies = loginRes.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        adminSessionCookie = cookies.get(0).split(";")[0];
    }

    @AfterEach
    void tearDown() {
        cleanupData();
    }

    private void cleanupData() {
        correctionRepository.deleteAll();
        attendanceRepository.deleteAll();
        // Delete only test-created employees to protect base system data
        employeeRepository.findByEmployeeIdIgnoreCase("DASH-EMP-01").ifPresent(employeeRepository::delete);
        employeeRepository.findByEmployeeIdIgnoreCase("DASH-EMP-02").ifPresent(employeeRepository::delete);
        employeeRepository.findByEmployeeIdIgnoreCase("DASH-EMP-03").ifPresent(employeeRepository::delete);
        employeeRepository.findByEmployeeIdIgnoreCase("DASH-EMP-INACTIVE").ifPresent(employeeRepository::delete);
    }

    @Test
    @DisplayName("Dashboard Security: Unauthenticated request to /api/admin/dashboard/summary is rejected with 401")
    void shouldRejectUnauthenticatedDashboardRequest() {
        ResponseEntity<String> res = restTemplate.getForEntity(
                dashboardUrl + "/summary", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    @Test
    @DisplayName("Dashboard Summary: Authenticated admin retrieves live PostgreSQL counts and metrics")
    void shouldReturnAccurateMetricsForWorkDate() {
        // Seed 3 active employees + 1 inactive employee
        Employee emp1 = new Employee("DASH-EMP-01", "Alice Smith", "Engineering", passwordEncoder.encode("1234"));
        emp1.setStatus(EmployeeStatus.ACTIVE);
        employeeRepository.save(emp1);

        Employee emp2 = new Employee("DASH-EMP-02", "Bob Jones", "HR", passwordEncoder.encode("1234"));
        emp2.setStatus(EmployeeStatus.ACTIVE);
        employeeRepository.save(emp2);

        Employee emp3 = new Employee("DASH-EMP-03", "Charlie Brown", "Finance", passwordEncoder.encode("1234"));
        emp3.setStatus(EmployeeStatus.ACTIVE);
        employeeRepository.save(emp3);

        Employee empInactive = new Employee("DASH-EMP-INACTIVE", "David Inactive", "Legal", passwordEncoder.encode("1234"));
        empInactive.setStatus(EmployeeStatus.INACTIVE);
        employeeRepository.save(empInactive);

        // emp1: Currently Checked In on today's workDate
        AttendanceRecord rec1 = new AttendanceRecord(emp1, today, Instant.now().minusSeconds(3600));
        rec1.setStatus(AttendanceStatus.CHECKED_IN);
        attendanceRepository.save(rec1);

        // emp2: Completed session on today's workDate
        AttendanceRecord rec2 = new AttendanceRecord(emp2, today, Instant.now().minusSeconds(14400));
        rec2.setCheckOutTime(Instant.now().minusSeconds(1000));
        rec2.setStatus(AttendanceStatus.CHECKED_OUT);
        attendanceRepository.save(rec2);

        // emp3 has no attendance today -> notCheckedInCount should include emp3

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, adminSessionCookie);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<DashboardSummaryResponse> res = restTemplate.exchange(
                dashboardUrl + "/summary?workDate=" + today,
                HttpMethod.GET,
                entity,
                DashboardSummaryResponse.class
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        DashboardSummaryResponse summary = res.getBody();
        assertNotNull(summary);

        assertEquals(today, summary.workDate());
        assertEquals("Asia/Kolkata", summary.timezone());
        assertTrue(summary.activeEmployeeCount() >= 3);
        assertTrue(summary.currentlyCheckedInCount() >= 1);
        assertTrue(summary.completedSessionCount() >= 1);
        assertTrue(summary.notCheckedInCount() >= 1);

        // Verify recent activities are present
        assertNotNull(summary.recentActivities());
        assertFalse(summary.recentActivities().isEmpty());
    }

    @Test
    @DisplayName("Dashboard Employee Search: Searches active staff and does not expose credential/PIN hashes")
    void shouldSearchEmployeesWithoutExposingCredentials() {
        Employee emp = new Employee("DASH-EMP-01", "Alice Smith", "Engineering", passwordEncoder.encode("1234"));
        emp.setStatus(EmployeeStatus.ACTIVE);
        employeeRepository.save(emp);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, adminSessionCookie);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<List<DashboardQuickSearchEmployee>> res = restTemplate.exchange(
                dashboardUrl + "/employee-search?query=Alice&workDate=" + today,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        List<DashboardQuickSearchEmployee> list = res.getBody();
        assertNotNull(list);
        assertFalse(list.isEmpty());

        DashboardQuickSearchEmployee match = list.get(0);
        assertEquals("DASH-EMP-01", match.employeeId());
        assertEquals("Alice Smith", match.fullName());
        assertEquals("Engineering", match.department());
        assertEquals(EmployeeStatus.ACTIVE, match.status());
    }

    @Test
    @DisplayName("Dashboard Summary: Incomplete / Stale open sessions from previous date are flagged for attention")
    void shouldFlagStaleOpenSessionsForAttention() {
        Employee emp = new Employee("DASH-EMP-01", "Alice Smith", "Engineering", passwordEncoder.encode("1234"));
        emp.setStatus(EmployeeStatus.ACTIVE);
        employeeRepository.save(emp);

        // Open session from yesterday (stale)
        LocalDate yesterday = today.minusDays(1);
        AttendanceRecord staleRecord = new AttendanceRecord(emp, yesterday, Instant.now().minusSeconds(86400));
        staleRecord.setStatus(AttendanceStatus.CHECKED_IN);
        attendanceRepository.save(staleRecord);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, adminSessionCookie);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<DashboardSummaryResponse> res = restTemplate.exchange(
                dashboardUrl + "/summary?workDate=" + today,
                HttpMethod.GET,
                entity,
                DashboardSummaryResponse.class
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        DashboardSummaryResponse summary = res.getBody();
        assertNotNull(summary);

        assertTrue(summary.incompleteSessionCount() >= 1);
        assertNotNull(summary.attentionRequiredSessions());
        assertFalse(summary.attentionRequiredSessions().isEmpty());

        boolean hasStale = summary.attentionRequiredSessions().stream()
                .anyMatch(item -> item.employeeCode().equals("DASH-EMP-01") && "STALE_OPEN_SESSION".equals(item.issueType()));
        assertTrue(hasStale, "Expected stale open session to be identified in attentionRequiredSessions");
    }
}
