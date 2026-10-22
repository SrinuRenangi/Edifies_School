package com.edifies.attendance.modules.kiosk;

import com.edifies.attendance.common.exception.ErrorResponse;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.kiosk.dto.KioskAuthRequest;
import com.edifies.attendance.modules.kiosk.dto.KioskAuthResponse;
import com.edifies.attendance.modules.kiosk.dto.KioskResetRequest;
import com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository;
import com.edifies.attendance.modules.kiosk.service.KioskSessionService;
import com.edifies.attendance.modules.security.service.LoginAttemptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.TestPropertySource(properties = "app.kiosk.device-enforcement-enabled=false")
class KioskSecurityIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private KioskSessionService kioskSessionService;

    @Autowired
    private com.edifies.attendance.modules.attendance.repository.AttendanceRepository attendanceRepository;

    @Autowired
    private AttendanceCorrectionRepository attendanceCorrectionRepository;

    private String kioskUrl;
    private String adminEmployeesUrl;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        kioskUrl = "http://localhost:" + port + "/api/kiosk";
        adminEmployeesUrl = "http://localhost:" + port + "/api/admin/employees";

        attendanceCorrectionRepository.deleteAll();
        attendanceRepository.deleteAll();
        employeeRepository.deleteAll();
        loginAttemptService.resetLockout("EMP-KIOSK-01");
        loginAttemptService.resetLockout("EMP-KIOSK-INACTIVE");
        loginAttemptService.resetLockout("NONEXISTENT");

        // Seed active employee
        Employee activeEmp = new Employee("EMP-KIOSK-01", "Ada Lovelace", "Mathematics", passwordEncoder.encode("4321"));
        activeEmp.setStatus(EmployeeStatus.ACTIVE);
        employeeRepository.save(activeEmp);

        // Seed inactive employee
        Employee inactiveEmp = new Employee("EMP-KIOSK-INACTIVE", "Charles Babbage", "History", passwordEncoder.encode("4321"));
        inactiveEmp.setStatus(EmployeeStatus.INACTIVE);
        employeeRepository.save(inactiveEmp);
    }

    @AfterEach
    void tearDown() {
        attendanceCorrectionRepository.deleteAll();
        attendanceRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    @DisplayName("Kiosk Auth: Active employee with correct PIN authenticates successfully")
    void shouldAuthenticateActiveEmployeeWithCorrectPin() {
        KioskAuthRequest req = new KioskAuthRequest("EMP-KIOSK-01", "4321");
        ResponseEntity<KioskAuthResponse> response = restTemplate.postForEntity(
                kioskUrl + "/authenticate", req, KioskAuthResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        KioskAuthResponse body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.token());
        assertEquals("EMP-KIOSK-01", body.employeeId());
        assertEquals("Ada Lovelace", body.fullName());
        assertEquals("Mathematics", body.department());
        assertEquals(60, body.expiresInSeconds());

        // Verify session was registered in kioskSessionService
        assertNotNull(kioskSessionService.validateSession(body.token()));
    }

    @Test
    @DisplayName("Kiosk Auth: Incorrect PIN fails with generic 401 error")
    void shouldRejectIncorrectPinWithGenericError() {
        KioskAuthRequest req = new KioskAuthRequest("EMP-KIOSK-01", "9999");
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                kioskUrl + "/authenticate", req, ErrorResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid Employee ID or PIN.", response.getBody().message());
    }

    @Test
    @DisplayName("Kiosk Auth: Unknown Employee ID fails with generic 401 error (no enumeration)")
    void shouldRejectUnknownEmployeeIdWithGenericError() {
        KioskAuthRequest req = new KioskAuthRequest("NONEXISTENT", "4321");
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                kioskUrl + "/authenticate", req, ErrorResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid Employee ID or PIN.", response.getBody().message());
    }

    @Test
    @DisplayName("Kiosk Auth: Inactive employee cannot authenticate (fails with generic 401)")
    void shouldRejectInactiveEmployee() {
        KioskAuthRequest req = new KioskAuthRequest("EMP-KIOSK-INACTIVE", "4321");
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                kioskUrl + "/authenticate", req, ErrorResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid Employee ID or PIN.", response.getBody().message());
    }

    @Test
    @DisplayName("Kiosk Auth: 5 consecutive failed attempts trigger lockout")
    void shouldLockoutAfterFiveFailedAttempts() {
        KioskAuthRequest badReq = new KioskAuthRequest("EMP-KIOSK-01", "0000");

        for (int i = 0; i < 5; i++) {
            restTemplate.postForEntity(kioskUrl + "/authenticate", badReq, String.class);
        }

        // 6th attempt must be locked out
        ResponseEntity<ErrorResponse> lockedRes = restTemplate.postForEntity(
                kioskUrl + "/authenticate", badReq, ErrorResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, lockedRes.getStatusCode());
        assertTrue(lockedRes.getBody().message().contains("Account temporarily locked"));

        // Administrative reset clears lockout
        loginAttemptService.resetLockout("EMP-KIOSK-01");
        assertFalse(loginAttemptService.isBlocked("EMP-KIOSK-01"));
    }

    @Test
    @DisplayName("Kiosk Reset: Invalidate session immediately")
    void shouldInvalidateKioskSessionOnReset() {
        KioskAuthRequest req = new KioskAuthRequest("EMP-KIOSK-01", "4321");
        ResponseEntity<KioskAuthResponse> authRes = restTemplate.postForEntity(
                kioskUrl + "/authenticate", req, KioskAuthResponse.class);
        assertEquals(HttpStatus.OK, authRes.getStatusCode());
        String token = authRes.getBody().token();

        // Validate session exists
        assertNotNull(kioskSessionService.validateSession(token));

        // Call reset endpoint
        KioskResetRequest resetReq = new KioskResetRequest(token);
        ResponseEntity<Map> resetRes = restTemplate.postForEntity(
                kioskUrl + "/reset", resetReq, Map.class);
        assertEquals(HttpStatus.OK, resetRes.getStatusCode());

        // Validate session is now gone
        assertNull(kioskSessionService.validateSession(token));
    }

    @Test
    @DisplayName("Kiosk Boundary: Kiosk token cannot access administrative APIs")
    void shouldRejectKioskTokenOnAdminApis() {
        KioskAuthRequest req = new KioskAuthRequest("EMP-KIOSK-01", "4321");
        ResponseEntity<KioskAuthResponse> authRes = restTemplate.postForEntity(
                kioskUrl + "/authenticate", req, KioskAuthResponse.class);
        String token = authRes.getBody().token();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Kiosk-Token", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> adminRes = restTemplate.exchange(
                adminEmployeesUrl, HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, adminRes.getStatusCode(),
                "Kiosk token must NEVER grant access to /api/admin/**");
    }
}
