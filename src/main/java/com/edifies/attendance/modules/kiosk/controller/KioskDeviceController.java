package com.edifies.attendance.modules.kiosk.controller;

import com.edifies.attendance.modules.kiosk.dto.CompletePairingRequest;
import com.edifies.attendance.modules.kiosk.dto.CompletePairingResponse;
import com.edifies.attendance.modules.kiosk.dto.DeviceStatusResponse;
import com.edifies.attendance.modules.kiosk.model.KioskDevice;
import com.edifies.attendance.modules.kiosk.service.KioskDeviceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kiosk/devices")
public class KioskDeviceController {

    private final KioskDeviceService kioskDeviceService;

    public KioskDeviceController(KioskDeviceService kioskDeviceService) {
        this.kioskDeviceService = kioskDeviceService;
    }

    /**
     * Complete device pairing using the one-time code provided by the administrator.
     * Open endpoint (device not yet paired).
     */
    @PostMapping("/pair")
    public ResponseEntity<CompletePairingResponse> pairDevice(
            @Valid @RequestBody CompletePairingRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        CompletePairingResponse response = kioskDeviceService.completePairing(request, httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    /**
     * Check registration status of the current device.
     * Protected by KioskDeviceAuthenticationFilter.
     */
    @GetMapping("/status")
    public ResponseEntity<DeviceStatusResponse> getDeviceStatus(HttpServletRequest request) {
        KioskDevice device = (KioskDevice) request.getAttribute("kioskDevice");
        if (device == null) {
            return ResponseEntity.ok(new DeviceStatusResponse(false, null, null, null, "UNREGISTERED"));
        }
        return ResponseEntity.ok(new DeviceStatusResponse(
                true,
                device.getId(),
                device.getDeviceIdentifier(),
                device.getDeviceName(),
                device.getStatus().name()
        ));
    }
}
