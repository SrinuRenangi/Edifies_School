package com.edifies.attendance.modules.attendance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttendancePolicyServiceTest {

    private final AttendancePolicyService policyService = new AttendancePolicyService("Asia/Kolkata", 4);
    private final ZoneId ist = ZoneId.of("Asia/Kolkata");

    @Test
    @DisplayName("Work Date: Time at 02:00 AM IST belongs to previous calendar day")
    void shouldMapPreCutoffTimeToPreviousWorkDate() {
        // 2026-10-22 02:00:00 IST
        ZonedDateTime zdt = ZonedDateTime.of(2026, 10, 22, 2, 0, 0, 0, ist);
        Instant instant = zdt.toInstant();

        LocalDate workDate = policyService.calculateWorkDate(instant);
        assertEquals(LocalDate.of(2026, 10, 21), workDate);
    }

    @Test
    @DisplayName("Work Date: Time at 03:59:59 IST belongs to previous calendar day")
    void shouldMapBoundaryPreCutoffToPreviousWorkDate() {
        // 2026-10-22 03:59:59 IST
        ZonedDateTime zdt = ZonedDateTime.of(2026, 10, 22, 3, 59, 59, 999999999, ist);
        Instant instant = zdt.toInstant();

        LocalDate workDate = policyService.calculateWorkDate(instant);
        assertEquals(LocalDate.of(2026, 10, 21), workDate);
    }

    @Test
    @DisplayName("Work Date: Time exactly at 04:00 AM IST belongs to current calendar day")
    void shouldMapCutoffExactTimeToCurrentWorkDate() {
        // 2026-10-22 04:00:00 IST
        ZonedDateTime zdt = ZonedDateTime.of(2026, 10, 22, 4, 0, 0, 0, ist);
        Instant instant = zdt.toInstant();

        LocalDate workDate = policyService.calculateWorkDate(instant);
        assertEquals(LocalDate.of(2026, 10, 22), workDate);
    }

    @Test
    @DisplayName("Work Date: Normal daytime at 09:30 AM IST belongs to current calendar day")
    void shouldMapDaytimeToCurrentWorkDate() {
        // 2026-10-22 09:30:00 IST
        ZonedDateTime zdt = ZonedDateTime.of(2026, 10, 22, 9, 30, 0, 0, ist);
        Instant instant = zdt.toInstant();

        LocalDate workDate = policyService.calculateWorkDate(instant);
        assertEquals(LocalDate.of(2026, 10, 22), workDate);
    }

    @Test
    @DisplayName("Work Date: Late evening at 23:50 IST belongs to current calendar day")
    void shouldMapLateEveningToCurrentWorkDate() {
        // 2026-10-22 23:50:00 IST
        ZonedDateTime zdt = ZonedDateTime.of(2026, 10, 22, 23, 50, 0, 0, ist);
        Instant instant = zdt.toInstant();

        LocalDate workDate = policyService.calculateWorkDate(instant);
        assertEquals(LocalDate.of(2026, 10, 22), workDate);
    }
}
