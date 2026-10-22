package com.edifies.attendance.modules.attendance.service;

import com.edifies.attendance.common.exception.ConflictException;
import com.edifies.attendance.common.exception.DuplicateResourceException;
import com.edifies.attendance.common.exception.InvalidOperationException;
import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.common.exception.ValidationException;
import com.edifies.attendance.modules.attendance.dto.AttendanceResponse;
import com.edifies.attendance.modules.attendance.dto.correction.AttendanceCorrectionRequest;
import com.edifies.attendance.modules.attendance.dto.correction.ManualAttendanceRequest;
import com.edifies.attendance.modules.attendance.model.AttendanceCorrection;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.model.CorrectionActionType;
import com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceCorrectionServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private AttendanceCorrectionRepository correctionRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private AttendanceCorrectionService correctionService;

    private Employee employee;
    private AttendanceRecord record;
    private UUID recordId;
    private UUID employeeId;

    @BeforeEach
    void setUp() {
        correctionService = new AttendanceCorrectionService(attendanceRepository, correctionRepository, employeeRepository);

        employeeId = UUID.randomUUID();
        employee = new Employee("EMP-001", "John Doe", "Engineering", "$2a$12$fakeHash");
        employee.setId(employeeId);

        recordId = UUID.randomUUID();
        record = new AttendanceRecord(employee, LocalDate.of(2026, 10, 20), Instant.now().minus(8, ChronoUnit.HOURS));
        record.setId(recordId);
        record.setVersion(1L);
    }

    @Test
    @DisplayName("Should successfully correct check-in and check-out times and persist audit event")
    void shouldCorrectPunchesSuccessfully() {
        Instant newIn = Instant.parse("2026-10-20T03:30:00Z");
        Instant newOut = Instant.parse("2026-10-20T11:30:00Z");

        when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(attendanceRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                newIn,
                newOut,
                AttendanceStatus.CHECKED_OUT,
                CorrectionActionType.CORRECT_BOTH,
                "Corrected clock discrepancy per verified swipe card logs.",
                1L
        );

        AttendanceResponse response = correctionService.correctAttendance(recordId, request, "admin_user");

        assertThat(response).isNotNull();
        assertThat(response.checkInTime()).isEqualTo(newIn);
        assertThat(response.checkOutTime()).isEqualTo(newOut);
        assertThat(response.status()).isEqualTo(AttendanceStatus.CHECKED_OUT);
        assertThat(response.correctionIndicator()).isEqualTo("Corrected");

        // Verify audit event persisted with before and after snapshots
        ArgumentCaptor<AttendanceCorrection> auditCaptor = ArgumentCaptor.forClass(AttendanceCorrection.class);
        verify(correctionRepository).save(auditCaptor.capture());
        AttendanceCorrection audit = auditCaptor.getValue();

        assertThat(audit.getPerformedBy()).isEqualTo("admin_user");
        assertThat(audit.getReason()).isEqualTo("Corrected clock discrepancy per verified swipe card logs.");
        assertThat(audit.getActionType()).isEqualTo(CorrectionActionType.CORRECT_BOTH);
        assertThat(audit.getCorrectedCheckInTime()).isEqualTo(newIn);
        assertThat(audit.getCorrectedCheckOutTime()).isEqualTo(newOut);
        assertThat(audit.getPreviousStatus()).isEqualTo(AttendanceStatus.CHECKED_IN);
        assertThat(audit.getCorrectedStatus()).isEqualTo(AttendanceStatus.CHECKED_OUT);
    }

    @Test
    @DisplayName("Should successfully resolve a missed checkout")
    void shouldResolveMissedCheckout() {
        record.setStatus(AttendanceStatus.AUTO_CLOSED);
        when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(attendanceRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant verifiedOut = record.getCheckInTime().plus(8, ChronoUnit.HOURS);
        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                null,
                verifiedOut,
                AttendanceStatus.CHECKED_OUT,
                CorrectionActionType.RESOLVE_MISSED_CHECKOUT,
                "Employee forgot checkout; departure verified by manager.",
                1L
        );

        AttendanceResponse response = correctionService.correctAttendance(recordId, request, "admin_user");

        assertThat(response.status()).isEqualTo(AttendanceStatus.CHECKED_OUT);
        assertThat(response.checkOutTime()).isEqualTo(verifiedOut);

        ArgumentCaptor<AttendanceCorrection> captor = ArgumentCaptor.forClass(AttendanceCorrection.class);
        verify(correctionRepository).save(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo(CorrectionActionType.RESOLVE_MISSED_CHECKOUT);
    }

    @Test
    @DisplayName("Should reject correction when reason is blank or missing")
    void shouldRejectBlankReason() {
        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                Instant.now(),
                Instant.now().plus(4, ChronoUnit.HOURS),
                AttendanceStatus.CHECKED_OUT,
                null,
                "   ",
                1L
        );

        assertThatThrownBy(() -> correctionService.correctAttendance(recordId, request, "admin_user"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("mandatory");

        verifyNoInteractions(attendanceRepository);
        verifyNoInteractions(correctionRepository);
    }

    @Test
    @DisplayName("Should reject check-out time preceding check-in time")
    void shouldRejectCheckoutBeforeCheckin() {
        when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));

        Instant newIn = Instant.parse("2026-10-20T10:00:00Z");
        Instant newOut = Instant.parse("2026-10-20T08:00:00Z"); // Earlier than check-in!

        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                newIn,
                newOut,
                AttendanceStatus.CHECKED_OUT,
                CorrectionActionType.CORRECT_BOTH,
                "Testing invalid interval.",
                1L
        );

        assertThatThrownBy(() -> correctionService.correctAttendance(recordId, request, "admin_user"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Check-out time cannot precede check-in time");

        verify(correctionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should detect concurrency conflict and reject stale record version")
    void shouldDetectVersionMismatchConflict() {
        record.setVersion(2L); // Database has version 2
        when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));

        AttendanceCorrectionRequest staleRequest = new AttendanceCorrectionRequest(
                Instant.now(),
                Instant.now().plus(2, ChronoUnit.HOURS),
                AttendanceStatus.CHECKED_OUT,
                null,
                "Stale update attempt.",
                1L // Submitting version 1
        );

        assertThatThrownBy(() -> correctionService.correctAttendance(recordId, staleRequest, "admin_user"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("version conflict");

        verify(attendanceRepository, never()).save(any());
        verify(correctionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully void an erroneous attendance record")
    void shouldVoidRecordSuccessfully() {
        when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(attendanceRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                null,
                null,
                AttendanceStatus.VOIDED,
                CorrectionActionType.VOID_RECORD,
                "Duplicate attendance punch created by erroneous kiosk retry.",
                1L
        );

        AttendanceResponse response = correctionService.correctAttendance(recordId, request, "admin_user");

        assertThat(response.status()).isEqualTo(AttendanceStatus.VOIDED);
        verify(correctionRepository).save(argThat(c -> c.getActionType() == CorrectionActionType.VOID_RECORD && c.getCorrectedStatus() == AttendanceStatus.VOIDED));
    }

    @Test
    @DisplayName("Should reject voiding an already voided record")
    void shouldRejectVoidingAlreadyVoided() {
        record.setStatus(AttendanceStatus.VOIDED);
        when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));

        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest(
                null,
                null,
                AttendanceStatus.VOIDED,
                CorrectionActionType.VOID_RECORD,
                "Attempting duplicate void.",
                1L
        );

        assertThatThrownBy(() -> correctionService.correctAttendance(recordId, request, "admin_user"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("already voided");
    }

    @Test
    @DisplayName("Should create manual attendance entry and record audit event")
    void shouldCreateManualEntrySuccessfully() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(attendanceRepository.existsByEmployeeIdAndWorkDateAndStatusNot(employeeId, LocalDate.of(2026, 10, 15), AttendanceStatus.VOIDED))
                .thenReturn(false);
        when(attendanceRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant in = Instant.parse("2026-10-15T03:30:00Z");
        Instant out = Instant.parse("2026-10-15T11:45:00Z");

        ManualAttendanceRequest request = new ManualAttendanceRequest(
                employeeId,
                LocalDate.of(2026, 10, 15),
                in,
                out,
                "Manual attendance entry approved by HR for field duty."
        );

        AttendanceResponse response = correctionService.createManualEntry(request, "admin_user");

        assertThat(response).isNotNull();
        assertThat(response.workDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(response.checkInTime()).isEqualTo(in);
        assertThat(response.checkOutTime()).isEqualTo(out);
        assertThat(response.correctionIndicator()).isEqualTo("Manual Entry");

        ArgumentCaptor<AttendanceCorrection> auditCaptor = ArgumentCaptor.forClass(AttendanceCorrection.class);
        verify(correctionRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getActionType()).isEqualTo(CorrectionActionType.MANUAL_ENTRY);
        assertThat(auditCaptor.getValue().getReason()).contains("field duty");
    }

    @Test
    @DisplayName("Should reject manual attendance entry if record already exists for date")
    void shouldRejectDuplicateManualEntry() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(attendanceRepository.existsByEmployeeIdAndWorkDateAndStatusNot(employeeId, LocalDate.of(2026, 10, 15), AttendanceStatus.VOIDED))
                .thenReturn(true);

        ManualAttendanceRequest request = new ManualAttendanceRequest(
                employeeId,
                LocalDate.of(2026, 10, 15),
                Instant.now().minus(8, ChronoUnit.HOURS),
                Instant.now(),
                "Duplicate date attempt."
        );

        assertThatThrownBy(() -> correctionService.createManualEntry(request, "admin_user"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(attendanceRepository, never()).save(any());
        verify(correctionRepository, never()).save(any());
    }
}
