package com.edifies.attendance.modules.security;

import com.edifies.attendance.common.exception.ErrorResponse;
import com.edifies.attendance.modules.employee.dto.CreateEmployeeRequest;
import com.edifies.attendance.modules.employee.dto.EmployeeResponse;
import com.edifies.attendance.modules.employee.dto.ResetPinRequest;
import com.edifies.attendance.modules.security.dto.AdminLoginRequest;
import com.edifies.attendance.modules.security.dto.AdminLoginResponse;
import com.edifies.attendance.modules.security.dto.AdminProfileResponse;
import com.edifies.attendance.modules.security.model.AdminUser;
import com.edifies.attendance.modules.security.repository.AdminUserRepository;
import com.edifies.attendance.modules.security.service.LoginAttemptService;
import com.edifies.attendance.modules.security.service.ManagerAccountInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoleBasedAccessControlIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private ManagerAccountInitializer managerAccountInitializer;

    private String baseUrl;
    private String authUrl;
    private String employeesUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        authUrl = baseUrl + "/api/auth/admin";
        employeesUrl = baseUrl + "/api/admin/employees";

        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());

        loginAttemptService.resetLockout("admin");
        loginAttemptService.resetLockout("manager");

        // Ensure default manager account exists
        managerAccountInitializer.run();
    }

    private HttpHeaders extractSessionHeaders(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        HttpHeaders headers = new HttpHeaders();
        if (setCookies != null) {
            StringBuilder sb = new StringBuilder();
            String xsrfToken = null;
            for (String c : setCookies) {
                String[] parts = c.split(";");
                if (parts.length > 0) {
                    String cookiePair = parts[0].trim();
                    if (!sb.isEmpty()) sb.append("; ");
                    sb.append(cookiePair);
                    if (cookiePair.startsWith("XSRF-TOKEN=")) {
                        xsrfToken = cookiePair.substring("XSRF-TOKEN=".length());
                    }
                }
            }
            headers.add(HttpHeaders.COOKIE, sb.toString());
            if (xsrfToken != null) {
                headers.add("X-XSRF-TOKEN", xsrfToken);
            }
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders loginAs(String username, String password) {
        AdminLoginRequest request = new AdminLoginRequest(username, password);
        ResponseEntity<AdminLoginResponse> response = restTemplate.postForEntity(
                authUrl + "/login",
                request,
                AdminLoginResponse.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Login should succeed for user: " + username);
        assertNotNull(response.getBody());
        return extractSessionHeaders(response);
    }

    // ========================================================
    // 1. ADMIN TESTS: Full Administrative Access
    // ========================================================

    @Test
    @DisplayName("Admin: Valid credentials authenticate and return ROLE_ADMIN")
    void testAdminLoginSuccess() {
        AdminLoginRequest request = new AdminLoginRequest("admin", "Admin@12345");
        ResponseEntity<AdminLoginResponse> response = restTemplate.postForEntity(
                authUrl + "/login",
                request,
                AdminLoginResponse.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        AdminLoginResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("admin", body.username());
        assertEquals("ROLE_ADMIN", body.role());
    }

    @Test
    @DisplayName("Admin: Can access staff directory and administrative modules")
    void testAdminAccessToAllModules() {
        HttpHeaders adminHeaders = loginAs("admin", "Admin@12345");

        // 1. Employee Directory
        ResponseEntity<String> empResp = restTemplate.exchange(
                employeesUrl,
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, empResp.getStatusCode());

        // 2. Dashboard Summary
        ResponseEntity<String> dashResp = restTemplate.exchange(
                baseUrl + "/api/admin/dashboard/summary",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, dashResp.getStatusCode());

        // 3. Attendance Log
        ResponseEntity<String> attResp = restTemplate.exchange(
                baseUrl + "/api/admin/attendance?page=0&size=5",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, attResp.getStatusCode());

        // 4. Reports
        ResponseEntity<String> repResp = restTemplate.exchange(
                baseUrl + "/api/admin/reports/monthly?month=10&year=2026",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, repResp.getStatusCode());

        // 5. Storage Health
        ResponseEntity<String> storResp = restTemplate.exchange(
                baseUrl + "/api/admin/storage/health",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, storResp.getStatusCode());

        // 6. Devices
        ResponseEntity<String> devResp = restTemplate.exchange(
                baseUrl + "/api/admin/devices",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, devResp.getStatusCode());

        // 7. Backup Status
        ResponseEntity<String> backupResp = restTemplate.exchange(
                baseUrl + "/api/admin/backup/status",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, backupResp.getStatusCode());
    }

    @Test
    @DisplayName("Admin: Authorized to reset employee PIN")
    void testAdminCanResetEmployeePin() {
        HttpHeaders adminHeaders = loginAs("admin", "Admin@12345");

        // Create an employee first to guarantee an entity exists
        String empCode = "PIN" + (System.currentTimeMillis() % 100000);
        CreateEmployeeRequest createReq = new CreateEmployeeRequest(
                empCode,
                "PIN Reset Test Staff",
                "Operations",
                "1234"
        );
        ResponseEntity<EmployeeResponse> createResp = restTemplate.exchange(
                employeesUrl,
                HttpMethod.POST,
                new HttpEntity<>(createReq, adminHeaders),
                EmployeeResponse.class
        );
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        assertNotNull(createResp.getBody());
        UUID empId = createResp.getBody().id();

        // Admin resets PIN
        ResetPinRequest pinReq = new ResetPinRequest("654321");
        ResponseEntity<Void> resetResp = restTemplate.exchange(
                employeesUrl + "/" + empId + "/reset-pin",
                HttpMethod.POST,
                new HttpEntity<>(pinReq, adminHeaders),
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, resetResp.getStatusCode());
    }

    // ========================================================
    // 2. MANAGER TESTS: Restricted Access
    // ========================================================

    @Test
    @DisplayName("Manager: Valid credentials authenticate and return ROLE_MANAGER")
    void testManagerLoginSuccess() {
        AdminLoginRequest request = new AdminLoginRequest("manager", "Manager@12345");
        ResponseEntity<AdminLoginResponse> response = restTemplate.postForEntity(
                authUrl + "/login",
                request,
                AdminLoginResponse.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        AdminLoginResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("manager", body.username());
        assertEquals("ROLE_MANAGER", body.role());
    }

    @Test
    @DisplayName("Manager: Profile endpoint returns ROLE_MANAGER")
    void testManagerProfileEndpoint() {
        HttpHeaders managerHeaders = loginAs("manager", "Manager@12345");

        ResponseEntity<AdminProfileResponse> response = restTemplate.exchange(
                authUrl + "/me",
                HttpMethod.GET,
                new HttpEntity<>(managerHeaders),
                AdminProfileResponse.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ROLE_MANAGER", response.getBody().role());
    }

    @Test
    @DisplayName("Manager: Authorized to view staff directory and create employees")
    void testManagerCanAccessStaffDirectory() {
        HttpHeaders managerHeaders = loginAs("manager", "Manager@12345");

        // 1. List employees
        ResponseEntity<String> listResp = restTemplate.exchange(
                employeesUrl,
                HttpMethod.GET,
                new HttpEntity<>(managerHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, listResp.getStatusCode(), "Manager must have access to employee directory");

        // 2. Create employee
        String randomCode = "MGR" + System.currentTimeMillis() % 10000;
        CreateEmployeeRequest createReq = new CreateEmployeeRequest(
                randomCode,
                "Manager Registered Staff",
                "Operations",
                "5555"
        );
        ResponseEntity<EmployeeResponse> createResp = restTemplate.exchange(
                employeesUrl,
                HttpMethod.POST,
                new HttpEntity<>(createReq, managerHeaders),
                EmployeeResponse.class
        );
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        assertNotNull(createResp.getBody());
        assertEquals(randomCode, createResp.getBody().employeeId());
    }

    @Test
    @DisplayName("Manager: Forbidden (403) from resetting employee PIN")
    void testManagerCannotResetEmployeePin() {
        HttpHeaders managerHeaders = loginAs("manager", "Manager@12345");
        HttpHeaders adminHeaders = loginAs("admin", "Admin@12345");

        // Find an employee using admin
        ResponseEntity<EmployeeResponse[]> empsResp = restTemplate.exchange(
                employeesUrl,
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                EmployeeResponse[].class
        );
        assertNotNull(empsResp.getBody());
        UUID empId = empsResp.getBody()[0].id();

        // Manager attempts to reset PIN -> must receive 403 Forbidden
        ResetPinRequest pinReq = new ResetPinRequest("987654");
        ResponseEntity<String> resetResp = restTemplate.exchange(
                employeesUrl + "/" + empId + "/reset-pin",
                HttpMethod.POST,
                new HttpEntity<>(pinReq, managerHeaders),
                String.class
        );
        assertEquals(HttpStatus.FORBIDDEN, resetResp.getStatusCode(), "Manager must be forbidden from resetting employee PIN");
    }

    @Test
    @DisplayName("Manager: Forbidden (403) from administrative modules (Dashboard, Attendance, Reports, Storage, Devices, Backup)")
    void testManagerForbiddenFromAdminModules() {
        HttpHeaders managerHeaders = loginAs("manager", "Manager@12345");

        // 1. Dashboard
        ResponseEntity<String> dashResp = restTemplate.exchange(
                baseUrl + "/api/admin/dashboard/summary",
                HttpMethod.GET,
                new HttpEntity<>(managerHeaders),
                String.class
        );
        assertEquals(HttpStatus.FORBIDDEN, dashResp.getStatusCode(), "Manager must not access Dashboard API");

        // 2. Attendance History Log
        ResponseEntity<String> attResp = restTemplate.exchange(
                baseUrl + "/api/admin/attendance",
                HttpMethod.GET,
                new HttpEntity<>(managerHeaders),
                String.class
        );
        assertEquals(HttpStatus.FORBIDDEN, attResp.getStatusCode(), "Manager must not access Attendance History API");

        // 3. Reports
        ResponseEntity<String> repResp = restTemplate.exchange(
                baseUrl + "/api/admin/reports/monthly",
                HttpMethod.GET,
                new HttpEntity<>(managerHeaders),
                String.class
        );
        assertEquals(HttpStatus.FORBIDDEN, repResp.getStatusCode(), "Manager must not access Reports API");

        // 4. Storage & Photo Retention
        ResponseEntity<String> storResp = restTemplate.exchange(
                baseUrl + "/api/admin/storage/health",
                HttpMethod.GET,
                new HttpEntity<>(managerHeaders),
                String.class
        );
        assertEquals(HttpStatus.FORBIDDEN, storResp.getStatusCode(), "Manager must not access Storage API");

        // 5. Authorized Devices
        ResponseEntity<String> devResp = restTemplate.exchange(
                baseUrl + "/api/admin/devices",
                HttpMethod.GET,
                new HttpEntity<>(managerHeaders),
                String.class
        );
        assertEquals(HttpStatus.FORBIDDEN, devResp.getStatusCode(), "Manager must not access Devices API");

        // 6. Disaster Recovery & Backups
        ResponseEntity<String> backupResp = restTemplate.exchange(
                baseUrl + "/api/admin/backup/status",
                HttpMethod.GET,
                new HttpEntity<>(managerHeaders),
                String.class
        );
        assertEquals(HttpStatus.FORBIDDEN, backupResp.getStatusCode(), "Manager must not access Backup API");

        // 7. Trigger Backup
        ResponseEntity<String> triggerResp = restTemplate.exchange(
                baseUrl + "/api/admin/backup/trigger",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("type", "MANUAL"), managerHeaders),
                String.class
        );
        assertEquals(HttpStatus.FORBIDDEN, triggerResp.getStatusCode(), "Manager must not trigger backups");
    }

    // ========================================================
    // 3. SECURITY, SESSION & REGRESSION TESTS
    // ========================================================

    @Test
    @DisplayName("Security: Anonymous requests to protected APIs are denied (401)")
    void testAnonymousAccessDenied() {
        ResponseEntity<String> resp = restTemplate.getForEntity(employeesUrl, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    @DisplayName("Security: Invalid credentials are rejected (401)")
    void testInvalidCredentialsRejected() {
        AdminLoginRequest request = new AdminLoginRequest("manager", "WrongPassword@999");
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                authUrl + "/login",
                request,
                ErrorResponse.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Security: Logout invalidates session immediately")
    void testManagerLogout() {
        HttpHeaders managerHeaders = loginAs("manager", "Manager@12345");

        // Perform logout
        ResponseEntity<String> logoutResp = restTemplate.exchange(
                authUrl + "/logout",
                HttpMethod.POST,
                new HttpEntity<>(managerHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, logoutResp.getStatusCode());

        // Subsequent call with same session headers must fail (401)
        ResponseEntity<String> afterLogoutResp = restTemplate.exchange(
                employeesUrl,
                HttpMethod.GET,
                new HttpEntity<>(managerHeaders),
                String.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, afterLogoutResp.getStatusCode());
    }

    @Test
    @DisplayName("Provisioning: Manager initialization is idempotent")
    void testManagerAccountInitializerIdempotent() {
        long initialCount = adminUserRepository.count();
        // Run initializer again
        managerAccountInitializer.run();
        managerAccountInitializer.run();
        long postCount = adminUserRepository.count();
        assertEquals(initialCount, postCount, "Repeated initializer executions must not duplicate accounts");
    }
}
