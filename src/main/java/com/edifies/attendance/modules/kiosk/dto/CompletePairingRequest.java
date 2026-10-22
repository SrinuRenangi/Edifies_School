package com.edifies.attendance.modules.kiosk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePairingRequest(
        @NotBlank(message = "Pairing code is required")
        @Size(min = 4, max = 32, message = "Invalid pairing code length")
        String pairingCode,
        String clientInfo
) {}
