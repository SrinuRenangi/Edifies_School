package com.edifies.attendance.modules.attendance.service;

import com.edifies.attendance.common.exception.InvalidOperationException;
import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.modules.attendance.dto.AttendanceResponse;
import com.edifies.attendance.modules.attendance.dto.AttendanceStatusResponse;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.kiosk.service.KioskSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private KioskSessionService kioskSessionService;

    @Mock
    private AttendancePolicyService policyService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private com.edifies.attendance.modules.photo.service.AttendancePhotoService attendancePhotoService;

    @InjectMocks
    private AttendanceService attendanceService;


    private Employee activeEmployee;
    private Employee inactiveEmployee;
    private KioskSessionService.KioskSession activeSession;
    private final String sessionToken = "session-test-token-123";
    private final LocalDate today = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void setUp() {
        activeEmployee = new Employee("EMP-001", "Ada Lovelace", "Engineering");
        activeEmployee.setId(UUID.randomUUID());
        activeEmployee.setStatus(EmployeeStatus.ACTIVE);

        inactiveEmployee = new Employee("EMP-002", "Charles Babbage", "Mathematics");
        inactiveEmployee.setId(UUID.randomUUID());
        inactiveEmployee.setStatus(EmployeeStatus.INACTIVE);

        activeSession = new KioskSessionService.KioskSession(
                sessionToken,
                activeEmployee.getId(),
                activeEmployee.getEmployeeId(),
                activeEmployee.getFullName(),
                activeEmployee.getDepartment(),
                Instant.now().plusSeconds(60)
        );
    }

    @Test
    @DisplayName("Status: New workday with no previous attendance returns NOT_CHECKED_IN and CHECK_IN action")
    void shouldReturnNotCheckedInWhenNoAttendanceExists() {
        when(kioskSessionService.validateSession(sessionToken)).thenReturn(activeSession);
        when(employeeRepository.findById(activeEmployee.getId())).thenReturn(Optional.of(activeEmployee));
        when(policyService.calculateWorkDate(any())).thenReturn(today);
        when(attendanceRepository.findByEmployeeIdAndStatus(activeEmployee.getId(), AttendanceStatus.CHECKED_IN))
                .thenReturn(Optional.empty());
        when(attendanceRepository.findByEmployeeIdAndWorkDateAndStatusNot(activeEmployee.getId(), today, AttendanceStatus.VOIDED))
                .thenReturn(Optional.empty());

        AttendanceStatusResponse status = attendanceService.getAttendanceStatus(sessionToken);

        assertNotNull(status);
        assertEquals("NOT_CHECKED_IN", status.currentStatus());
        assertEquals("CHECK_IN", status.allowedAction());
        assertNull(status.checkInTime());
    }

    @Test
    @DisplayName("Status: Open session for today returns CHECKED_IN and CHECK_OUT action")
    void shouldReturnCheckedInWhenOpenSessionExists() {
        AttendanceRecord openRecord = new AttendanceRecord(activeEmployee, today, Instant.now().minusSeconds(3600));

        when(kioskSessionService.validateSession(sessionToken)).thenReturn(activeSession);
        when(employeeRepository.findById(activeEmployee.getId())).thenReturn(Optional.of(activeEmployee));
        when(policyService.calculateWorkDate(any())).thenReturn(today);
        when(attendanceRepository.findByEmployeeIdAndStatus(activeEmployee.getId(), AttendanceStatus.CHECKED_IN))
                .thenReturn(Optional.of(openRecord));

        AttendanceStatusResponse status = attendanceService.getAttendanceStatus(sessionToken);

        assertNotNull(status);
        assertEquals("CHECKED_IN", status.currentStatus());
        assertEquals("CHECK_OUT", status.allowedAction());
        assertEquals(openRecord.getCheckInTime(), status.checkInTime());
    }

    @Test
    @DisplayName("Status: Already checked out today returns CHECKED_OUT and NONE action")
    void shouldReturnCheckedOutWhenSessionAlreadyClosed() {
        AttendanceRecord closedRecord = new AttendanceRecord(activeEmployee, today, Instant.now().minusSeconds(7200));
        closedRecord.setCheckOutTime(Instant.now().minusSeconds(3600));
        closedRecord.setStatus(AttendanceStatus.CHECKED_OUT);

        when(kioskSessionService.validateSession(sessionToken)).thenReturn(activeSession);
        when(employeeRepository.findById(activeEmployee.getId())).thenReturn(Optional.of(activeEmployee));
        when(policyService.calculateWorkDate(any())).thenReturn(today);
        when(attendanceRepository.findByEmployeeIdAndStatus(activeEmployee.getId(), AttendanceStatus.CHECKED_IN))
                .thenReturn(Optional.empty());
        when(attendanceRepository.findByEmployeeIdAndWorkDateAndStatusNot(activeEmployee.getId(), today, AttendanceStatus.VOIDED))
                .thenReturn(Optional.of(closedRecord));

        AttendanceStatusResponse status = attendanceService.getAttendanceStatus(sessionToken);

        assertNotNull(status);
        assertEquals("CHECKED_OUT", status.currentStatus());
        assertEquals("NONE", status.allowedAction());
    }

    @Test
    @DisplayName("Check-In: Successfully records check-in when employee is active and has no open session")
    void shouldSuccessfullyCheckIn() {
        when(idempotencyService.getCachedResponse(any())).thenReturn(Optional.empty());
        when(kioskSessionService.validateSession(sessionToken)).thenReturn(activeSession);
        when(employeeRepository.findById(activeEmployee.getId())).thenReturn(Optional.of(activeEmployee));
        when(policyService.calculateWorkDate(any())).thenReturn(today);
        when(attendanceRepository.findByEmployeeIdAndStatus(activeEmployee.getId(), AttendanceStatus.CHECKED_IN))
                .thenReturn(Optional.empty());
        when(attendanceRepository.findByEmployeeIdAndWorkDateAndStatusNot(activeEmployee.getId(), today, AttendanceStatus.VOIDED))
                .thenReturn(Optional.empty());

        when(attendanceRepository.saveAndFlush(any(AttendanceRecord.class))).thenAnswer(invocation -> {
            AttendanceRecord r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        AttendanceResponse res = attendanceService.checkIn(sessionToken, "idem-key-1");

        assertNotNull(res);
        assertEquals(AttendanceStatus.CHECKED_IN, res.status());
        assertEquals(today, res.workDate());
        assertEquals("EMP-001", res.employeeCode());
        verify(attendanceRepository, times(1)).saveAndFlush(any(AttendanceRecord.class));
        verify(idempotencyService, times(1)).recordResponse(eq("idem-key-1"), anyString(), eq(activeEmployee.getId()), eq(200), any());
    }

    @Test
    @DisplayName("Check-In: Rejects duplicate check-in if open session already exists for today")
    void shouldRejectCheckInWhenAlreadyCheckedIn() {
        AttendanceRecord openRecord = new AttendanceRecord(activeEmployee, today, Instant.now().minusSeconds(1000));

        when(idempotencyService.getCachedResponse(any())).thenReturn(Optional.empty());
        when(kioskSessionService.validateSession(sessionToken)).thenReturn(activeSession);
        when(employeeRepository.findById(activeEmployee.getId())).thenReturn(Optional.of(activeEmployee));
        when(policyService.calculateWorkDate(any())).thenReturn(today);
        when(attendanceRepository.findByEmployeeIdAndStatus(activeEmployee.getId(), AttendanceStatus.CHECKED_IN))
                .thenReturn(Optional.of(openRecord));

        assertThrows(InvalidOperationException.class, () ->
                attendanceService.checkIn(sessionToken, "idem-key-2"));
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Check-In: Rejects check-in if inactive employee attempts punch")
    void shouldRejectCheckInForInactiveEmployee() {
        KioskSessionService.KioskSession inactiveSession = new KioskSessionService.KioskSession(
                "token-inactive",
                inactiveEmployee.getId(),
                inactiveEmployee.getEmployeeId(),
                inactiveEmployee.getFullName(),
                inactiveEmployee.getDepartment(),
                Instant.now().plusSeconds(60)
        );

        when(idempotencyService.getCachedResponse(any())).thenReturn(Optional.empty());
        when(kioskSessionService.validateSession("token-inactive")).thenReturn(inactiveSession);
        when(employeeRepository.findById(inactiveEmployee.getId())).thenReturn(Optional.of(inactiveEmployee));

        assertThrows(InvalidOperationException.class, () ->
                attendanceService.checkIn("token-inactive", null));
    }

    @Test
    @DisplayName("Check-In: Rejects check-in if session token is expired or invalid")
    void shouldRejectCheckInForExpiredSessionToken() {
        when(idempotencyService.getCachedResponse(any())).thenReturn(Optional.empty());
        when(kioskSessionService.validateSession("expired-token")).thenReturn(null);

        assertThrows(BadCredentialsException.class, () ->
                attendanceService.checkIn("expired-token", null));
    }

    @Test
    @DisplayName("Check-Out: Successfully closes open session")
    void shouldSuccessfullyCheckOut() {
        AttendanceRecord openRecord = new AttendanceRecord(activeEmployee, today, Instant.now().minusSeconds(3600));
        openRecord.setId(UUID.randomUUID());

        when(idempotencyService.getCachedResponse(any())).thenReturn(Optional.empty());
        when(kioskSessionService.validateSession(sessionToken)).thenReturn(activeSession);
        when(employeeRepository.findById(activeEmployee.getId())).thenReturn(Optional.of(activeEmployee));
        when(attendanceRepository.findByEmployeeIdAndStatus(activeEmployee.getId(), AttendanceStatus.CHECKED_IN))
                .thenReturn(Optional.of(openRecord));
        when(attendanceRepository.saveAndFlush(any(AttendanceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceResponse res = attendanceService.checkOut(sessionToken, "idem-checkout-1");

        assertNotNull(res);
        assertEquals(AttendanceStatus.CHECKED_OUT, res.status());
        assertNotNull(res.checkOutTime());
        verify(attendanceRepository, times(1)).saveAndFlush(openRecord);
    }

    @Test
    @DisplayName("Check-Out: Rejects checkout when employee has no open session")
    void shouldRejectCheckOutWhenNoOpenSessionExists() {
        when(idempotencyService.getCachedResponse(any())).thenReturn(Optional.empty());
        when(kioskSessionService.validateSession(sessionToken)).thenReturn(activeSession);
        when(employeeRepository.findById(activeEmployee.getId())).thenReturn(Optional.of(activeEmployee));
        when(attendanceRepository.findByEmployeeIdAndStatus(activeEmployee.getId(), AttendanceStatus.CHECKED_IN))
                .thenReturn(Optional.empty());

        assertThrows(InvalidOperationException.class, () ->
                attendanceService.checkOut(sessionToken, null));
    }

    @Test
    @DisplayName("Idempotency: Repeated request with same idempotency key returns cached response")
    void shouldReturnCachedResponseOnIdempotencyHit() {
        AttendanceResponse cached = new AttendanceResponse(
                UUID.randomUUID(),
                activeEmployee.getId(),
                "EMP-001",
                "Ada Lovelace",
                "Engineering",
                today,
                AttendanceStatus.CHECKED_IN,
                Instant.now(),
                null,
                "Cached response"
        );

        when(idempotencyService.getCachedResponse("idem-repeated-key")).thenReturn(Optional.of(cached));

        AttendanceResponse res = attendanceService.checkIn(sessionToken, "idem-repeated-key");

        assertSame(cached, res);
        verify(attendanceRepository, never()).saveAndFlush(any());
    }
}
