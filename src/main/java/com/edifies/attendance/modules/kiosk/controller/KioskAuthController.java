package com.edifies.attendance.modules.kiosk.controller;

import com.edifies.attendance.modules.kiosk.dto.KioskAuthRequest;
import com.edifies.attendance.modules.kiosk.dto.KioskAuthResponse;
import com.edifies.attendance.modules.kiosk.dto.KioskResetRequest;
import com.edifies.attendance.modules.kiosk.service.KioskAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/kiosk")
public class KioskAuthController {

    private final KioskAuthService kioskAuthService;

    public KioskAuthController(KioskAuthService kioskAuthService) {
        this.kioskAuthService = kioskAuthService;
    }

    @PostMapping("/authenticate")
    public ResponseEntity<KioskAuthResponse> authenticate(@Valid @RequestBody KioskAuthRequest request) {
        KioskAuthResponse response = kioskAuthService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset(
            @RequestBody(required = false) KioskResetRequest request,
            @RequestHeader(value = "X-Kiosk-Session", required = false) String sessionHeader,
            @RequestHeader(value = "X-Kiosk-Token", required = false) String legacyTokenHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = null;
        if (request != null && request.token() != null && !request.token().isBlank()) {
            token = request.token().trim();
        } else if (sessionHeader != null && !sessionHeader.isBlank()) {
            token = sessionHeader.trim();
        } else if (legacyTokenHeader != null && !legacyTokenHeader.isBlank()) {
            token = legacyTokenHeader.trim();
        } else if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = authHeader.substring(7).trim();
        }

        if (token != null) {
            kioskAuthService.resetSession(token);
        }

        return ResponseEntity.ok(Map.of("message", "Kiosk session cleared successfully"));
    }
}
