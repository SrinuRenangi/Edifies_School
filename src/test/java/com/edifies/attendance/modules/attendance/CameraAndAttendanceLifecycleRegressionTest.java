package com.edifies.attendance.modules.attendance;

import com.edifies.attendance.modules.attendance.dto.AttendanceResponse;
import com.edifies.attendance.modules.attendance.dto.AttendanceStatusResponse;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.kiosk.dto.KioskAuthRequest;
import com.edifies.attendance.modules.kiosk.dto.KioskAuthResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.TestPropertySource(properties = "app.kiosk.device-enforcement-enabled=false")
class CameraAndAttendanceLifecycleRegressionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String baseUrl;
    private Employee testEmployee;
    private final String rawPin = "4321";

    private byte[] createValidJpeg() {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());

        attendanceRepository.deleteAll();

        String code = "TEST-CAM-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Employee employee = new Employee(code, "Grace Hopper", "Engineering", passwordEncoder.encode(rawPin));
        employee.setStatus(EmployeeStatus.ACTIVE);
        testEmployee = employeeRepository.save(employee);
    }

    @AfterEach
    void tearDown() {
        attendanceRepository.deleteAll();
    }

    private String authenticateKiosk(String employeeId, String pin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        KioskAuthRequest body = new KioskAuthRequest(employeeId, pin);
        HttpEntity<KioskAuthRequest> request = new HttpEntity<>(body, headers);

        ResponseEntity<KioskAuthResponse> response = restTemplate.postForEntity(
                baseUrl + "/api/kiosk/authenticate",
                request,
                KioskAuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().token();
    }

    @Test
    @DisplayName("Lifecycle Test: Check-In with photo evidence followed immediately by Check-Out with photo evidence")
    void testCheckInFollowedShortlyByCheckOut() {
        // Step 1: Employee authenticates at Kiosk for first check-in
        String sessionToken1 = authenticateKiosk(testEmployee.getEmployeeId(), rawPin);
        assertThat(sessionToken1).isNotBlank();

        // Step 2: Query attendance status - should allow CHECK_IN
        HttpHeaders statusHeaders1 = new HttpHeaders();
        statusHeaders1.set("X-Kiosk-Session", sessionToken1);
        ResponseEntity<AttendanceStatusResponse> statusResp1 = restTemplate.exchange(
                baseUrl + "/api/kiosk/attendance/status",
                HttpMethod.GET,
                new HttpEntity<>(statusHeaders1),
                AttendanceStatusResponse.class
        );
        assertThat(statusResp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResp1.getBody()).isNotNull();
        assertThat(statusResp1.getBody().allowedAction()).isEqualTo("CHECK_IN");

        // Step 3: Perform Check-In with Photo
        HttpHeaders checkInHeaders = new HttpHeaders();
        checkInHeaders.set("X-Kiosk-Session", sessionToken1);
        checkInHeaders.set("Idempotency-Key", "idem-checkin-" + UUID.randomUUID());
        checkInHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> checkInBody = new LinkedMultiValueMap<>();
        ByteArrayResource checkInPhoto = new ByteArrayResource(createValidJpeg()) {
            @Override
            public String getFilename() {
                return "check-in.jpg";
            }
        };
        checkInBody.add("photo", checkInPhoto);

        ResponseEntity<AttendanceResponse> checkInResp = restTemplate.postForEntity(
                baseUrl + "/api/kiosk/attendance/check-in",
                new HttpEntity<>(checkInBody, checkInHeaders),
                AttendanceResponse.class
        );

        assertThat(checkInResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        AttendanceResponse checkInData = checkInResp.getBody();
        assertThat(checkInData).isNotNull();
        assertThat(checkInData.status()).isEqualTo(AttendanceStatus.CHECKED_IN);
        assertThat(checkInData.hasCheckInPhoto()).isTrue();
        assertThat(checkInData.checkInTime()).isNotNull();
        assertThat(checkInData.checkOutTime()).isNull();

        // Step 4: Simulate Kiosk reset for next action
        HttpHeaders resetHeaders = new HttpHeaders();
        resetHeaders.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(
                baseUrl + "/api/kiosk/reset",
                new HttpEntity<>("{\"token\":\"" + sessionToken1 + "\"}", resetHeaders),
                Void.class
        );

        // Step 5: Employee returns shortly afterward for Check-Out (Scenario B)
        String sessionToken2 = authenticateKiosk(testEmployee.getEmployeeId(), rawPin);
        assertThat(sessionToken2).isNotBlank();

        // Step 6: Query attendance status - must now authoritatively report CHECK_OUT
        HttpHeaders statusHeaders2 = new HttpHeaders();
        statusHeaders2.set("X-Kiosk-Session", sessionToken2);
        ResponseEntity<AttendanceStatusResponse> statusResp2 = restTemplate.exchange(
                baseUrl + "/api/kiosk/attendance/status",
                HttpMethod.GET,
                new HttpEntity<>(statusHeaders2),
                AttendanceStatusResponse.class
        );
        assertThat(statusResp2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResp2.getBody()).isNotNull();
        assertThat(statusResp2.getBody().allowedAction()).isEqualTo("CHECK_OUT");
        assertThat(statusResp2.getBody().checkInTime().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .isEqualTo(checkInData.checkInTime().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));

        // Step 7: Perform Check-Out with Photo evidence
        HttpHeaders checkOutHeaders = new HttpHeaders();
        checkOutHeaders.set("X-Kiosk-Session", sessionToken2);
        checkOutHeaders.set("Idempotency-Key", "idem-checkout-" + UUID.randomUUID());
        checkOutHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> checkOutBody = new LinkedMultiValueMap<>();
        ByteArrayResource checkOutPhoto = new ByteArrayResource(createValidJpeg()) {
            @Override
            public String getFilename() {
                return "check-out.jpg";
            }
        };
        checkOutBody.add("photo", checkOutPhoto);

        ResponseEntity<AttendanceResponse> checkOutResp = restTemplate.postForEntity(
                baseUrl + "/api/kiosk/attendance/check-out",
                new HttpEntity<>(checkOutBody, checkOutHeaders),
                AttendanceResponse.class
        );

        assertThat(checkOutResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        AttendanceResponse checkOutData = checkOutResp.getBody();
        assertThat(checkOutData).isNotNull();
        assertThat(checkOutData.status()).isEqualTo(AttendanceStatus.CHECKED_OUT);
        assertThat(checkOutData.hasCheckInPhoto()).isTrue();
        assertThat(checkOutData.hasCheckOutPhoto()).isTrue();
        assertThat(checkOutData.checkInTime()).isNotNull();
        assertThat(checkOutData.checkOutTime()).isNotNull();
        assertThat(checkOutData.checkOutTime()).isAfterOrEqualTo(checkOutData.checkInTime());

        // Step 8: Verify database persistence
        AttendanceRecord savedRecord = attendanceRepository.findById(checkOutData.id()).orElseThrow();
        assertThat(savedRecord.getStatus()).isEqualTo(AttendanceStatus.CHECKED_OUT);
        assertThat(savedRecord.isHasCheckInPhoto()).isTrue();
        assertThat(savedRecord.isHasCheckOutPhoto()).isTrue();

        // Step 9: Re-authenticating on the same day reports completed shift (NONE allowed)
        String sessionToken3 = authenticateKiosk(testEmployee.getEmployeeId(), rawPin);
        HttpHeaders statusHeaders3 = new HttpHeaders();
        statusHeaders3.set("X-Kiosk-Session", sessionToken3);
        ResponseEntity<AttendanceStatusResponse> statusResp3 = restTemplate.exchange(
                baseUrl + "/api/kiosk/attendance/status",
                HttpMethod.GET,
                new HttpEntity<>(statusHeaders3),
                AttendanceStatusResponse.class
        );
        assertThat(statusResp3.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResp3.getBody().allowedAction()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("Lifecycle Test: Repeated check-out attempts are prevented cleanly")
    void testPreventDuplicateCheckOut() {
        String token1 = authenticateKiosk(testEmployee.getEmployeeId(), rawPin);

        // Check in
        HttpHeaders inHeaders = new HttpHeaders();
        inHeaders.set("X-Kiosk-Session", token1);
        inHeaders.set("Idempotency-Key", "idem-in-dup-" + UUID.randomUUID());
        restTemplate.postForEntity(
                baseUrl + "/api/kiosk/attendance/check-in",
                new HttpEntity<>(new LinkedMultiValueMap<>(), inHeaders),
                AttendanceResponse.class
        );

        // Check out once
        String token2 = authenticateKiosk(testEmployee.getEmployeeId(), rawPin);
        HttpHeaders outHeaders = new HttpHeaders();
        outHeaders.set("X-Kiosk-Session", token2);
        outHeaders.set("Idempotency-Key", "idem-out-1-" + UUID.randomUUID());
        ResponseEntity<AttendanceResponse> out1 = restTemplate.postForEntity(
                baseUrl + "/api/kiosk/attendance/check-out",
                new HttpEntity<>(new LinkedMultiValueMap<>(), outHeaders),
                AttendanceResponse.class
        );
        assertThat(out1.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Attempt second check-out with distinct session and key - should fail with 400 Bad Request
        String token3 = authenticateKiosk(testEmployee.getEmployeeId(), rawPin);
        HttpHeaders outHeaders2 = new HttpHeaders();
        outHeaders2.set("X-Kiosk-Session", token3);
        outHeaders2.set("Idempotency-Key", "idem-out-2-" + UUID.randomUUID());
        ResponseEntity<String> out2 = restTemplate.postForEntity(
                baseUrl + "/api/kiosk/attendance/check-out",
                new HttpEntity<>(new LinkedMultiValueMap<>(), outHeaders2),
                String.class
        );
        assertThat(out2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
