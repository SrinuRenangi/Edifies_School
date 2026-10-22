package com.edifies.attendance.modules.attendance.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class AttendancePolicyService {

    private final ZoneId zoneId;
    private final int cutoffHour;

    public AttendancePolicyService(
            @Value("${attendance.timezone:Asia/Kolkata}") String timezone,
            @Value("${attendance.cutoff-hour:4}") int cutoffHour) {
        this.zoneId = ZoneId.of(timezone);
        this.cutoffHour = cutoffHour;
    }

    /**
     * Calculates the business work-date for a given UTC timestamp based on the configured
     * business timezone (Asia/Kolkata) and the 04:00 AM daily cutoff policy.
     *
     * Example:
     * - 2026-10-22 02:30 IST is before 04:00 AM -> belongs to 2026-10-21 work date.
     * - 2026-10-22 04:00 IST is at/after 04:00 AM -> belongs to 2026-10-22 work date.
     */
    public LocalDate calculateWorkDate(Instant instant) {
        ZonedDateTime zdt = instant.atZone(zoneId);
        if (zdt.toLocalTime().isBefore(LocalTime.of(cutoffHour, 0))) {
            return zdt.toLocalDate().minusDays(1);
        }
        return zdt.toLocalDate();
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public int getCutoffHour() {
        return cutoffHour;
    }
}
