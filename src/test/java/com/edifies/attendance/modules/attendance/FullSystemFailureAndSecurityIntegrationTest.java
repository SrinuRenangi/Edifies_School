package com.edifies.attendance.modules.attendance;

import com.edifies.attendance.common.exception.ConflictException;
import com.edifies.attendance.modules.attendance.dto.correction.AttendanceCorrectionRequest;
import com.edifies.attendance.modules.attendance.dto.report.MonthlyReportResponse;
import com.edifies.attendance.modules.attendance.model.AttendanceCorrection;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.model.CorrectionActionType;
import com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.attendance.service.AttendanceCorrectionService;
import com.edifies.attendance.modules.attendance.service.AttendanceReportService;
import com.edifies.attendance.modules.attendance.service.AttendanceService;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.kiosk.service.KioskSessionService;
import com.edifies.attendance.modules.photo.service.PhotoValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FullSystemFailureAndSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceCorrectionService correctionService;

    @Autowired
    private AttendanceReportService reportService;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AttendanceCorrectionRepository correctionRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private KioskSessionService kioskSessionService;

    @Autowired
    private PhotoValidationService photoValidationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Employee employee;
    private final String rawPin = "5566";

    @BeforeEach
    void setUp() {
        correctionRepository.deleteAllInBatch();
        attendanceRepository.deleteAllInBatch();

        String code = "TEST-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        employee = new Employee(code, "E2E Test Subject", "Quality Assurance", passwordEncoder.encode(rawPin));
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee = employeeRepository.save(employee);
    }

    @AfterEach
    void tearDown() {
        try {
            correctionRepository.deleteAllInBatch();
            attendanceRepository.deleteAllInBatch();
            if (employee != null && employee.getId() != null) {
                employeeRepository.delete(employee);
            }
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Concurrency: Simultaneous check-in requests result in exactly 1 success and 0 duplicate sessions")
    void shouldHandleConcurrentCheckInsSafely() throws Exception {
        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // All threads release at the exact same millisecond
                    String token = kioskSessionService.createSession(employee).token();
                    attendanceService.checkIn(token, null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire threads simultaneously

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);

        List<AttendanceRecord> records = attendanceRepository.findAllByEmployeeId(employee.getId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo(AttendanceStatus.CHECKED_IN);
    }

    @Test
    @DisplayName("Concurrency: Simultaneous check-out requests result in exactly 1 success")
    void shouldHandleConcurrentCheckOutsSafely() throws Exception {
        // First check in cleanly
        String session1 = kioskSessionService.createSession(employee).token();
        attendanceService.checkIn(session1, null);

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    String token = kioskSessionService.createSession(employee).token();
                    attendanceService.checkOut(token, null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);

        List<AttendanceRecord> records = attendanceRepository.findAllByEmployeeId(employee.getId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo(AttendanceStatus.CHECKED_OUT);
        assertThat(records.get(0).getCheckOutTime()).isNotNull();
    }

    @Test
    @DisplayName("Optimistic Locking: Stale version in correction request triggers 409 Conflict")
    void shouldRejectCorrectionWithStaleVersion() {
        AttendanceRecord record = new AttendanceRecord(employee, LocalDate.now(), Instant.now().minus(2, ChronoUnit.HOURS));
        record.setStatus(AttendanceStatus.CHECKED_OUT);
        record.setCheckOutTime(Instant.now());
        record = attendanceRepository.saveAndFlush(record);

        Long originalVersion = record.getVersion();

        // 1. First correction succeeds and increments version
        AttendanceCorrectionRequest req1 = new AttendanceCorrectionRequest(
                record.getCheckInTime(),
                Instant.now().minus(1, ChronoUnit.HOURS),
                AttendanceStatus.CHECKED_OUT,
                CorrectionActionType.CORRECT_CHECK_OUT,
                "First supervisor adjustment",
                originalVersion
        );
        correctionService.correctAttendance(record.getId(), req1, "supervisor1");

        // 2. Second correction with the stale originalVersion must throw ConflictException
        AttendanceCorrectionRequest staleReq = new AttendanceCorrectionRequest(
                record.getCheckInTime(),
                Instant.now().minus(30, ChronoUnit.MINUTES),
                AttendanceStatus.CHECKED_OUT,
                CorrectionActionType.CORRECT_CHECK_OUT,
                "Concurrent supervisor collision",
                originalVersion
        );

        final UUID recordId = record.getId();
        assertThrows(ConflictException.class, () -> {
            correctionService.correctAttendance(recordId, staleReq, "supervisor2");
        });
    }

    @Test
    @DisplayName("Audit History: Correction preserves original values and creates immutable audit record")
    void shouldCreateImmutableAuditRecordOnCorrection() {
        Instant checkIn = Instant.now().minus(4, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        Instant checkOut = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);

        AttendanceRecord record = new AttendanceRecord(employee, LocalDate.now(), checkIn);
        record.setStatus(AttendanceStatus.CHECKED_OUT);
        record.setCheckOutTime(checkOut);
        record = attendanceRepository.saveAndFlush(record);

        Instant newCheckOut = Instant.now().minus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
        AttendanceCorrectionRequest req = new AttendanceCorrectionRequest(
                checkIn,
                newCheckOut,
                AttendanceStatus.CHECKED_OUT,
                CorrectionActionType.CORRECT_CHECK_OUT,
                "Audited departure adjustment",
                record.getVersion()
        );

        correctionService.correctAttendance(record.getId(), req, "lead_auditor");

        List<AttendanceCorrection> audits = correctionRepository.findByAttendanceRecordIdOrderByCreatedAtDesc(record.getId());
        assertThat(audits).hasSize(1);

        AttendanceCorrection audit = audits.get(0);
        assertThat(audit.getPerformedBy()).isEqualTo("lead_auditor");
        assertThat(audit.getReason()).isEqualTo("Audited departure adjustment");
        assertThat(audit.getActionType()).isEqualTo(CorrectionActionType.CORRECT_CHECK_OUT);
        assertThat(audit.getPreviousCheckOutTime()).isEqualTo(checkOut);
        assertThat(audit.getCorrectedCheckOutTime()).isEqualTo(newCheckOut);
        assertThat(audit.getAttendanceRecord().getId()).isEqualTo(record.getId());
    }

    @Test
    @DisplayName("Security: Unauthenticated access to admin endpoints is rejected (401/403)")
    void shouldDenyUnauthenticatedAccessToAdminApis() throws Exception {
        mockMvc.perform(get("/api/admin/employees"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/reports/monthly"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/storage/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Unauthorized photo retrieval is denied")
    void shouldDenyUnauthorizedPhotoRetrieval() throws Exception {
        mockMvc.perform(get("/api/photos/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Input Validation: Extreme and malicious inputs are safely handled")
    void shouldSafelyHandleExtremeAndHostileInputs() throws Exception {
        // SQL injection payload in query parameter safely handled by parameterized query
        mockMvc.perform(get("/api/admin/employees")
                .param("search", "'; DROP TABLE attendance_records; --")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk());

        // Negative pagination safely normalized by Spring Pageable resolver
        mockMvc.perform(get("/api/admin/employees")
                .param("page", "-1")
                .param("size", "-5"))
                .andExpect(status().isOk());

        // XSS script payload in employee registration with dynamic ID
        String xssId = "XSS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String xssPayload = """
            {
                "employeeId": "%s",
                "fullName": "<script>alert('xss')</script>",
                "department": "Engineering",
                "pin": "1234"
            }
            """.formatted(xssId);
        mockMvc.perform(post("/api/admin/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(xssPayload))
                .andExpect(status().isCreated());

        // Clean up created XSS employee
        employeeRepository.findByEmployeeId(xssId).ifPresent(employeeRepository::delete);
    }

    @Test
    @DisplayName("Photo Validation: Magic bytes verification blocks non-JPEG content")
    void shouldBlockNonJpegFilesByMagicBytes() {
        // Plain text file pretending to be JPEG
        byte[] textBytes = "This is not an image, it is a text document.".getBytes();
        MockMultipartFile textFile = new MockMultipartFile("photo", "fake.jpg", "image/jpeg", textBytes);

        assertThrows(Exception.class, () -> {
            photoValidationService.validate(textFile);
        });

        // Valid minimal 200x200 JPEG created with BufferedImage
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(img, "jpg", baos);
        } catch (Exception e) {
            fail("Failed to generate test image: " + e.getMessage());
        }
        MockMultipartFile jpegFile = new MockMultipartFile("photo", "valid.jpg", "image/jpeg", baos.toByteArray());

        assertDoesNotThrow(() -> {
            photoValidationService.validate(jpegFile);
        });
    }

    @Test
    @DisplayName("Three-Year Historical Data: Date queries span 3 years and inactive employees remain accessible")
    void shouldHandleThreeYearHistoryAndPreserveInactiveEmployees() {
        // 3 years ago (2023)
        LocalDate date2023 = LocalDate.of(2023, 6, 15);
        AttendanceRecord r2023 = new AttendanceRecord(employee, date2023, Instant.now().minus(3 * 365, ChronoUnit.DAYS));
        r2023.setStatus(AttendanceStatus.CHECKED_OUT);
        r2023.setCheckOutTime(r2023.getCheckInTime().plus(8, ChronoUnit.HOURS));
        attendanceRepository.saveAndFlush(r2023);

        // 1 year ago (2025)
        LocalDate date2025 = LocalDate.of(2025, 6, 15);
        AttendanceRecord r2025 = new AttendanceRecord(employee, date2025, Instant.now().minus(365, ChronoUnit.DAYS));
        r2025.setStatus(AttendanceStatus.CHECKED_OUT);
        r2025.setCheckOutTime(r2025.getCheckInTime().plus(8, ChronoUnit.HOURS));
        attendanceRepository.saveAndFlush(r2025);

        // Deactivate employee
        employee.setStatus(EmployeeStatus.INACTIVE);
        employeeRepository.saveAndFlush(employee);

        // Multi-year range query must return all historical records for inactive employee
        List<AttendanceRecord> history = attendanceRepository.findAllWithEmployeeByWorkDateBetween(
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2026, 12, 31)
        );

        assertThat(history).extracting(AttendanceRecord::getWorkDate)
                .contains(date2023, date2025);

        // Monthly report for month=6, year=2023 returns the record even though employee is now inactive
        MonthlyReportResponse report = reportService.generateMonthlyReport(6, 2023, null, null);
        assertThat(report.totalRecordedSessions()).isGreaterThanOrEqualTo(1);
    }
}
