package com.edifies.attendance.modules.kiosk.service;

import com.edifies.attendance.common.exception.ConflictException;
import com.edifies.attendance.common.exception.InvalidOperationException;
import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.modules.kiosk.dto.*;
import com.edifies.attendance.modules.kiosk.model.*;
import com.edifies.attendance.modules.kiosk.repository.KioskDeviceAuditLogRepository;
import com.edifies.attendance.modules.kiosk.repository.KioskDeviceRepository;
import com.edifies.attendance.modules.kiosk.repository.KioskPairingCodeRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class KioskDeviceService {

    private static final Logger log = LoggerFactory.getLogger(KioskDeviceService.class);

    private static final String CHAR_POOL = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // 32 unambiguous chars
    private static final long PAIRING_EXPIRY_SECONDS = 600; // 10 minutes
    private static final long DEVICE_COOKIE_MAX_AGE = 365L * 24 * 3600; // 1 year

    private final KioskDeviceRepository deviceRepository;
    private final KioskPairingCodeRepository pairingCodeRepository;
    private final KioskDeviceAuditLogRepository auditLogRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.kiosk.device-enforcement-enabled:true}")
    private boolean deviceEnforcementEnabled;

    public KioskDeviceService(KioskDeviceRepository deviceRepository,
                              KioskPairingCodeRepository pairingCodeRepository,
                              KioskDeviceAuditLogRepository auditLogRepository) {
        this.deviceRepository = deviceRepository;
        this.pairingCodeRepository = pairingCodeRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public boolean isDeviceEnforcementEnabled() {
        return deviceEnforcementEnabled;
    }

    /**
     * Administrator initiates a new device pairing workflow.
     * Generates an 8-character secure one-time pairing code and returns it ONCE.
     */
    public InitiatePairingResponse initiatePairing(InitiatePairingRequest request, String adminUsername, HttpServletRequest httpRequest) {
        String code = generatePairingCode();
        String codeHash = sha256(normalizeCode(code));
        Instant expiresAt = Instant.now().plusSeconds(PAIRING_EXPIRY_SECONDS);

        KioskPairingCode pairingCode = new KioskPairingCode(
                request.deviceName().trim(),
                codeHash,
                adminUsername,
                expiresAt
        );
        KioskPairingCode saved = pairingCodeRepository.save(pairingCode);

        // Audit log
        recordAudit(null, request.deviceName().trim(), DeviceAuditEventType.PAIRING_INITIATED,
                adminUsername, httpRequest, "Admin initiated pairing for device: " + request.deviceName().trim());

        log.info("Admin '{}' initiated pairing for device '{}', pairingId={}", adminUsername, request.deviceName(), saved.getId());

        return new InitiatePairingResponse(
                saved.getId(),
                saved.getDeviceName(),
                formatCodeWithHyphen(code),
                expiresAt,
                PAIRING_EXPIRY_SECONDS
        );
    }

    /**
     * Administrator cancels a pending device pairing.
     */
    public void cancelPairing(UUID pairingId, String adminUsername, HttpServletRequest httpRequest) {
        KioskPairingCode pairingCode = pairingCodeRepository.findById(pairingId)
                .orElseThrow(() -> new ResourceNotFoundException("Pairing authorization not found"));

        if (pairingCode.getStatus() != PairingCodeStatus.PENDING) {
            throw new InvalidOperationException("Pairing authorization is already " + pairingCode.getStatus());
        }

        pairingCode.setStatus(PairingCodeStatus.CANCELLED);
        pairingCode.setCancelledAt(Instant.now());
        pairingCodeRepository.save(pairingCode);

        recordAudit(null, pairingCode.getDeviceName(), DeviceAuditEventType.PAIRING_CANCELLED,
                adminUsername, httpRequest, "Admin cancelled pending pairing id: " + pairingId);

        log.info("Admin '{}' cancelled pairing id={}", adminUsername, pairingId);
    }

    /**
     * Kiosk client completes pairing using the one-time pairing code.
     * Validates expiration, single-use, and attempt limits, creates active KioskDevice,
     * generates a 256-bit bearer token, stores token verifier (hash), and sets HttpOnly cookie.
     */
    public CompletePairingResponse completePairing(CompletePairingRequest request,
                                                  HttpServletRequest httpRequest,
                                                  HttpServletResponse httpResponse) {
        String rawInputCode = request.pairingCode();
        if (rawInputCode == null || rawInputCode.isBlank()) {
            throw new BadCredentialsException("Pairing code is required.");
        }

        String normalized = normalizeCode(rawInputCode);
        String codeHash = sha256(normalized);

        Optional<KioskPairingCode> pairingCodeOpt = pairingCodeRepository.findByPairingCodeHash(codeHash);
        if (pairingCodeOpt.isEmpty()) {
            recordAudit(null, "UNKNOWN", DeviceAuditEventType.PAIRING_FAILED,
                    "KIOSK", httpRequest, "Invalid pairing code attempt from " + getClientIp(httpRequest));
            log.warn("Pairing failed: code not found from IP {}", getClientIp(httpRequest));
            throw new BadCredentialsException("Invalid or expired pairing code.");
        }

        KioskPairingCode pairingCode = pairingCodeOpt.get();

        // Check if cancelled or already used
        if (pairingCode.getStatus() == PairingCodeStatus.USED) {
            recordAudit(pairingCode.getPairedDeviceId(), pairingCode.getDeviceName(), DeviceAuditEventType.PAIRING_FAILED,
                    "KIOSK", httpRequest, "Attempt to reuse already consumed pairing code");
            log.warn("Pairing failed: code already used for device '{}'", pairingCode.getDeviceName());
            throw new BadCredentialsException("Pairing code has already been used.");
        }

        if (pairingCode.getStatus() == PairingCodeStatus.CANCELLED) {
            recordAudit(null, pairingCode.getDeviceName(), DeviceAuditEventType.PAIRING_FAILED,
                    "KIOSK", httpRequest, "Attempt to use cancelled pairing code");
            throw new BadCredentialsException("Pairing code has been cancelled by an administrator.");
        }

        // Check expiration
        if (pairingCode.isExpired()) {
            pairingCode.setStatus(PairingCodeStatus.EXPIRED);
            pairingCodeRepository.save(pairingCode);
            recordAudit(null, pairingCode.getDeviceName(), DeviceAuditEventType.PAIRING_FAILED,
                    "KIOSK", httpRequest, "Attempt to use expired pairing code");
            throw new BadCredentialsException("Pairing code has expired. Please request a new code from an administrator.");
        }

        // Check maximum failed attempts
        if (pairingCode.getFailedAttempts() >= pairingCode.getMaxAttempts()) {
            pairingCode.setStatus(PairingCodeStatus.EXPIRED);
            pairingCodeRepository.save(pairingCode);
            recordAudit(null, pairingCode.getDeviceName(), DeviceAuditEventType.PAIRING_FAILED,
                    "KIOSK", httpRequest, "Pairing code locked due to too many failed attempts");
            throw new BadCredentialsException("Pairing code has exceeded maximum verification attempts.");
        }

        // Code is valid! Generate bearer token & device identifier
        String rawDeviceToken = generateDeviceBearerToken();
        String tokenHash = sha256(rawDeviceToken);
        String deviceIdentifier = "DEV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        KioskDevice device = new KioskDevice(
                pairingCode.getDeviceName(),
                deviceIdentifier,
                tokenHash,
                pairingCode.getCreatedBy()
        );
        KioskDevice savedDevice = deviceRepository.save(device);

        // Mark pairing code as USED
        pairingCode.setStatus(PairingCodeStatus.USED);
        pairingCode.setUsedAt(Instant.now());
        pairingCode.setPairedDeviceId(savedDevice.getId());
        pairingCodeRepository.save(pairingCode);

        // Set HttpOnly device cookie if response is available
        if (httpResponse != null) {
            Cookie cookie = new Cookie("kiosk_device_token", rawDeviceToken);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge((int) DEVICE_COOKIE_MAX_AGE);
            // In modern servlet, set Secure and SameSite
            if (httpRequest != null && httpRequest.isSecure()) {
                cookie.setSecure(true);
            }
            cookie.setAttribute("SameSite", "Lax");
            httpResponse.addCookie(cookie);
        }

        recordAudit(savedDevice.getId(), savedDevice.getDeviceName(), DeviceAuditEventType.PAIRING_COMPLETED,
                "KIOSK", httpRequest, "Device paired successfully as " + savedDevice.getDeviceIdentifier());

        log.info("Device paired successfully: id={}, identifier='{}', name='{}'",
                savedDevice.getId(), savedDevice.getDeviceIdentifier(), savedDevice.getDeviceName());

        return new CompletePairingResponse(
                savedDevice.getId(),
                savedDevice.getDeviceIdentifier(),
                savedDevice.getDeviceName(),
                rawDeviceToken,
                savedDevice.getStatus().name(),
                savedDevice.getPairedAt()
        );
    }

    /**
     * Administrator revokes an active device.
     * The device token hash is immediately cleared and status set to REVOKED.
     */
    public KioskDeviceResponse revokeDevice(UUID deviceId, RevokeDeviceRequest request, String adminUsername, HttpServletRequest httpRequest) {
        KioskDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        if (device.getStatus() == KioskDeviceStatus.REVOKED) {
            throw new ConflictException("Device is already revoked.");
        }

        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason().trim()
                : "Revoked by administrator";

        device.setStatus(KioskDeviceStatus.REVOKED);
        device.setRevokedAt(Instant.now());
        device.setRevokedBy(adminUsername);
        device.setRevocationReason(reason);
        device.setDeviceTokenHash(null); // Invalidate token verifier immediately

        KioskDevice saved = deviceRepository.save(device);

        recordAudit(saved.getId(), saved.getDeviceName(), DeviceAuditEventType.DEVICE_REVOKED,
                adminUsername, httpRequest, "Device revoked by admin. Reason: " + reason);

        log.warn("Device revoked by admin '{}': id={}, name='{}', reason='{}'",
                adminUsername, saved.getId(), saved.getDeviceName(), reason);

        return mapToResponse(saved);
    }

    /**
     * Lists all registered devices for administrator review.
     */
    @Transactional(readOnly = true)
    public List<KioskDeviceResponse> getAllDevices() {
        return deviceRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Verifies an incoming device token against active registered devices.
     */
    @Transactional(readOnly = true)
    public Optional<KioskDevice> verifyDeviceToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        String tokenHash = sha256(rawToken.trim());
        Optional<KioskDevice> deviceOpt = deviceRepository.findByDeviceTokenHash(tokenHash);

        if (deviceOpt.isPresent()) {
            KioskDevice device = deviceOpt.get();
            if (device.getStatus() == KioskDeviceStatus.ACTIVE) {
                return Optional.of(device);
            }
        }

        return Optional.empty();
    }

    /**
     * Record device activity, throttled to at most once every 5 minutes to prevent DB churn.
     */
    public void recordDeviceActivity(KioskDevice device, HttpServletRequest request) {
        if (device == null || device.getId() == null) return;

        Instant now = Instant.now();
        if (device.getLastSeenAt() == null || Duration.between(device.getLastSeenAt(), now).toMinutes() >= 5) {
            device.setLastSeenAt(now);
            deviceRepository.save(device);
        }
    }

    /**
     * Retrieve recent device audit logs.
     */
    @Transactional(readOnly = true)
    public List<DeviceAuditLogResponse> getDeviceAuditLogs() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(log -> new DeviceAuditLogResponse(
                        log.getId(),
                        log.getDeviceId(),
                        log.getDeviceName(),
                        log.getEventType().name(),
                        log.getActor(),
                        log.getIpAddress(),
                        log.getUserAgent(),
                        log.getDetails(),
                        log.getCreatedAt()
                ))
                .toList();
    }

    /**
     * Record an audit event into kiosk_device_audit_logs.
     */
    public void recordAudit(UUID deviceId, String deviceName, DeviceAuditEventType eventType,
                            String actor, HttpServletRequest request, String details) {
        try {
            String ip = getClientIp(request);
            String userAgent = request != null ? request.getHeader("User-Agent") : null;
            KioskDeviceAuditLog auditLog = new KioskDeviceAuditLog(
                    deviceId, deviceName, eventType, actor, ip, userAgent, details
            );
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to persist device audit log", e);
        }
    }

    private KioskDeviceResponse mapToResponse(KioskDevice device) {
        return new KioskDeviceResponse(
                device.getId(),
                device.getDeviceIdentifier(),
                device.getDeviceName(),
                device.getStatus().name(),
                device.getPairedAt(),
                device.getLastSeenAt(),
                device.getRevokedAt(),
                device.getRevocationReason(),
                device.getCreatedBy(),
                device.getRevokedBy(),
                device.getCreatedAt()
        );
    }

    private String generatePairingCode() {
        char[] code = new char[8];
        for (int i = 0; i < 8; i++) {
            code[i] = CHAR_POOL.charAt(secureRandom.nextInt(CHAR_POOL.length()));
        }
        return new String(code);
    }

    private String formatCodeWithHyphen(String code) {
        if (code != null && code.length() == 8 && !code.contains("-")) {
            return code.substring(0, 4) + "-" + code.substring(4);
        }
        return code;
    }

    private String normalizeCode(String code) {
        return code.replace("-", "").replace(" ", "").trim().toUpperCase();
    }

    private String generateDeviceBearerToken() {
        byte[] randomBytes = new byte[32]; // 256 bits of entropy
        secureRandom.nextBytes(randomBytes);
        return "kd_live_" + HexFormat.of().formatHex(randomBytes);
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "UNKNOWN";
    }
}
