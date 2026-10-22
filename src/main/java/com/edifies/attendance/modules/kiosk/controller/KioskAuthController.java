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
@CrossOrigin(origins = "*")
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
            @RequestHeader(value = "X-Kiosk-Token", required = false) String headerToken) {
        String token = (request != null && request.token() != null && !request.token().isBlank())
                ? request.token()
                : headerToken;

        if (token != null) {
            kioskAuthService.resetSession(token);
        }

        return ResponseEntity.ok(Map.of("message", "Kiosk session cleared successfully"));
    }
}
