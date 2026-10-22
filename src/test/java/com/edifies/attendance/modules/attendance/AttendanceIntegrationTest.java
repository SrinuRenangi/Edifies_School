package com.edifies.attendance.modules.attendance;

import com.edifies.attendance.modules.attendance.dto.AttendanceResponse;
import com.edifies.attendance.modules.attendance.dto.AttendanceStatusResponse;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.kiosk.dto.KioskAuthRequest;
import com.edifies.attendance.modules.kiosk.dto.KioskAuthResponse;
import com.edifies.attendance.modules.security.dto.AdminLoginRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.TestPropertySource(properties = "app.kiosk.device-enforcement-enabled=false")
class AttendanceIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AttendanceCorrectionRepository attendanceCorrectionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String baseUrl;
    private Employee testEmployee;
    private final String rawPin = "1122";

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;

        // Clean previous test attendance
        attendanceCorrectionRepository.deleteAll();
        attendanceRepository.deleteAll();

        // Create or get active test employee
        String empCode = "EMP-ATT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Employee emp = new Employee(empCode, "Katherine Johnson", "Trajectory Computing", passwordEncoder.encode(rawPin));
        emp.setStatus(EmployeeStatus.ACTIVE);
        testEmployee = employeeRepository.save(emp);
    }

    @AfterEach
    void tearDown() {
        attendanceCorrectionRepository.deleteAll();
        attendanceRepository.deleteAll();
    }

    private String authenticateKiosk() {
        KioskAuthRequest req = new KioskAuthRequest(testEmployee.getEmployeeId(), rawPin);
        ResponseEntity<KioskAuthResponse> res = restTemplate.postForEntity(
                baseUrl + "/api/kiosk/authenticate", req, KioskAuthResponse.class);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        return res.getBody().token();
    }

    private String authenticateAdminSession() {
        AdminLoginRequest req = new AdminLoginRequest("admin", "Admin@12345");
        ResponseEntity<Map> res = restTemplate.postForEntity(
                baseUrl + "/api/auth/admin/login", req, Map.class);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        List<String> cookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        assertFalse(cookies.isEmpty());
        return cookies.get(0);
    }

    @Test
    @DisplayName("End-to-End: Full attendance cycle (Status -> Check-In -> Status -> Check-Out -> Status)")
    void shouldCompleteFullAttendanceCycle() {
        String token = authenticateKiosk();

        // 1. Initial Status: NOT_CHECKED_IN
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Kiosk-Session", token);
        HttpEntity<Void> statusReq = new HttpEntity<>(headers);

        ResponseEntity<AttendanceStatusResponse> statusRes1 = restTemplate.exchange(
                baseUrl + "/api/kiosk/attendance/status", HttpMethod.GET, statusReq, AttendanceStatusResponse.class);
        assertEquals(HttpStatus.OK, statusRes1.getStatusCode());
        assertEquals("NOT_CHECKED_IN", statusRes1.getBody().currentStatus());
        assertEquals("CHECK_IN", statusRes1.getBody().allowedAction());

        // 2. Check-In
        ResponseEntity<AttendanceResponse> checkInRes = restTemplate.exchange(
                baseUrl + "/api/kiosk/attendance/check-in", HttpMethod.POST, statusReq, AttendanceResponse.class);
        assertEquals(HttpStatus.OK, checkInRes.getStatusCode());
        assertNotNull(checkInRes.getBody());
        assertEquals(AttendanceStatus.CHECKED_IN, checkInRes.getBody().status());
        assertEquals(testEmployee.getEmployeeId(), checkInRes.getBody().employeeCode());
        assertNotNull(checkInRes.getBody().checkInTime());

        // 3. Status after Check-In: CHECKED_IN
        ResponseEntity<AttendanceStatusResponse> statusRes2 = restTemplate.exchange(
                baseUrl + "/api/kiosk/attendance/status", HttpMethod.GET, statusReq, AttendanceStatusResponse.class);
        assertEquals(HttpStatus.OK, statusRes2.getStatusCode());
        assertEquals("CHECKED_IN", statusRes2.getBody().currentStatus());
        assertEquals("CHECK_OUT", statusRes2.getBody().allowedAction());

        // 4. Duplicate Check-In rejected
        ResponseEntity<Map> duplicateCheckIn = restTemplate.exchange(
                baseUrl + "/api/kiosk/attendance/check-in", HttpMethod.POST, statusReq, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, duplicateCheckIn.getStatusCode());

        // 5. Check-Out
        ResponseEntity<AttendanceResponse> checkOutRes = restTemplate.exchange(
                baseUrl + "/api/kiosk/attendance/check-out", HttpMethod.POST, statusReq, AttendanceResponse.class);
        assertEquals(HttpStatus.OK, checkOutRes.getStatusCode());
        assertNotNull(checkOutRes.getBody());
        assertEquals(AttendanceStatus.CHECKED_OUT, checkOutRes.getBody().status());
        assertNotNull(checkOutRes.getBody().checkOutTime());

        // 6. Status after Check-Out: CHECKED_OUT and NONE action
        ResponseEntity<AttendanceStatusResponse> statusRes3 = restTemplate.exchange(
                baseUrl + "/api/kiosk/attendance/status", HttpMethod.GET, statusReq, AttendanceStatusResponse.class);
        assertEquals(HttpStatus.OK, statusRes3.getStatusCode());
        assertEquals("CHECKED_OUT", statusRes3.getBody().currentStatus());
        assertEquals("NONE", statusRes3.getBody().allowedAction());

        // 7. Duplicate Check-Out rejected
        ResponseEntity<Map> duplicateCheckOut = restTemplate.exchange(
                baseUrl + "/api/kiosk/attendance/check-out", HttpMethod.POST, statusReq, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, duplicateCheckOut.getStatusCode());
    }

    @Test
    @DisplayName("Database Invariant: Partial Unique Index uk_single_open_session prevents two open sessions")
    void shouldEnforceSingleOpenSessionAtDatabaseLevel() {
        AttendanceRecord session1 = new AttendanceRecord(testEmployee, LocalDate.of(2026, 1, 1), Instant.now());
        session1.setStatus(AttendanceStatus.CHECKED_IN);
        attendanceRepository.saveAndFlush(session1);

        // Attempting to insert a second open session for the same employee must violate uk_single_open_session
        AttendanceRecord session2 = new AttendanceRecord(testEmployee, LocalDate.of(2026, 1, 2), Instant.now());
        session2.setStatus(AttendanceStatus.CHECKED_IN);

        assertThrows(DataIntegrityViolationException.class, () -> {
            attendanceRepository.saveAndFlush(session2);
        });
    }

    @Test
    @DisplayName("Database Invariant: Unique Index uk_employee_work_date prevents duplicate daily records")
    void shouldEnforceSingleRecordPerWorkDate() {
        LocalDate workDate = LocalDate.of(2026, 2, 1);
        AttendanceRecord r1 = new AttendanceRecord(testEmployee, workDate, Instant.now());
        r1.setStatus(AttendanceStatus.CHECKED_OUT);
        r1.setCheckOutTime(Instant.now());
        attendanceRepository.saveAndFlush(r1);

        // Second record for the same employee and same work date must fail
        AttendanceRecord r2 = new AttendanceRecord(testEmployee, workDate, Instant.now());
        r2.setStatus(AttendanceStatus.CHECKED_OUT);
        r2.setCheckOutTime(Instant.now());

        assertThrows(DataIntegrityViolationException.class, () -> {
            attendanceRepository.saveAndFlush(r2);
        });
    }

    @Test
    @DisplayName("Deactivation Invariant: Deactivating employee preserves historical attendance records")
    void shouldPreserveAttendanceHistoryWhenEmployeeIsDeactivated() {
        // Record attendance
        AttendanceRecord record = new AttendanceRecord(testEmployee, LocalDate.of(2026, 3, 1), Instant.now());
        record.setStatus(AttendanceStatus.CHECKED_OUT);
        record.setCheckOutTime(Instant.now());
        attendanceRepository.saveAndFlush(record);

        // Deactivate employee
        testEmployee.setStatus(EmployeeStatus.INACTIVE);
        employeeRepository.saveAndFlush(testEmployee);

        // Attendance history must remain intact
        List<AttendanceRecord> history = attendanceRepository.findAllByEmployeeId(testEmployee.getId());
        assertEquals(1, history.size());
        assertEquals(LocalDate.of(2026, 3, 1), history.get(0).getWorkDate());
    }

    @Test
    @DisplayName("Idempotency: Re-submitting check-in with same Idempotency-Key returns original result without duplicate record")
    void shouldHandleIdempotencyKeys() {
        String token = authenticateKiosk();
        String idempotencyKey = UUID.randomUUID().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Kiosk-Session", token);
        headers.set("Idempotency-Key", idempotencyKey);
        HttpEntity<Void> req = new HttpEntity<>(headers);

        // First call
        ResponseEntity<AttendanceResponse> firstCall = restTemplate.exchange(
                baseUrl + "/api/kiosk/attendance/check-in", HttpMethod.POST, req, AttendanceResponse.class);
        assertEquals(HttpStatus.OK, firstCall.getStatusCode());
        UUID recordId = firstCall.getBody().id();

        // Second call with same idempotency key
        ResponseEntity<AttendanceResponse> secondCall = restTemplate.exchange(
                baseUrl + "/api/kiosk/attendance/check-in", HttpMethod.POST, req, AttendanceResponse.class);
        assertEquals(HttpStatus.OK, secondCall.getStatusCode());
        assertEquals(recordId, secondCall.getBody().id());

        // Verify only 1 record exists in database
        List<AttendanceRecord> records = attendanceRepository.findAllByEmployeeId(testEmployee.getId());
        assertEquals(1, records.size());
    }

    @Test
    @DisplayName("Security: Admin attendance history requires ROLE_ADMIN and returns paginated data")
    void shouldEnforceAdminSecurityOnAttendanceHistory() {
        // 1. Unauthenticated request to /api/admin/attendance is rejected with 401
        ResponseEntity<Map> unauthorizedRes = restTemplate.getForEntity(
                baseUrl + "/api/admin/attendance", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorizedRes.getStatusCode());

        // 2. Authenticated Admin session accesses /api/admin/attendance successfully
        String adminCookie = authenticateAdminSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, adminCookie);
        HttpEntity<Void> adminReq = new HttpEntity<>(headers);

        ResponseEntity<Map> authorizedRes = restTemplate.exchange(
                baseUrl + "/api/admin/attendance?page=0&size=10", HttpMethod.GET, adminReq, Map.class);
        assertEquals(HttpStatus.OK, authorizedRes.getStatusCode());
        assertNotNull(authorizedRes.getBody().get("content"));
    }
}
