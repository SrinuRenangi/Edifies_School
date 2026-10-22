package com.edifies.attendance.modules.kiosk.security;

import com.edifies.attendance.modules.kiosk.model.DeviceAuditEventType;
import com.edifies.attendance.modules.kiosk.model.KioskDevice;
import com.edifies.attendance.modules.kiosk.service.KioskDeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Component
public class KioskDeviceAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(KioskDeviceAuthenticationFilter.class);
    private static final String DEVICE_TOKEN_HEADER = "X-Kiosk-Device-Token";
    private static final String DEVICE_COOKIE_NAME = "kiosk_device_token";
    private static final String PAIR_ENDPOINT = "/api/kiosk/devices/pair";

    private final KioskDeviceService kioskDeviceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KioskDeviceAuthenticationFilter(
            @org.springframework.beans.factory.annotation.Autowired(required = false) KioskDeviceService kioskDeviceService) {
        this.kioskDeviceService = kioskDeviceService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (kioskDeviceService == null) {
            return true;
        }
        String path = request.getRequestURI();

        // Only filter requests targeted at /api/kiosk/**
        if (!path.startsWith("/api/kiosk")) {
            return true;
        }

        // Public device pairing endpoint must be accessible without prior device token
        if (path.equals(PAIR_ENDPOINT) && "POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Skip CORS pre-flight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // If device enforcement is disabled in config, pass through
        if (!kioskDeviceService.isDeviceEnforcementEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = resolveDeviceToken(request);

        if (rawToken == null || rawToken.isBlank()) {
            log.warn("Blocked unauthenticated kiosk request to '{}' from IP '{}': missing device token",
                    request.getRequestURI(), request.getRemoteAddr());

            kioskDeviceService.recordAudit(null, "UNREGISTERED", DeviceAuditEventType.DEVICE_AUTH_FAILED,
                    "ANONYMOUS", request, "Restricted kiosk request without device token: " + request.getRequestURI());

            sendUnauthorizedError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Workstation registration required. Device is not authorized for kiosk attendance.",
                    "DEVICE_NOT_AUTHORIZED");
            return;
        }

        Optional<KioskDevice> deviceOpt = kioskDeviceService.verifyDeviceToken(rawToken);

        if (deviceOpt.isEmpty()) {
            log.warn("Blocked kiosk request to '{}' from IP '{}': invalid or revoked device token",
                    request.getRequestURI(), request.getRemoteAddr());

            kioskDeviceService.recordAudit(null, "UNKNOWN", DeviceAuditEventType.DEVICE_AUTH_FAILED,
                    "ANONYMOUS", request, "Restricted kiosk request with invalid/revoked device token");

            sendUnauthorizedError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Workstation authorization is invalid or has been revoked.",
                    "DEVICE_REVOKED");
            return;
        }

        KioskDevice device = deviceOpt.get();
        request.setAttribute("kioskDevice", device);
        kioskDeviceService.recordDeviceActivity(device, request);

        filterChain.doFilter(request, response);
    }

    private String resolveDeviceToken(HttpServletRequest request) {
        // 1. Check custom header
        String headerToken = request.getHeader(DEVICE_TOKEN_HEADER);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken.trim();
        }

        // 2. Check cookies
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (DEVICE_COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue().trim();
                }
            }
        }

        return null;
    }

    private void sendUnauthorizedError(HttpServletRequest request, HttpServletResponse response, int status, String message, String code) throws IOException {
        try {
            if (request != null && request.getInputStream() != null) {
                request.getInputStream().readAllBytes();
            }
        } catch (Exception ignored) {
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> errorPayload = Map.of(
                "status", status,
                "error", "Unauthorized",
                "message", message,
                "code", code
        );
        objectMapper.writeValue(response.getOutputStream(), errorPayload);
    }
}
