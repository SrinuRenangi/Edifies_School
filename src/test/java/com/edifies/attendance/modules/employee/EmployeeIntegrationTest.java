package com.edifies.attendance.modules.employee;

import com.edifies.attendance.common.exception.ErrorResponse;
import com.edifies.attendance.modules.employee.dto.CreateEmployeeRequest;
import com.edifies.attendance.modules.employee.dto.EmployeeResponse;
import com.edifies.attendance.modules.employee.dto.UpdateEmployeeRequest;
import com.edifies.attendance.modules.employee.dto.UpdateStatusRequest;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;

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

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/admin/employees";
        // Configure JdkClientHttpRequestFactory to support HTTP PATCH
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        employeeRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End: Full employee lifecycle with PostgreSQL and Flyway")
    void shouldExecuteFullEmployeeLifecycle() {
        // 1. Create employee via POST
        CreateEmployeeRequest createReq = new CreateEmployeeRequest("EMP-IT-01", "Jane Austin", "Information Technology");
        ResponseEntity<EmployeeResponse> createRes = restTemplate.postForEntity(
                baseUrl,
                createReq,
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

        // 2. Query list via GET
        ResponseEntity<EmployeeResponse[]> listRes = restTemplate.getForEntity(
                baseUrl,
                EmployeeResponse[].class
        );
        assertEquals(HttpStatus.OK, listRes.getStatusCode());
        EmployeeResponse[] list = listRes.getBody();
        assertNotNull(list);
        assertEquals(1, list.length);
        assertEquals("Jane Austin", list[0].fullName());

        // 3. Search by partial name
        ResponseEntity<EmployeeResponse[]> searchRes = restTemplate.getForEntity(
                baseUrl + "?search=Jane",
                EmployeeResponse[].class
        );
        assertEquals(HttpStatus.OK, searchRes.getStatusCode());
        assertNotNull(searchRes.getBody());
        assertEquals(1, searchRes.getBody().length);

        // 4. Update employee details via PATCH
        UpdateEmployeeRequest updateReq = new UpdateEmployeeRequest("Jane Austen-Wilde", "Lead Architecture");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UpdateEmployeeRequest> updateEntity = new HttpEntity<>(updateReq, headers);

        ResponseEntity<EmployeeResponse> updateRes = restTemplate.exchange(
                baseUrl + "/" + empId,
                HttpMethod.PATCH,
                updateEntity,
                EmployeeResponse.class
        );
        assertEquals(HttpStatus.OK, updateRes.getStatusCode());
        EmployeeResponse updated = updateRes.getBody();
        assertNotNull(updated);
        assertEquals("Jane Austen-Wilde", updated.fullName());
        assertEquals("Lead Architecture", updated.department());

        // 5. Deactivate employee via PATCH status
        UpdateStatusRequest statusReq = new UpdateStatusRequest(EmployeeStatus.INACTIVE);
        HttpEntity<UpdateStatusRequest> statusEntity = new HttpEntity<>(statusReq, headers);
        ResponseEntity<EmployeeResponse> statusRes = restTemplate.exchange(
                baseUrl + "/" + empId + "/status",
                HttpMethod.PATCH,
                statusEntity,
                EmployeeResponse.class
        );
        assertEquals(HttpStatus.OK, statusRes.getStatusCode());
        EmployeeResponse deactivated = statusRes.getBody();
        assertNotNull(deactivated);
        assertEquals(EmployeeStatus.INACTIVE, deactivated.status());

        // 6. Verify record is still preserved in database (not deleted)
        assertTrue(employeeRepository.findById(empId).isPresent());

        // 7. Test duplicate ID rejection (409 Conflict)
        CreateEmployeeRequest duplicateReq = new CreateEmployeeRequest("EMP-IT-01", "Copycat", "HR");
        ResponseEntity<ErrorResponse> duplicateRes = restTemplate.postForEntity(
                baseUrl,
                duplicateReq,
                ErrorResponse.class
        );
        assertEquals(HttpStatus.CONFLICT, duplicateRes.getStatusCode());
        assertNotNull(duplicateRes.getBody());
        assertTrue(duplicateRes.getBody().message().contains("already exists"));
    }
}
