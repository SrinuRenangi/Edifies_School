package com.edifies.attendance.modules.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCK_TIME_DURATION_MILLIS = 15 * 60 * 1000; // 15 minutes

    private final ConcurrentHashMap<String, AttemptData> attemptsCache = new ConcurrentHashMap<>();

    private record AttemptData(int attempts, Instant lastAttempt) {}

    public void recordFailure(String key) {
        if (key == null || key.isBlank()) return;
        String normalizedKey = key.trim().toLowerCase();

        attemptsCache.compute(normalizedKey, (k, current) -> {
            Instant now = Instant.now();
            if (current == null) {
                return new AttemptData(1, now);
            }
            if (now.toEpochMilli() - current.lastAttempt().toEpochMilli() > LOCK_TIME_DURATION_MILLIS) {
                return new AttemptData(1, now);
            }
            int newCount = current.attempts() + 1;
            if (newCount >= MAX_ATTEMPTS) {
                log.warn("Security alert: Lockout threshold reached for key='{}' (attempts={})", k, newCount);
            }
            return new AttemptData(newCount, now);
        });
    }

    public void recordSuccess(String key) {
        if (key == null || key.isBlank()) return;
        attemptsCache.remove(key.trim().toLowerCase());
    }

    public boolean isBlocked(String key) {
        if (key == null || key.isBlank()) return false;
        AttemptData data = attemptsCache.get(key.trim().toLowerCase());
        if (data == null) {
            return false;
        }

        Instant now = Instant.now();
        if (now.toEpochMilli() - data.lastAttempt().toEpochMilli() > LOCK_TIME_DURATION_MILLIS) {
            attemptsCache.remove(key.trim().toLowerCase());
            return false;
        }

        return data.attempts() >= MAX_ATTEMPTS;
    }

    public long getRemainingLockoutSeconds(String key) {
        if (key == null || key.isBlank()) return 0;
        AttemptData data = attemptsCache.get(key.trim().toLowerCase());
        if (data == null) return 0;

        long elapsed = Instant.now().toEpochMilli() - data.lastAttempt().toEpochMilli();
        if (elapsed > LOCK_TIME_DURATION_MILLIS) {
            attemptsCache.remove(key.trim().toLowerCase());
            return 0;
        }
        return Math.max(0, (LOCK_TIME_DURATION_MILLIS - elapsed) / 1000);
    }

    public void resetLockout(String key) {
        recordSuccess(key);
    }
}
