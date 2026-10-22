package com.edifies.attendance.modules.attendance;

import com.edifies.attendance.modules.attendance.dto.correction.AttendanceCorrectionRequest;
import com.edifies.attendance.modules.attendance.dto.correction.ManualAttendanceRequest;
import com.edifies.attendance.modules.attendance.model.AttendanceCorrection;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.model.CorrectionActionType;
import com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAttendanceCorrectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AttendanceCorrectionRepository correctionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Employee employee;
    private AttendanceRecord testRecord;

    @BeforeEach
    void setUp() {
        correctionRepository.deleteAll();
        attendanceRepository.deleteAll();
        employeeRepository.deleteAll();

        String hashedPin = passwordEncoder.encode("1234");
        employee = new Employee("EMP-CORR-01", "Charles Darwin", "Biology", hashedPin);
        employee = employeeRepository.saveAndFlush(employee);

        Instant checkIn = Instant.parse("2026-10-18T04:00:00Z");
        testRecord = new AttendanceRecord(employee, LocalDate.of(2026, 10, 18), checkIn);
        testRecord.setStatus(AttendanceStatus.CHECKED_IN);
        testRecord = attendanceRepository.saveAndFlush(testRecord);
    }

    @AfterEach
    void tearDown() {
        correctionRepository.deleteAll();
        attendanceRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "admin_supervisor", roles = {"ADMIN"})
    @DisplayName("Admin can correct check-in and check-out times, atomically persisting audit event")
    void shouldAllowAdminToCorrectPunches() throws Exception {
        Instant correctedIn = Instant.parse("2026-10-18T03:30:00Z");
        Instant correctedOut = Instant.parse("2026-10-18T12:00:00Z");

        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                correctedIn,
                correctedOut,
                AttendanceStatus.CHECKED_OUT,
                CorrectionActionType.CORRECT_BOTH,
                "Corrected clock-in time and added verified departure time.",
                testRecord.getVersion()
        );

        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", testRecord.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.correctionIndicator").value("Corrected"))
                .andExpect(jsonPath("$.checkInTime").value("2026-10-18T03:30:00Z"))
                .andExpect(jsonPath("$.checkOutTime").value("2026-10-18T12:00:00Z"));

        // Verify database state
        AttendanceRecord updated = attendanceRepository.findById(testRecord.getId()).orElseThrow();
        assertThat(updated.isCorrected()).isTrue();
        assertThat(updated.getStatus()).isEqualTo(AttendanceStatus.CHECKED_OUT);
        assertThat(updated.getCheckInTime()).isEqualTo(correctedIn);
        assertThat(updated.getCheckOutTime()).isEqualTo(correctedOut);

        // Verify audit event
        List<AttendanceCorrection> audits = correctionRepository.findByAttendanceRecordIdOrderByCreatedAtDesc(testRecord.getId());
        assertThat(audits).hasSize(1);
        AttendanceCorrection audit = audits.get(0);
        assertThat(audit.getPerformedBy()).isEqualTo("admin_supervisor");
        assertThat(audit.getActionType()).isEqualTo(CorrectionActionType.CORRECT_BOTH);
        assertThat(audit.getReason()).isEqualTo("Corrected clock-in time and added verified departure time.");
        assertThat(audit.getPreviousStatus()).isEqualTo(AttendanceStatus.CHECKED_IN);
        assertThat(audit.getCorrectedStatus()).isEqualTo(AttendanceStatus.CHECKED_OUT);
    }

    @Test
    @WithMockUser(username = "admin_supervisor", roles = {"ADMIN"})
    @DisplayName("Admin can resolve a missed checkout")
    void shouldAllowAdminToResolveMissedCheckout() throws Exception {
        Instant verifiedOut = Instant.parse("2026-10-18T11:45:00Z");

        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                null,
                verifiedOut,
                AttendanceStatus.CHECKED_OUT,
                CorrectionActionType.RESOLVE_MISSED_CHECKOUT,
                "Missed checkout resolved per supervisor confirmation.",
                testRecord.getVersion()
        );

        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", testRecord.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.isIncomplete").value(false))
                .andExpect(jsonPath("$.correctionIndicator").value("Corrected"));

        List<AttendanceCorrection> audits = correctionRepository.findByAttendanceRecordIdOrderByCreatedAtDesc(testRecord.getId());
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getActionType()).isEqualTo(CorrectionActionType.RESOLVE_MISSED_CHECKOUT);
    }

    @Test
    @WithMockUser(username = "admin_supervisor", roles = {"ADMIN"})
    @DisplayName("Admin can void an erroneous duplicate attendance record")
    void shouldAllowAdminToVoidRecord() throws Exception {
        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                null,
                null,
                AttendanceStatus.VOIDED,
                CorrectionActionType.VOID_RECORD,
                "Voiding duplicate accidental test punch.",
                testRecord.getVersion()
        );

        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", testRecord.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));

        AttendanceRecord updated = attendanceRepository.findById(testRecord.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AttendanceStatus.VOIDED);

        List<AttendanceCorrection> audits = correctionRepository.findByAttendanceRecordIdOrderByCreatedAtDesc(testRecord.getId());
        assertThat(audits.get(0).getActionType()).isEqualTo(CorrectionActionType.VOID_RECORD);
    }

    @Test
    @WithMockUser(username = "admin_supervisor", roles = {"ADMIN"})
    @DisplayName("Admin can create manual attendance entry with audit log")
    void shouldAllowAdminToCreateManualEntry() throws Exception {
        LocalDate manualDate = LocalDate.of(2026, 10, 12);
        Instant checkIn = Instant.parse("2026-10-12T03:30:00Z");
        Instant checkOut = Instant.parse("2026-10-12T11:30:00Z");

        ManualAttendanceRequest request = new ManualAttendanceRequest(
                employee.getId(),
                manualDate,
                checkIn,
                checkOut,
                "Manual attendance entry approved by principal."
        );

        mockMvc.perform(post("/api/admin/attendance/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correctionIndicator").value("Manual Entry"))
                .andExpect(jsonPath("$.workDate").value("2026-10-12"))
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"));

        // Verify audit event
        List<AttendanceCorrection> audits = correctionRepository.findAll();
        assertThat(audits).anyMatch(a -> a.getActionType() == CorrectionActionType.MANUAL_ENTRY
                && a.getReason().contains("principal"));
    }

    @Test
    @WithMockUser(username = "admin_supervisor", roles = {"ADMIN"})
    @DisplayName("Audit history endpoint returns chronological entries for a record")
    void shouldReturnChronologicalAuditHistory() throws Exception {
        // Perform 2 consecutive corrections
        AttendanceCorrectionRequest c1 = new AttendanceCorrectionRequest(
                Instant.parse("2026-10-18T03:45:00Z"),
                null,
                AttendanceStatus.CHECKED_IN,
                CorrectionActionType.CORRECT_CHECK_IN,
                "Correction 1: Fixed check-in time.",
                testRecord.getVersion()
        );
        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", testRecord.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(c1))).andExpect(status().isOk());

        AttendanceRecord r2 = attendanceRepository.findById(testRecord.getId()).orElseThrow();

        AttendanceCorrectionRequest c2 = new AttendanceCorrectionRequest(
                null,
                Instant.parse("2026-10-18T12:00:00Z"),
                AttendanceStatus.CHECKED_OUT,
                CorrectionActionType.RESOLVE_MISSED_CHECKOUT,
                "Correction 2: Resolved missed checkout.",
                r2.getVersion()
        );
        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", testRecord.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(c2))).andExpect(status().isOk());

        // Fetch audit history
        mockMvc.perform(get("/api/admin/attendance/{id}/audit-history", testRecord.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].reason").value("Correction 2: Resolved missed checkout."))
                .andExpect(jsonPath("$.content[1].reason").value("Correction 1: Fixed check-in time."));
    }

    @Test
    @DisplayName("Anonymous users cannot correct attendance (401 Unauthorized)")
    void shouldRejectAnonymousUsers() throws Exception {
        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                Instant.now(), null, AttendanceStatus.CHECKED_IN, null, "Reason.", 1L
        );
        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", testRecord.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "kiosk_device", roles = {"KIOSK"})
    @DisplayName("Employee kiosk sessions cannot access correction endpoints (403 Forbidden)")
    void shouldRejectKioskRole() throws Exception {
        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                Instant.now(), null, AttendanceStatus.CHECKED_IN, null, "Reason.", 1L
        );
        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", testRecord.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_supervisor", roles = {"ADMIN"})
    @DisplayName("Should reject blank correction reason with 400 Bad Request")
    void shouldRejectBlankReason() throws Exception {
        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                Instant.now(), null, AttendanceStatus.CHECKED_IN, null, "   ", testRecord.getVersion()
        );
        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", testRecord.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin_supervisor", roles = {"ADMIN"})
    @DisplayName("Should detect optimistic locking version mismatch and return 409 Conflict")
    void shouldReturn409OnVersionMismatch() throws Exception {
        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                Instant.now(), null, AttendanceStatus.CHECKED_IN, null, "Valid reason.", 999L // Wrong version
        );
        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", testRecord.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("version conflict")));
    }

    @Test
    @WithMockUser(username = "admin_supervisor", roles = {"ADMIN"})
    @DisplayName("Should return 404 when correcting a non-existent attendance ID")
    void shouldHandleInvalidAttendanceIdSafely() throws Exception {
        UUID randomId = UUID.randomUUID();
        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                Instant.now(), null, AttendanceStatus.CHECKED_IN, null, "Valid reason.", 0L
        );
        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", randomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("not found")));
    }

    @Test
    @WithMockUser(username = "admin_supervisor", roles = {"ADMIN"})
    @DisplayName("Should allow correcting historical records of inactive employees with full audit trail")
    void shouldAllowCorrectingHistoricalRecordOfInactiveEmployee() throws Exception {
        Employee inactiveEmp = new Employee("EMP-INACTIVE", "Ada Lovelace", "Mathematics", passwordEncoder.encode("1234"));
        inactiveEmp.setStatus(EmployeeStatus.INACTIVE);
        inactiveEmp = employeeRepository.saveAndFlush(inactiveEmp);

        AttendanceRecord pastRecord = new AttendanceRecord(inactiveEmp, LocalDate.of(2026, 10, 10), Instant.parse("2026-10-10T04:00:00Z"));
        pastRecord.setStatus(AttendanceStatus.AUTO_CLOSED);
        pastRecord = attendanceRepository.saveAndFlush(pastRecord);

        Instant verifiedOut = Instant.parse("2026-10-10T12:00:00Z");
        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                null,
                verifiedOut,
                AttendanceStatus.CHECKED_OUT,
                CorrectionActionType.RESOLVE_MISSED_CHECKOUT,
                "Historical correction for inactive staff verified from paper register.",
                pastRecord.getVersion()
        );

        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", pastRecord.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.correctionIndicator").value("Corrected"));

        // Audit check
        List<AttendanceCorrection> audits = correctionRepository.findByAttendanceRecordIdOrderByCreatedAtDesc(pastRecord.getId());
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getPerformedBy()).isEqualTo("admin_supervisor");
        assertThat(audits.get(0).getEmployee().getEmployeeId()).isEqualTo("EMP-INACTIVE");
    }

    @Test
    @WithMockUser(username = "admin_supervisor", roles = {"ADMIN"})
    @DisplayName("Failed correction validation does not create misleading audit event")
    void shouldNotCreateAuditWhenCorrectionFailsValidation() throws Exception {
        Instant checkIn = Instant.parse("2026-10-18T10:00:00Z");
        Instant invalidCheckOut = Instant.parse("2026-10-18T08:00:00Z"); // earlier than check-in!

        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                checkIn,
                invalidCheckOut,
                AttendanceStatus.CHECKED_OUT,
                CorrectionActionType.CORRECT_BOTH,
                "Testing checkout before checkin validation.",
                testRecord.getVersion()
        );

        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", testRecord.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot precede")));

        // Zero audit entries must exist
        List<AttendanceCorrection> audits = correctionRepository.findByAttendanceRecordIdOrderByCreatedAtDesc(testRecord.getId());
        assertThat(audits).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin_supervisor", roles = {"ADMIN"})
    @DisplayName("Audit events cannot be modified or deleted through ordinary application APIs (405 Method Not Allowed)")
    void shouldRejectModificationOrDeletionOfAuditEvents() throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder deleteReq =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/attendance/{id}/audit-history", testRecord.getId());
        mockMvc.perform(deleteReq)
                .andExpect(status().isMethodNotAllowed());

        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder putReq =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/attendance/{id}/audit-history", testRecord.getId());
        mockMvc.perform(putReq)
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @WithMockUser(username = "admin_supervisor", roles = {"ADMIN"})
    @DisplayName("Corrected values appear authoritatively in general attendance queries")
    void shouldReflectCorrectedValuesInOrdinaryAttendanceQueries() throws Exception {
        Instant correctedIn = Instant.parse("2026-10-18T03:30:00Z");
        Instant correctedOut = Instant.parse("2026-10-18T11:30:00Z");

        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                correctedIn,
                correctedOut,
                AttendanceStatus.CHECKED_OUT,
                CorrectionActionType.CORRECT_BOTH,
                "Corrected values verification in standard list query.",
                testRecord.getVersion()
        );

        mockMvc.perform(post("/api/admin/attendance/{id}/corrections", testRecord.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Now query regular attendance endpoint
        mockMvc.perform(get("/api/admin/attendance")
                        .param("fromDate", "2026-10-18")
                        .param("toDate", "2026-10-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].checkInTime").value("2026-10-18T03:30:00Z"))
                .andExpect(jsonPath("$.content[0].checkOutTime").value("2026-10-18T11:30:00Z"))
                .andExpect(jsonPath("$.content[0].durationMinutes").value(480))
                .andExpect(jsonPath("$.content[0].durationFormatted").value("8h 00m"))
                .andExpect(jsonPath("$.content[0].correctionIndicator").value("Corrected"))
                .andExpect(jsonPath("$.content[0].isIncomplete").value(false));
    }
}
