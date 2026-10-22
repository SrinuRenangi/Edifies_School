package com.edifies.attendance.modules.employee;

import com.edifies.attendance.common.exception.ErrorResponse;
import com.edifies.attendance.modules.employee.dto.CreateEmployeeRequest;
import com.edifies.attendance.modules.employee.dto.EmployeeResponse;
import com.edifies.attendance.modules.employee.dto.ResetPinRequest;
import com.edifies.attendance.modules.employee.dto.UpdateEmployeeRequest;
import com.edifies.attendance.modules.employee.dto.UpdateStatusRequest;
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
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmployeeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.edifies.attendance.modules.attendance.repository.AttendanceRepository attendanceRepository;

    @Autowired
    private com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository attendanceCorrectionRepository;

    private String adminEmployeesUrl;
    private String adminAuthUrl;

    @BeforeEach
    void setUp() {
        adminEmployeesUrl = "http://localhost:" + port + "/api/admin/employees";
        adminAuthUrl = "http://localhost:" + port + "/api/auth/admin";
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        attendanceCorrectionRepository.deleteAll();
        attendanceRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        attendanceCorrectionRepository.deleteAll();
        attendanceRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    private String loginAsAdmin() {
        AdminLoginRequest loginReq = new AdminLoginRequest("admin", "Admin@12345");
        ResponseEntity<AdminLoginResponse> loginRes = restTemplate.postForEntity(
                adminAuthUrl + "/login",
                loginReq,
                AdminLoginResponse.class
        );
        assertEquals(HttpStatus.OK, loginRes.getStatusCode());
        List<String> cookies = loginRes.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies, "Session cookie must be returned on login");
        String rawCookie = cookies.get(0);
        return rawCookie.split(";")[0].trim();
    }

    @Test
    @DisplayName("End-to-End: Full employee lifecycle with PostgreSQL, Flyway, and Spring Security")
    void shouldExecuteFullEmployeeLifecycle() {
        // 0. Anonymous request to /api/admin/employees is rejected with 401
        ResponseEntity<String> unauthRes = restTemplate.getForEntity(adminEmployeesUrl, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthRes.getStatusCode());

        // 1. Authenticate as administrator
        String sessionCookie = loginAsAdmin();
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        // 2. Create employee via POST with PIN
        CreateEmployeeRequest createReq = new CreateEmployeeRequest("EMP-IT-01", "Jane Austin", "Information Technology", "1234");
        HttpEntity<CreateEmployeeRequest> createEntity = new HttpEntity<>(createReq, authHeaders);
        ResponseEntity<EmployeeResponse> createRes = restTemplate.exchange(
                adminEmployeesUrl,
                HttpMethod.POST,
                createEntity,
                EmployeeResponse.class
        );

        assertEquals(HttpStatus.CREATED, createRes.getStatusCode());
        EmployeeResponse created = createRes.getBody();
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals("EMP-IT-01", created.employeeId());
        assertEquals("Jane Austin", created.fullName());
        assertEquals("Information Technology", created.department());
        assertEquals(EmployeeStatus.ACTIVE, created.status());

        UUID empId = created.id();

        // 3. Verify PIN hash in database is securely hashed (never plaintext '1234')
        Employee dbEmployee = employeeRepository.findById(empId).orElseThrow();
        assertNotNull(dbEmployee.getPinHash());
        assertNotEquals("1234", dbEmployee.getPinHash());
        assertTrue(passwordEncoder.matches("1234", dbEmployee.getPinHash()), "BCrypt must verify raw PIN against hash");

        // 4. Query list via GET with authenticated session
        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders);
        ResponseEntity<EmployeeResponse[]> listRes = restTemplate.exchange(
                adminEmployeesUrl,
                HttpMethod.GET,
                getEntity,
                EmployeeResponse[].class
        );
        assertEquals(HttpStatus.OK, listRes.getStatusCode());
        EmployeeResponse[] list = listRes.getBody();
        assertNotNull(list);
        assertEquals(1, list.length);
        assertEquals("Jane Austin", list[0].fullName());

        // 5. Search by partial name
        ResponseEntity<EmployeeResponse[]> searchRes = restTemplate.exchange(
                adminEmployeesUrl + "?search=Jane",
                HttpMethod.GET,
                getEntity,
                EmployeeResponse[].class
        );
        assertEquals(HttpStatus.OK, searchRes.getStatusCode());
        assertNotNull(searchRes.getBody());
        assertEquals(1, searchRes.getBody().length);

        // 6. Update employee details via PATCH
        UpdateEmployeeRequest updateReq = new UpdateEmployeeRequest("Jane Austen-Wilde", "Lead Architecture");
        HttpEntity<UpdateEmployeeRequest> updateEntity = new HttpEntity<>(updateReq, authHeaders);

        ResponseEntity<EmployeeResponse> updateRes = restTemplate.exchange(
                adminEmployeesUrl + "/" + empId,
                HttpMethod.PATCH,
                updateEntity,
                EmployeeResponse.class
        );
        assertEquals(HttpStatus.OK, updateRes.getStatusCode());
        EmployeeResponse updated = updateRes.getBody();
        assertNotNull(updated);
        assertEquals("Jane Austen-Wilde", updated.fullName());
        assertEquals("Lead Architecture", updated.department());

        // 7. Reset PIN via POST /api/admin/employees/{id}/reset-pin
        ResetPinRequest resetPinReq = new ResetPinRequest("5678");
        HttpEntity<ResetPinRequest> resetPinEntity = new HttpEntity<>(resetPinReq, authHeaders);
        ResponseEntity<Void> resetPinRes = restTemplate.exchange(
                adminEmployeesUrl + "/" + empId + "/reset-pin",
                HttpMethod.POST,
                resetPinEntity,
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, resetPinRes.getStatusCode());

        Employee updatedDbEmployee = employeeRepository.findById(empId).orElseThrow();
        assertTrue(passwordEncoder.matches("5678", updatedDbEmployee.getPinHash()), "PIN hash must be updated to new PIN");
        assertFalse(passwordEncoder.matches("1234", updatedDbEmployee.getPinHash()), "Old PIN must no longer match");

        // 8. Deactivate employee via PATCH status
        UpdateStatusRequest statusReq = new UpdateStatusRequest(EmployeeStatus.INACTIVE);
        HttpEntity<UpdateStatusRequest> statusEntity = new HttpEntity<>(statusReq, authHeaders);
        ResponseEntity<EmployeeResponse> statusRes = restTemplate.exchange(
                adminEmployeesUrl + "/" + empId + "/status",
                HttpMethod.PATCH,
                statusEntity,
                EmployeeResponse.class
        );
        assertEquals(HttpStatus.OK, statusRes.getStatusCode());
        EmployeeResponse deactivated = statusRes.getBody();
        assertNotNull(deactivated);
        assertEquals(EmployeeStatus.INACTIVE, deactivated.status());

        // 9. Verify record is preserved in database
        assertTrue(employeeRepository.findById(empId).isPresent());

        // 10. Test duplicate ID rejection (409 Conflict)
        CreateEmployeeRequest duplicateReq = new CreateEmployeeRequest("EMP-IT-01", "Copycat", "HR", "9999");
        HttpEntity<CreateEmployeeRequest> duplicateEntity = new HttpEntity<>(duplicateReq, authHeaders);
        ResponseEntity<ErrorResponse> duplicateRes = restTemplate.exchange(
                adminEmployeesUrl,
                HttpMethod.POST,
                duplicateEntity,
                ErrorResponse.class
        );
        assertEquals(HttpStatus.CONFLICT, duplicateRes.getStatusCode());
        assertNotNull(duplicateRes.getBody());
        assertTrue(duplicateRes.getBody().message().contains("already exists"));
    }
}
