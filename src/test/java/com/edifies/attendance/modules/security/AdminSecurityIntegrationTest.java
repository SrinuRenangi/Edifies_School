package com.edifies.attendance.modules.security;

import com.edifies.attendance.common.exception.ErrorResponse;
import com.edifies.attendance.modules.security.dto.AdminLoginRequest;
import com.edifies.attendance.modules.security.dto.AdminLoginResponse;
import com.edifies.attendance.modules.security.dto.AdminProfileResponse;
import com.edifies.attendance.modules.security.model.AdminUser;
import com.edifies.attendance.modules.security.repository.AdminUserRepository;
import com.edifies.attendance.modules.security.service.LoginAttemptService;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminSecurityIntegrationTest {

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

    private String authUrl;
    private String adminEmployeesUrl;

    @BeforeEach
    void setUp() {
        authUrl = "http://localhost:" + port + "/api/auth/admin";
        adminEmployeesUrl = "http://localhost:" + port + "/api/admin/employees";
        loginAttemptService.resetLockout("admin");
        loginAttemptService.resetLockout("inactive_admin");
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

    @Test
    @DisplayName("Admin Login: Correct credentials succeed and return HTTP-only session")
    void shouldLoginSuccessfullyWithValidCredentials() {
        AdminLoginRequest req = new AdminLoginRequest("admin", "Admin@12345");
        ResponseEntity<AdminLoginResponse> response = restTemplate.postForEntity(
                authUrl + "/login", req, AdminLoginResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        AdminLoginResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("admin", body.username());
        assertEquals("System Administrator", body.fullName());
        assertEquals("ROLE_ADMIN", body.role());

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies, "Must set session cookie");
        assertTrue(cookies.stream().anyMatch(c -> c.contains("JSESSIONID")), "Cookie must contain JSESSIONID");
    }

    @Test
    @DisplayName("Admin Login: Incorrect password fails with 401 Unauthorized")
    void shouldRejectIncorrectPassword() {
        AdminLoginRequest req = new AdminLoginRequest("admin", "WrongPassword!99");
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                authUrl + "/login", req, ErrorResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid username or password.", response.getBody().message());
    }

    @Test
    @DisplayName("Admin Login: Inactive administrator cannot log in")
    void shouldRejectInactiveAdministrator() {
        if (!adminUserRepository.existsByUsernameIgnoreCase("inactive_admin")) {
            AdminUser inactiveAdmin = new AdminUser(
                    "inactive_admin",
                    passwordEncoder.encode("Secret@12345"),
                    "Disabled Staff",
                    "ROLE_ADMIN",
                    false
            );
            adminUserRepository.save(inactiveAdmin);
        }

        AdminLoginRequest req = new AdminLoginRequest("inactive_admin", "Secret@12345");
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                authUrl + "/login", req, ErrorResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Admin Me: Returns admin profile when authenticated, 401 when anonymous")
    void shouldVerifyAdminSessionProfile() {
        // Anonymous me
        ResponseEntity<String> unauthMe = restTemplate.getForEntity(authUrl + "/me", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthMe.getStatusCode());

        // Login
        AdminLoginRequest req = new AdminLoginRequest("admin", "Admin@12345");
        ResponseEntity<AdminLoginResponse> loginRes = restTemplate.postForEntity(authUrl + "/login", req, AdminLoginResponse.class);
        assertEquals(HttpStatus.OK, loginRes.getStatusCode());

        HttpHeaders headers = extractSessionHeaders(loginRes);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<AdminProfileResponse> meRes = restTemplate.exchange(
                authUrl + "/me", HttpMethod.GET, entity, AdminProfileResponse.class);

        assertEquals(HttpStatus.OK, meRes.getStatusCode());
        assertNotNull(meRes.getBody());
        assertEquals("admin", meRes.getBody().username());
    }

    @Test
    @DisplayName("Admin Logout: Invalidate session and subsequent access fails")
    void shouldLogoutAndInvalidateSession() {
        // Login
        AdminLoginRequest req = new AdminLoginRequest("admin", "Admin@12345");
        ResponseEntity<AdminLoginResponse> loginRes = restTemplate.postForEntity(authUrl + "/login", req, AdminLoginResponse.class);
        HttpHeaders headers = extractSessionHeaders(loginRes);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // Logout
        ResponseEntity<String> logoutRes = restTemplate.exchange(
                authUrl + "/logout", HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.OK, logoutRes.getStatusCode());

        // Subsequent access to protected endpoint with old cookie must fail (401)
        ResponseEntity<String> protectedRes = restTemplate.exchange(
                adminEmployeesUrl, HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, protectedRes.getStatusCode());
    }

    @Test
    @DisplayName("Brute Force: 5 failed attempts lock out administrator")
    void shouldLockoutAfterRepeatedFailures() {
        AdminLoginRequest badReq = new AdminLoginRequest("admin", "WrongGuess123!");

        for (int i = 0; i < 5; i++) {
            restTemplate.postForEntity(authUrl + "/login", badReq, String.class);
        }

        // 6th attempt must be locked out
        ResponseEntity<ErrorResponse> lockedRes = restTemplate.postForEntity(
                authUrl + "/login", badReq, ErrorResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, lockedRes.getStatusCode());
        assertTrue(lockedRes.getBody().message().contains("Account temporarily locked"));

        // Clean up
        loginAttemptService.resetLockout("admin");
    }
}
