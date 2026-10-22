package com.edifies.attendance.modules.kiosk.controller;

import com.edifies.attendance.modules.kiosk.dto.*;
import com.edifies.attendance.modules.kiosk.service.KioskDeviceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/devices")
@PreAuthorize("hasRole('ADMIN')")
public class AdminKioskDeviceController {

    private final KioskDeviceService kioskDeviceService;

    public AdminKioskDeviceController(KioskDeviceService kioskDeviceService) {
        this.kioskDeviceService = kioskDeviceService;
    }

    @GetMapping
    public ResponseEntity<List<KioskDeviceResponse>> getAllDevices() {
        return ResponseEntity.ok(kioskDeviceService.getAllDevices());
    }

    @PostMapping("/pair/initiate")
    public ResponseEntity<InitiatePairingResponse> initiatePairing(
            @Valid @RequestBody InitiatePairingRequest request,
            Principal principal,
            HttpServletRequest httpRequest) {
        String adminUsername = principal != null ? principal.getName() : "admin";
        InitiatePairingResponse response = kioskDeviceService.initiatePairing(request, adminUsername, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pair/{pairingId}/cancel")
    public ResponseEntity<Map<String, String>> cancelPairing(
            @PathVariable UUID pairingId,
            Principal principal,
            HttpServletRequest httpRequest) {
        String adminUsername = principal != null ? principal.getName() : "admin";
        kioskDeviceService.cancelPairing(pairingId, adminUsername, httpRequest);
        return ResponseEntity.ok(Map.of("message", "Pairing authorization cancelled successfully."));
    }

    @PostMapping("/{deviceId}/revoke")
    public ResponseEntity<KioskDeviceResponse> revokeDevice(
            @PathVariable UUID deviceId,
            @RequestBody(required = false) RevokeDeviceRequest request,
            Principal principal,
            HttpServletRequest httpRequest) {
        String adminUsername = principal != null ? principal.getName() : "admin";
        KioskDeviceResponse response = kioskDeviceService.revokeDevice(deviceId, request, adminUsername, httpRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/audit")
    public ResponseEntity<List<DeviceAuditLogResponse>> getDeviceAuditLogs() {
        return ResponseEntity.ok(kioskDeviceService.getDeviceAuditLogs());
    }
}
