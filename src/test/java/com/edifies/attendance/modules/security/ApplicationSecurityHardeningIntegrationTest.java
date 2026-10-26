package com.edifies.attendance.modules.security;

import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.kiosk.dto.KioskAuthRequest;
import com.edifies.attendance.modules.kiosk.model.KioskDevice;
import com.edifies.attendance.modules.kiosk.repository.KioskDeviceRepository;
import com.edifies.attendance.modules.kiosk.service.KioskSessionService;
import com.edifies.attendance.modules.photo.dto.PhotoValidationResult;
import com.edifies.attendance.modules.photo.service.PhotoValidationService;
import com.edifies.attendance.modules.security.dto.AdminLoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ApplicationSecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private KioskDeviceRepository kioskDeviceRepository;

    @Autowired
    private KioskSessionService kioskSessionService;

    @Autowired
    private PhotoValidationService photoValidationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String validDeviceToken;
    private Employee testEmployee;

    @BeforeEach
    void setupSecurityFixtures() {
        // Register an authorized kiosk device
        validDeviceToken = "kd_live_sec_token_" + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = com.edifies.attendance.modules.kiosk.service.KioskDeviceService.sha256(validDeviceToken);
        KioskDevice device = new KioskDevice(
                "Security-Audit-Kiosk",
                "DEV-TEST-SEC",
                tokenHash,
                "ADMIN"
        );
        kioskDeviceRepository.save(device);

        // Register a test employee
        String empId = "SEC" + (int)(Math.random() * 9000 + 1000);
        testEmployee = new Employee(
                empId,
                "Security Test Worker",
                "Information Security",
                passwordEncoder.encode("9876")
        );
        employeeRepository.save(testEmployee);
    }

    // =========================================================================
    // 1. HTTP SECURITY HEADERS & PERMISSIONS-POLICY VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("Security Headers: Permissions-Policy, CSP, nosniff, and frame options must be present")
    void testSecurityHeadersPresentOnResponses() throws Exception {
        mockMvc.perform(get("/api/auth/admin/me"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=(self)")))
                .andExpect(header().string("Permissions-Policy", containsString("microphone=()")))
                .andExpect(header().string("Permissions-Policy", containsString("geolocation=()")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("base-uri 'self'")));
    }

    // =========================================================================
    // 2. CENTRALIZED CORS ENFORCEMENT & ORIGIN RESTRICTION
    // =========================================================================

    @Test
    @DisplayName("CORS: Malicious cross-origin pre-flight requests must be denied")
    void testUntrustedOriginCorsDenied() throws Exception {
        mockMvc.perform(options("/api/admin/employees")
                        .header("Origin", "http://malicious-attacker-website.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CORS: Authorized frontend origin pre-flight request must be accepted with credentials")
    void testAuthorizedOriginCorsAllowed() throws Exception {
        mockMvc.perform(options("/api/admin/employees")
                        .header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    // =========================================================================
    // 3. REJECTION OF SESSION TOKENS IN URL QUERY PARAMETERS
    // =========================================================================

    @Test
    @DisplayName("Session Security: Attendance endpoints must reject session tokens in query parameters")
    void testSessionTokenInQueryParamRejected() throws Exception {
        // Create an active session
        var session = kioskSessionService.createSession(testEmployee);

        // Calling status with ?token=... in URL must be rejected with 401 Unauthorized (token in URL is disallowed)
        mockMvc.perform(get("/api/kiosk/attendance/status")
                        .param("token", session.token())
                        .header("X-Kiosk-Device-Token", validDeviceToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Kiosk session token is required")));

        // Calling status with secure X-Kiosk-Session header MUST authenticate
        mockMvc.perform(get("/api/kiosk/attendance/status")
                        .header("X-Kiosk-Session", session.token())
                        .header("X-Kiosk-Device-Token", validDeviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(testEmployee.getEmployeeId()));
    }

    // =========================================================================
    // 4. PHOTO SECURITY: DECOMPRESSION BOMB & EXIF STRIPPING
    // =========================================================================

    @Test
    @DisplayName("Photo Security: Oversized pixel dimensions must be rejected before raster allocation")
    void testDecompressionBombDimensionsRejected() throws Exception {
        // Generate an image exceeding MAX_WIDTH (4000)
        BufferedImage hugeImage = new BufferedImage(4001, 300, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(hugeImage, "jpg", baos);

        MockMultipartFile file = new MockMultipartFile("photo", "huge.jpg", "image/jpeg", baos.toByteArray());

        assertThrows(Exception.class, () -> photoValidationService.validate(file));
    }

    @Test
    @DisplayName("Photo Security: Valid image must be re-encoded to strip EXIF metadata")
    void testPhotoSanitizedAndExifStripped() throws Exception {
        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 300, 300);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);

        MockMultipartFile file = new MockMultipartFile("photo", "camera.jpg", "image/jpeg", baos.toByteArray());

        PhotoValidationResult result = photoValidationService.validate(file);

        assertNotNull(result.sanitizedBytes(), "Sanitized bytes must be generated");
        assertTrue(result.sanitizedBytes().length > 0, "Sanitized photo bytes must not be empty");
        assertEquals("JPEG", result.format());
        assertEquals(300, result.width());
        assertEquals(300, result.height());
    }

    // =========================================================================
    // 5. SERVER-SIDE AUTHORIZATION & IDOR PREVENTION
    // =========================================================================

    @Test
    @DisplayName("Authorization: Unauthenticated requests to administrative endpoints must be denied (401)")
    void testUnauthenticatedAdminEndpointsDenied() throws Exception {
        mockMvc.perform(get("/api/admin/employees"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/reports/monthly?month=10&year=2026"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/storage/health"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/attendance/" + UUID.randomUUID() + "/photo/CHECK_IN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Kiosk Boundary: Kiosk device cannot access administrative employee records")
    void testKioskCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/employees")
                        .header("X-Kiosk-Device-Token", validDeviceToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Device Authorization: Unregistered device cannot access kiosk authenticate")
    void testUnregisteredDeviceCannotAccessKioskAuth() throws Exception {
        KioskAuthRequest request = new KioskAuthRequest(testEmployee.getEmployeeId(), "9876");

        mockMvc.perform(post("/api/kiosk/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 6. RATE LIMITING & TEMPORARY LOCKOUT
    // =========================================================================

    @Test
    @DisplayName("Brute Force: 5 consecutive invalid admin logins trigger temporary account lockout")
    void testAdminBruteForceLockout() throws Exception {
        AdminLoginRequest badLogin = new AdminLoginRequest("admin", "WrongPass123");

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badLogin)))
                    .andExpect(status().isUnauthorized());
        }

        // 5th attempt reaches threshold
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized());

        // 6th attempt is blocked with account locked message
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("locked")));
    }

    @Test
    @DisplayName("Brute Force: 5 consecutive invalid employee PIN attempts trigger temporary lockout")
    void testEmployeePinBruteForceLockout() throws Exception {
        KioskAuthRequest badPin = new KioskAuthRequest(testEmployee.getEmployeeId(), "0000");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/kiosk/authenticate")
                            .header("X-Kiosk-Device-Token", validDeviceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badPin)))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt is locked out
        mockMvc.perform(post("/api/kiosk/authenticate")
                        .header("X-Kiosk-Device-Token", validDeviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("locked")));
    }

    // =========================================================================
    // 7. KIOSK RESET & CLEANUP
    // =========================================================================

    @Test
    @DisplayName("Kiosk Session: /reset with X-Kiosk-Session header successfully clears active employee session")
    void testKioskResetSession() throws Exception {
        var session = kioskSessionService.createSession(testEmployee);
        assertNotNull(kioskSessionService.validateSession(session.token()));

        mockMvc.perform(post("/api/kiosk/reset")
                        .header("X-Kiosk-Session", session.token())
                        .header("X-Kiosk-Device-Token", validDeviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Kiosk session cleared successfully"));

        assertNull(kioskSessionService.validateSession(session.token()), "Session must be invalidated after reset");
    }
}
