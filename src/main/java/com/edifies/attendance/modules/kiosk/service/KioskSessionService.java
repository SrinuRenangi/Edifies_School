package com.edifies.attendance.modules.kiosk.service;

import com.edifies.attendance.modules.employee.model.Employee;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KioskSessionService {

    public static final long SESSION_DURATION_SECONDS = 60; // 60s timeout

    public record KioskSession(
            String token,
            UUID employeeUuid,
            String employeeId,
            String fullName,
            String department,
            Instant expiresAt
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final ConcurrentHashMap<String, KioskSession> activeSessions = new ConcurrentHashMap<>();

    public KioskSession createSession(Employee employee) {
        cleanExpiredSessions();
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(SESSION_DURATION_SECONDS);
        KioskSession session = new KioskSession(
                token,
                employee.getId(),
                employee.getEmployeeId(),
                employee.getFullName(),
                employee.getDepartment(),
                expiresAt
        );
        activeSessions.put(token, session);
        return session;
    }

    public KioskSession validateSession(String token) {
        if (token == null || token.isBlank()) return null;
        KioskSession session = activeSessions.get(token.trim());
        if (session == null) return null;
        if (session.isExpired()) {
            activeSessions.remove(token.trim());
            return null;
        }
        return session;
    }

    public void invalidateSession(String token) {
        if (token != null && !token.isBlank()) {
            activeSessions.remove(token.trim());
        }
    }

    private void cleanExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
