package com.edifies.attendance.modules.kiosk;

import com.edifies.attendance.common.exception.ErrorResponse;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.kiosk.dto.*;
import com.edifies.attendance.modules.kiosk.model.KioskDevice;
import com.edifies.attendance.modules.kiosk.repository.KioskDeviceAuditLogRepository;
import com.edifies.attendance.modules.kiosk.repository.KioskDeviceRepository;
import com.edifies.attendance.modules.kiosk.repository.KioskPairingCodeRepository;
import com.edifies.attendance.modules.security.dto.AdminLoginRequest;
import com.edifies.attendance.modules.security.dto.AdminLoginResponse;
import com.edifies.attendance.modules.security.model.AdminUser;
import com.edifies.attendance.modules.security.repository.AdminUserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KioskDeviceIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KioskDeviceRepository deviceRepository;

    @Autowired
    private KioskPairingCodeRepository pairingCodeRepository;

    @Autowired
    private KioskDeviceAuditLogRepository auditLogRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminAuthUrl;
    private String adminDevicesUrl;
    private String kioskDevicesUrl;
    private String kioskAuthUrl;

    private String adminSessionCookie;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        adminAuthUrl = "http://localhost:" + port + "/api/auth/admin";
        adminDevicesUrl = "http://localhost:" + port + "/api/admin/devices";
        kioskDevicesUrl = "http://localhost:" + port + "/api/kiosk/devices";
        kioskAuthUrl = "http://localhost:" + port + "/api/kiosk/authenticate";

        cleanupData();

        // Seed Active Employee for kiosk PIN testing
        Employee emp = new Employee("EMP-DEV-01", "Ada Lovelace", "Engineering", passwordEncoder.encode("1234"));
        emp.setStatus(EmployeeStatus.ACTIVE);
        employeeRepository.save(emp);

        // Login Admin
        AdminLoginRequest loginReq = new AdminLoginRequest("admin", "Admin@12345");
        ResponseEntity<AdminLoginResponse> loginRes = restTemplate.postForEntity(adminAuthUrl + "/login", loginReq, AdminLoginResponse.class);
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
        auditLogRepository.deleteAll();
        pairingCodeRepository.deleteAll();
        deviceRepository.deleteAll();
        employeeRepository.findByEmployeeIdIgnoreCase("EMP-DEV-01").ifPresent(employeeRepository::delete);
    }

    private HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, adminSessionCookie);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("Admin can initiate device pairing; unauthenticated user is rejected")
    void shouldInitiatePairingWithAdminAuthOnly() {
        // 1. Unauthenticated initiation must fail with 401
        InitiatePairingRequest req = new InitiatePairingRequest("Front Desk iPad");
        ResponseEntity<String> unauthRes = restTemplate.postForEntity(
                adminDevicesUrl + "/pair/initiate", req, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthRes.getStatusCode());

        // 2. Authenticated Admin initiation succeeds
        HttpEntity<InitiatePairingRequest> adminEntity = new HttpEntity<>(req, createAdminHeaders());
        ResponseEntity<InitiatePairingResponse> authRes = restTemplate.postForEntity(
                adminDevicesUrl + "/pair/initiate", adminEntity, InitiatePairingResponse.class);

        assertEquals(HttpStatus.OK, authRes.getStatusCode());
        InitiatePairingResponse body = authRes.getBody();
        assertNotNull(body);
        assertEquals("Front Desk iPad", body.deviceName());
        assertNotNull(body.pairingCode());
        assertEquals(600, body.expiresInSeconds());
    }

    @Test
    @DisplayName("End-to-End Pairing Flow: Initiate -> Complete -> Active Device Verification")
    void shouldCompleteFullDevicePairingWorkflow() {
        // 1. Admin initiates pairing
        InitiatePairingRequest initReq = new InitiatePairingRequest("Reception Workstation");
        HttpEntity<InitiatePairingRequest> adminEntity = new HttpEntity<>(initReq, createAdminHeaders());
        ResponseEntity<InitiatePairingResponse> initRes = restTemplate.postForEntity(
                adminDevicesUrl + "/pair/initiate", adminEntity, InitiatePairingResponse.class);
        assertEquals(HttpStatus.OK, initRes.getStatusCode());
        String code = initRes.getBody().pairingCode();

        // 2. Kiosk terminal submits pairing code
        CompletePairingRequest pairReq = new CompletePairingRequest(code, "Chrome OS Terminal");
        ResponseEntity<CompletePairingResponse> pairRes = restTemplate.postForEntity(
                kioskDevicesUrl + "/pair", pairReq, CompletePairingResponse.class);

        assertEquals(HttpStatus.OK, pairRes.getStatusCode());
        CompletePairingResponse pairBody = pairRes.getBody();
        assertNotNull(pairBody);
        assertEquals("Reception Workstation", pairBody.deviceName());
        assertTrue(pairBody.deviceToken().startsWith("kd_live_"));
        assertEquals("ACTIVE", pairBody.status());

        String deviceToken = pairBody.deviceToken();

        // 3. Verify kiosk status using newly issued device token
        HttpHeaders deviceHeaders = new HttpHeaders();
        deviceHeaders.set("X-Kiosk-Device-Token", deviceToken);
        HttpEntity<Void> deviceEntity = new HttpEntity<>(deviceHeaders);

        ResponseEntity<DeviceStatusResponse> statusRes = restTemplate.exchange(
                kioskDevicesUrl + "/status", HttpMethod.GET, deviceEntity, DeviceStatusResponse.class);
        assertEquals(HttpStatus.OK, statusRes.getStatusCode());
        assertTrue(statusRes.getBody().registered());
        assertEquals("Reception Workstation", statusRes.getBody().deviceName());

        // 4. Verify admin device list reflects active device
        HttpEntity<Void> adminGetEntity = new HttpEntity<>(createAdminHeaders());
        ResponseEntity<List<KioskDeviceResponse>> listRes = restTemplate.exchange(
                adminDevicesUrl, HttpMethod.GET, adminGetEntity, new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, listRes.getStatusCode());
        assertEquals(1, listRes.getBody().size());
        assertEquals("Reception Workstation", listRes.getBody().get(0).deviceName());
        assertEquals("ACTIVE", listRes.getBody().get(0).status());
    }

    @Test
    @DisplayName("Pairing Security: Reusing a consumed pairing code fails")
    void shouldRejectPairingCodeReuse() {
        // Initiate
        InitiatePairingRequest initReq = new InitiatePairingRequest("Office Tablet");
        HttpEntity<InitiatePairingRequest> adminEntity = new HttpEntity<>(initReq, createAdminHeaders());
        ResponseEntity<InitiatePairingResponse> initRes = restTemplate.postForEntity(
                adminDevicesUrl + "/pair/initiate", adminEntity, InitiatePairingResponse.class);
        String code = initRes.getBody().pairingCode();

        // First use succeeds
        CompletePairingRequest pairReq = new CompletePairingRequest(code, null);
        ResponseEntity<CompletePairingResponse> res1 = restTemplate.postForEntity(
                kioskDevicesUrl + "/pair", pairReq, CompletePairingResponse.class);
        assertEquals(HttpStatus.OK, res1.getStatusCode());

        // Second use MUST fail
        ResponseEntity<ErrorResponse> res2 = restTemplate.postForEntity(
                kioskDevicesUrl + "/pair", pairReq, ErrorResponse.class);
        assertEquals(HttpStatus.UNAUTHORIZED, res2.getStatusCode());
        assertTrue(res2.getBody().message().contains("already been used"));
    }

    @Test
    @DisplayName("Device Authorization: Unregistered client is rejected from kiosk endpoints")
    void shouldRejectUnregisteredClientFromKioskAuthenticate() {
        KioskAuthRequest authReq = new KioskAuthRequest("EMP-DEV-01", "1234");

        // Request with NO device token header or cookie
        ResponseEntity<String> response = restTemplate.postForEntity(kioskAuthUrl, authReq, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody().contains("DEVICE_NOT_AUTHORIZED") || response.getBody().contains("Workstation registration required"));
    }

    @Test
    @DisplayName("Device Authorization: Registered client can perform employee PIN authentication")
    void shouldAllowRegisteredClientToAuthenticateEmployee() {
        // 1. Pair a device
        InitiatePairingRequest initReq = new InitiatePairingRequest("Security Desk PC");
        ResponseEntity<InitiatePairingResponse> initRes = restTemplate.postForEntity(
                adminDevicesUrl + "/pair/initiate", new HttpEntity<>(initReq, createAdminHeaders()), InitiatePairingResponse.class);
        String code = initRes.getBody().pairingCode();

        ResponseEntity<CompletePairingResponse> pairRes = restTemplate.postForEntity(
                kioskDevicesUrl + "/pair", new CompletePairingRequest(code, null), CompletePairingResponse.class);
        String deviceToken = pairRes.getBody().deviceToken();

        // 2. Perform employee kiosk authentication with device token header
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Kiosk-Device-Token", deviceToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        KioskAuthRequest authReq = new KioskAuthRequest("EMP-DEV-01", "1234");
        HttpEntity<KioskAuthRequest> authEntity = new HttpEntity<>(authReq, headers);

        ResponseEntity<KioskAuthResponse> authRes = restTemplate.postForEntity(kioskAuthUrl, authEntity, KioskAuthResponse.class);
        assertEquals(HttpStatus.OK, authRes.getStatusCode());
        assertNotNull(authRes.getBody().token());
        assertEquals("EMP-DEV-01", authRes.getBody().employeeId());
    }

    @Test
    @DisplayName("Revocation: Admin revoking device immediately blocks all subsequent kiosk requests")
    void shouldBlockKioskRequestsImmediatelyAfterDeviceRevocation() {
        // 1. Pair device
        InitiatePairingRequest initReq = new InitiatePairingRequest("Warehouse Kiosk");
        ResponseEntity<InitiatePairingResponse> initRes = restTemplate.postForEntity(
                adminDevicesUrl + "/pair/initiate", new HttpEntity<>(initReq, createAdminHeaders()), InitiatePairingResponse.class);
        String code = initRes.getBody().pairingCode();

        ResponseEntity<CompletePairingResponse> pairRes = restTemplate.postForEntity(
                kioskDevicesUrl + "/pair", new CompletePairingRequest(code, null), CompletePairingResponse.class);
        UUID deviceId = pairRes.getBody().deviceId();
        String deviceToken = pairRes.getBody().deviceToken();

        // 2. Verify device works
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Kiosk-Device-Token", deviceToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<DeviceStatusResponse> beforeRevoke = restTemplate.exchange(
                kioskDevicesUrl + "/status", HttpMethod.GET, new HttpEntity<>(headers), DeviceStatusResponse.class);
        assertEquals(HttpStatus.OK, beforeRevoke.getStatusCode());

        // 3. Admin revokes device
        RevokeDeviceRequest revokeReq = new RevokeDeviceRequest("Workstation stolen or replaced");
        HttpEntity<RevokeDeviceRequest> revokeEntity = new HttpEntity<>(revokeReq, createAdminHeaders());
        ResponseEntity<KioskDeviceResponse> revokeRes = restTemplate.postForEntity(
                adminDevicesUrl + "/" + deviceId + "/revoke", revokeEntity, KioskDeviceResponse.class);
        assertEquals(HttpStatus.OK, revokeRes.getStatusCode());
        assertEquals("REVOKED", revokeRes.getBody().status());

        // 4. Subsequent kiosk request with old device token MUST be immediately rejected with 401
        ResponseEntity<String> afterRevoke = restTemplate.exchange(
                kioskDevicesUrl + "/status", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, afterRevoke.getStatusCode());
        assertTrue(afterRevoke.getBody().contains("DEVICE_REVOKED") || afterRevoke.getBody().contains("revoked"));
    }

    @Test
    @DisplayName("Boundary: Device token cannot access admin routes")
    void shouldPreventDeviceTokenFromAccessingAdminRoutes() {
        // 1. Pair device
        InitiatePairingRequest initReq = new InitiatePairingRequest("Lobby Terminal");
        ResponseEntity<InitiatePairingResponse> initRes = restTemplate.postForEntity(
                adminDevicesUrl + "/pair/initiate", new HttpEntity<>(initReq, createAdminHeaders()), InitiatePairingResponse.class);
        String code = initRes.getBody().pairingCode();

        ResponseEntity<CompletePairingResponse> pairRes = restTemplate.postForEntity(
                kioskDevicesUrl + "/pair", new CompletePairingRequest(code, null), CompletePairingResponse.class);
        String deviceToken = pairRes.getBody().deviceToken();

        // 2. Attempt to call /api/admin/devices with device token
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Kiosk-Device-Token", deviceToken);
        headers.setBearerAuth(deviceToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> adminRes = restTemplate.exchange(
                adminDevicesUrl, HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, adminRes.getStatusCode(),
                "Device token MUST NEVER grant access to administrative endpoints");
    }

    @Test
    @DisplayName("Audit Trail: Device lifecycle operations record immutable audit events")
    void shouldRecordDeviceAuditTrail() {
        // 1. Initiate pairing
        InitiatePairingRequest initReq = new InitiatePairingRequest("Audit Test Device");
        ResponseEntity<InitiatePairingResponse> initRes = restTemplate.postForEntity(
                adminDevicesUrl + "/pair/initiate", new HttpEntity<>(initReq, createAdminHeaders()), InitiatePairingResponse.class);
        String code = initRes.getBody().pairingCode();

        // 2. Complete pairing
        ResponseEntity<CompletePairingResponse> pairRes = restTemplate.postForEntity(
                kioskDevicesUrl + "/pair", new CompletePairingRequest(code, null), CompletePairingResponse.class);
        UUID deviceId = pairRes.getBody().deviceId();

        // 3. Revoke device
        restTemplate.postForEntity(
                adminDevicesUrl + "/" + deviceId + "/revoke",
                new HttpEntity<>(new RevokeDeviceRequest("Audit check"), createAdminHeaders()),
                KioskDeviceResponse.class);

        // 4. Query audit trail
        ResponseEntity<List<DeviceAuditLogResponse>> auditRes = restTemplate.exchange(
                adminDevicesUrl + "/audit", HttpMethod.GET, new HttpEntity<>(createAdminHeaders()),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, auditRes.getStatusCode());
        List<DeviceAuditLogResponse> logs = auditRes.getBody();
        assertNotNull(logs);
        assertTrue(logs.size() >= 3);

        boolean hasInitiated = logs.stream().anyMatch(l -> "PAIRING_INITIATED".equals(l.eventType()));
        boolean hasCompleted = logs.stream().anyMatch(l -> "PAIRING_COMPLETED".equals(l.eventType()));
        boolean hasRevoked = logs.stream().anyMatch(l -> "DEVICE_REVOKED".equals(l.eventType()));

        assertTrue(hasInitiated, "Must log PAIRING_INITIATED");
        assertTrue(hasCompleted, "Must log PAIRING_COMPLETED");
        assertTrue(hasRevoked, "Must log DEVICE_REVOKED");
    }
}
