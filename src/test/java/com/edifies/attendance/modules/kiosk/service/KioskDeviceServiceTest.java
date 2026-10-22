package com.edifies.attendance.modules.kiosk.service;

import com.edifies.attendance.common.exception.ConflictException;
import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.modules.kiosk.dto.*;
import com.edifies.attendance.modules.kiosk.model.*;
import com.edifies.attendance.modules.kiosk.repository.KioskDeviceAuditLogRepository;
import com.edifies.attendance.modules.kiosk.repository.KioskDeviceRepository;
import com.edifies.attendance.modules.kiosk.repository.KioskPairingCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KioskDeviceServiceTest {

    @Mock
    private KioskDeviceRepository deviceRepository;

    @Mock
    private KioskPairingCodeRepository pairingCodeRepository;

    @Mock
    private KioskDeviceAuditLogRepository auditLogRepository;

    private KioskDeviceService kioskDeviceService;

    @BeforeEach
    void setUp() {
        kioskDeviceService = new KioskDeviceService(deviceRepository, pairingCodeRepository, auditLogRepository);
    }

    @Test
    @DisplayName("Initiate pairing creates short-lived pairing code with hash")
    void shouldInitiatePairingSuccessfully() {
        InitiatePairingRequest request = new InitiatePairingRequest("Front Desk Mac");

        when(pairingCodeRepository.save(any(KioskPairingCode.class))).thenAnswer(invocation -> {
            KioskPairingCode code = invocation.getArgument(0);
            code.setId(UUID.randomUUID());
            return code;
        });

        InitiatePairingResponse response = kioskDeviceService.initiatePairing(request, "admin", null);

        assertNotNull(response);
        assertNotNull(response.pairingId());
        assertEquals("Front Desk Mac", response.deviceName());
        assertNotNull(response.pairingCode());
        assertTrue(response.pairingCode().contains("-"));
        assertEquals(600, response.expiresInSeconds());

        // Verify SHA-256 hash was stored, not plaintext
        ArgumentCaptor<KioskPairingCode> captor = ArgumentCaptor.forClass(KioskPairingCode.class);
        verify(pairingCodeRepository).save(captor.capture());
        KioskPairingCode savedCode = captor.getValue();
        assertNotEquals(response.pairingCode(), savedCode.getPairingCodeHash());
        assertEquals(64, savedCode.getPairingCodeHash().length()); // SHA-256 hex length
    }

    @Test
    @DisplayName("Complete pairing succeeds with valid code and generates bearer token")
    void shouldCompletePairingSuccessfully() {
        String rawCode = "7XKP-M29B";
        String normalized = "7XKPM29B";
        String hash = KioskDeviceService.sha256(normalized);

        KioskPairingCode pairingCode = new KioskPairingCode("Front Desk Mac", hash, "admin", Instant.now().plusSeconds(600));
        pairingCode.setId(UUID.randomUUID());

        when(pairingCodeRepository.findByPairingCodeHash(hash)).thenReturn(Optional.of(pairingCode));
        when(deviceRepository.save(any(KioskDevice.class))).thenAnswer(invocation -> {
            KioskDevice d = invocation.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        CompletePairingRequest request = new CompletePairingRequest(rawCode, "Mozilla/5.0");
        CompletePairingResponse response = kioskDeviceService.completePairing(request, null, null);

        assertNotNull(response);
        assertNotNull(response.deviceId());
        assertEquals("Front Desk Mac", response.deviceName());
        assertTrue(response.deviceToken().startsWith("kd_live_"));
        assertEquals("ACTIVE", response.status());

        // Verify pairing code is marked as USED
        assertEquals(PairingCodeStatus.USED, pairingCode.getStatus());
        assertNotNull(pairingCode.getUsedAt());
        verify(pairingCodeRepository).save(pairingCode);
    }

    @Test
    @DisplayName("Complete pairing rejects non-existent pairing code")
    void shouldRejectNonExistentPairingCode() {
        String hash = KioskDeviceService.sha256("UNKNOWN1");
        when(pairingCodeRepository.findByPairingCodeHash(hash)).thenReturn(Optional.empty());

        CompletePairingRequest request = new CompletePairingRequest("UNKNOWN1", "client");

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                kioskDeviceService.completePairing(request, null, null));
        assertEquals("Invalid or expired pairing code.", ex.getMessage());
    }

    @Test
    @DisplayName("Complete pairing rejects already used code")
    void shouldRejectReusedPairingCode() {
        String rawCode = "USED-CODE";
        String hash = KioskDeviceService.sha256("USEDCODE");

        KioskPairingCode pairingCode = new KioskPairingCode("Front Desk", hash, "admin", Instant.now().plusSeconds(600));
        pairingCode.setStatus(PairingCodeStatus.USED);

        when(pairingCodeRepository.findByPairingCodeHash(hash)).thenReturn(Optional.of(pairingCode));

        CompletePairingRequest request = new CompletePairingRequest(rawCode, null);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                kioskDeviceService.completePairing(request, null, null));
        assertTrue(ex.getMessage().contains("already been used"));
    }

    @Test
    @DisplayName("Complete pairing rejects expired code")
    void shouldRejectExpiredPairingCode() {
        String rawCode = "EXPI-RE01";
        String hash = KioskDeviceService.sha256("EXPIRE01");

        KioskPairingCode pairingCode = new KioskPairingCode("Front Desk", hash, "admin", Instant.now().minusSeconds(10));

        when(pairingCodeRepository.findByPairingCodeHash(hash)).thenReturn(Optional.of(pairingCode));

        CompletePairingRequest request = new CompletePairingRequest(rawCode, null);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                kioskDeviceService.completePairing(request, null, null));
        assertTrue(ex.getMessage().contains("expired"));
        assertEquals(PairingCodeStatus.EXPIRED, pairingCode.getStatus());
    }

    @Test
    @DisplayName("Revoke device invalidates token hash and sets status to REVOKED")
    void shouldRevokeDeviceSuccessfully() {
        UUID deviceId = UUID.randomUUID();
        KioskDevice device = new KioskDevice("Front Desk", "DEV-01", "some_token_hash", "admin");
        device.setId(deviceId);

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(KioskDevice.class))).thenAnswer(i -> i.getArgument(0));

        RevokeDeviceRequest request = new RevokeDeviceRequest("Workstation lost");
        KioskDeviceResponse response = kioskDeviceService.revokeDevice(deviceId, request, "admin_user", null);

        assertNotNull(response);
        assertEquals("REVOKED", response.status());
        assertNull(device.getDeviceTokenHash()); // Immediate token verifier invalidation
        assertNotNull(device.getRevokedAt());
        assertEquals("admin_user", device.getRevokedBy());
        assertEquals("Workstation lost", device.getRevocationReason());
    }

    @Test
    @DisplayName("Revoking already revoked device throws ConflictException")
    void shouldThrowConflictWhenRevokingAlreadyRevokedDevice() {
        UUID deviceId = UUID.randomUUID();
        KioskDevice device = new KioskDevice("Front Desk", "DEV-01", null, "admin");
        device.setId(deviceId);
        device.setStatus(KioskDeviceStatus.REVOKED);

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        assertThrows(ConflictException.class, () ->
                kioskDeviceService.revokeDevice(deviceId, new RevokeDeviceRequest("test"), "admin", null));
    }

    @Test
    @DisplayName("Verify device token returns active device and rejects revoked device")
    void shouldVerifyDeviceTokenCorrectly() {
        String rawToken = "kd_live_abcdef123456";
        String tokenHash = KioskDeviceService.sha256(rawToken);

        KioskDevice activeDevice = new KioskDevice("Reception", "DEV-02", tokenHash, "admin");
        when(deviceRepository.findByDeviceTokenHash(tokenHash)).thenReturn(Optional.of(activeDevice));

        // 1. Active device is verified
        Optional<KioskDevice> verified = kioskDeviceService.verifyDeviceToken(rawToken);
        assertTrue(verified.isPresent());
        assertEquals("Reception", verified.get().getDeviceName());

        // 2. Revoked device is rejected
        activeDevice.setStatus(KioskDeviceStatus.REVOKED);
        Optional<KioskDevice> revokedCheck = kioskDeviceService.verifyDeviceToken(rawToken);
        assertTrue(revokedCheck.isEmpty());

        // 3. Unknown token is rejected
        String unknownToken = "kd_live_unknown999";
        String unknownHash = KioskDeviceService.sha256(unknownToken);
        when(deviceRepository.findByDeviceTokenHash(unknownHash)).thenReturn(Optional.empty());
        Optional<KioskDevice> unknownCheck = kioskDeviceService.verifyDeviceToken(unknownToken);
        assertTrue(unknownCheck.isEmpty());
    }
}
