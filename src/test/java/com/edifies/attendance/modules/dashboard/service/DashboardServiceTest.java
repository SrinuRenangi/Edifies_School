package com.edifies.attendance.modules.dashboard.service;

import com.edifies.attendance.modules.attendance.model.AttendanceCorrection;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.attendance.service.AttendancePolicyService;
import com.edifies.attendance.modules.dashboard.dto.DashboardSummaryResponse;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private AttendanceCorrectionRepository correctionRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AttendancePolicyService policyService;

    private DashboardService dashboardService;

    private final LocalDate testDate = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                attendanceRepository,
                correctionRepository,
                employeeRepository,
                policyService
        );
        when(policyService.getZoneId()).thenReturn(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    @DisplayName("Dashboard Summary: Counts and metrics correctly computed from repositories")
    void shouldComputeDashboardSummaryCorrectly() {
        when(employeeRepository.countByStatus(EmployeeStatus.ACTIVE)).thenReturn(20L);
        when(employeeRepository.count()).thenReturn(25L); // 20 active + 5 inactive

        when(attendanceRepository.countDistinctEmployeesByWorkDateAndStatusAndEmployeeStatus(
                eq(testDate), eq(AttendanceStatus.CHECKED_IN), eq(EmployeeStatus.ACTIVE))).thenReturn(12L);

        when(attendanceRepository.countByWorkDateAndStatus(
                eq(testDate), eq(AttendanceStatus.CHECKED_OUT))).thenReturn(6L);

        when(attendanceRepository.countIncompleteSessions(
                eq(testDate), eq(AttendanceStatus.AUTO_CLOSED), eq(AttendanceStatus.CHECKED_IN))).thenReturn(2L);

        when(attendanceRepository.countDistinctEmployeesWithAttendanceOnWorkDate(
                eq(testDate), eq(AttendanceStatus.VOIDED), eq(EmployeeStatus.ACTIVE))).thenReturn(18L);

        when(attendanceRepository.findRecentActivityRecords(eq(testDate), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        when(correctionRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        when(attendanceRepository.findAttentionRequiredRecords(
                eq(testDate), eq(AttendanceStatus.AUTO_CLOSED), eq(AttendanceStatus.CHECKED_IN), any(Instant.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(testDate);

        assertNotNull(summary);
        assertEquals(testDate, summary.workDate());
        assertEquals("Asia/Kolkata", summary.timezone());
        assertEquals(20L, summary.activeEmployeeCount());
        assertEquals(25L, summary.totalEmployeeCount());
        assertEquals(12L, summary.currentlyCheckedInCount());
        assertEquals(6L, summary.completedSessionCount());
        assertEquals(2L, summary.incompleteSessionCount());
        // notCheckedInCount = 20 active - 18 with attendance = 2
        assertEquals(2L, summary.notCheckedInCount());
    }

    @Test
    @DisplayName("Dashboard Summary: Excludes deactivated employees from active counts without deleting history")
    void shouldExcludeDeactivatedEmployeesFromActiveCount() {
        when(employeeRepository.countByStatus(EmployeeStatus.ACTIVE)).thenReturn(15L);
        when(employeeRepository.count()).thenReturn(20L);

        when(attendanceRepository.countDistinctEmployeesByWorkDateAndStatusAndEmployeeStatus(
                eq(testDate), eq(AttendanceStatus.CHECKED_IN), eq(EmployeeStatus.ACTIVE))).thenReturn(10L);

        when(attendanceRepository.countByWorkDateAndStatus(
                eq(testDate), eq(AttendanceStatus.CHECKED_OUT))).thenReturn(4L);

        when(attendanceRepository.countIncompleteSessions(
                eq(testDate), eq(AttendanceStatus.AUTO_CLOSED), eq(AttendanceStatus.CHECKED_IN))).thenReturn(1L);

        when(attendanceRepository.countDistinctEmployeesWithAttendanceOnWorkDate(
                eq(testDate), eq(AttendanceStatus.VOIDED), eq(EmployeeStatus.ACTIVE))).thenReturn(14L);

        when(attendanceRepository.findRecentActivityRecords(eq(testDate), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(correctionRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(attendanceRepository.findAttentionRequiredRecords(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(testDate);

        assertEquals(15L, summary.activeEmployeeCount());
        assertEquals(20L, summary.totalEmployeeCount());
        assertEquals(1L, summary.notCheckedInCount()); // 15 - 14 = 1
    }

    @Test
    @DisplayName("Dashboard Attention Items: Classifies stale open sessions and auto-closed correctly")
    void shouldClassifyAttentionItemsAccurately() {
        Employee emp1 = new Employee("EMP001", "Alice", "IT", "1234");
        AttendanceRecord staleRecord = new AttendanceRecord(emp1, testDate.minusDays(1), Instant.now().minusSeconds(86400));
        staleRecord.setStatus(AttendanceStatus.CHECKED_IN);

        Employee emp2 = new Employee("EMP002", "Bob", "HR", "1234");
        AttendanceRecord autoClosedRecord = new AttendanceRecord(emp2, testDate, Instant.now().minusSeconds(10000));
        autoClosedRecord.setStatus(AttendanceStatus.AUTO_CLOSED);

        when(employeeRepository.countByStatus(any())).thenReturn(10L);
        when(employeeRepository.count()).thenReturn(10L);
        when(attendanceRepository.findRecentActivityRecords(any(), any())).thenReturn(Collections.emptyList());
        when(correctionRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(Collections.emptyList());

        when(attendanceRepository.findAttentionRequiredRecords(eq(testDate), any(), any(), any(), any()))
                .thenReturn(List.of(staleRecord, autoClosedRecord));

        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(testDate);

        assertNotNull(summary.attentionRequiredSessions());
        assertEquals(2, summary.attentionRequiredSessions().size());
        assertEquals("STALE_OPEN_SESSION", summary.attentionRequiredSessions().get(0).issueType());
        assertEquals("AUTO_CLOSED", summary.attentionRequiredSessions().get(1).issueType());
    }
}
