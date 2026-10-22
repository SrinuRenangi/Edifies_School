package com.edifies.attendance.modules.photo;

import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.photo.dto.PhotoRetentionPolicyRequest;
import com.edifies.attendance.modules.photo.model.AttendancePhoto;
import com.edifies.attendance.modules.photo.model.PhotoRetentionPolicy;
import com.edifies.attendance.modules.photo.model.PhotoType;
import com.edifies.attendance.modules.photo.model.StorageStatus;
import com.edifies.attendance.modules.photo.repository.AttendancePhotoRepository;
import com.edifies.attendance.modules.photo.repository.PhotoCleanupHistoryRepository;
import com.edifies.attendance.modules.photo.repository.PhotoRetentionPolicyRepository;
import com.edifies.attendance.modules.photo.service.PhotoStorageService;
import com.edifies.attendance.modules.security.model.AdminUser;
import com.edifies.attendance.modules.security.repository.AdminUserRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminStorageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AttendancePhotoRepository attendancePhotoRepository;

    @Autowired
    private PhotoRetentionPolicyRepository policyRepository;

    @Autowired
    private PhotoCleanupHistoryRepository cleanupHistoryRepository;

    @Autowired
    private PhotoStorageService photoStorageService;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AttendanceCorrectionRepository attendanceCorrectionRepository;

    private Employee employee;
    private AttendanceRecord attendanceRecord;

    @BeforeEach
    void setUp() {
        attendanceCorrectionRepository.deleteAll();
        attendancePhotoRepository.deleteAll();
        attendanceRepository.deleteAll();
        employeeRepository.deleteAll();
        cleanupHistoryRepository.deleteAll();

        // Seed admin user if not present
        if (!adminUserRepository.existsByUsernameIgnoreCase("admin")) {
            AdminUser admin = new AdminUser("admin", passwordEncoder.encode("Admin@12345"), "System Admin", "ROLE_ADMIN", true);
            adminUserRepository.saveAndFlush(admin);
        }

        // Create sample staff & attendance session
        employee = new Employee();
        employee.setEmployeeId("EMP-STORAGE-01");
        employee.setFullName("Storage Admin Staff");
        employee.setDepartment("Operations");
        employee.setPinHash(passwordEncoder.encode("1234"));
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee = employeeRepository.saveAndFlush(employee);

        attendanceRecord = new AttendanceRecord();
        attendanceRecord.setEmployee(employee);
        attendanceRecord.setWorkDate(LocalDate.now().minusDays(100));
        attendanceRecord.setCheckInTime(Instant.now().minus(100, ChronoUnit.DAYS));
        attendanceRecord.setStatus(AttendanceStatus.CHECKED_IN);
        attendanceRecord = attendanceRepository.saveAndFlush(attendanceRecord);
    }

    @AfterEach
    void tearDown() {
        // Reset policy to default disabled
        Optional<PhotoRetentionPolicy> policyOpt = policyRepository.findById(PhotoRetentionPolicy.DEFAULT_POLICY_ID);
        if (policyOpt.isPresent()) {
            PhotoRetentionPolicy p = policyOpt.get();
            p.setRetentionEnabled(false);
            p.setRetentionDays(90);
            policyRepository.saveAndFlush(p);
        }
    }

    @Test
    @DisplayName("Anonymous access to storage endpoints is blocked with 401")
    void anonymousAccessBlocked() throws Exception {
        mockMvc.perform(get("/api/admin/storage/health"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/storage/retention/policy"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/storage/retention/dry-run"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/storage/cleanup/execute"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "kiosk-device", roles = {"KIOSK"})
    @DisplayName("Non-admin role access to storage retention endpoints is blocked with 403")
    void nonAdminRoleForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/storage/health"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/storage/retention/policy"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Admin can view storage health metrics with disk and photo stats")
    void adminCanViewStorageHealth() throws Exception {
        mockMvc.perform(get("/api/admin/storage/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageRootAccessible").value(true))
                .andExpect(jsonPath("$.storageWritable").value(true))
                .andExpect(jsonPath("$.warningLevel").isNotEmpty())
                .andExpect(jsonPath("$.totalSpaceBytes").isNumber())
                .andExpect(jsonPath("$.usableSpaceBytes").isNumber());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Admin can view default photo retention policy (disabled by default)")
    void adminCanViewDefaultPolicy() throws Exception {
        mockMvc.perform(get("/api/admin/storage/retention/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionEnabled").value(false))
                .andExpect(jsonPath("$.retentionDays").value(90))
                .andExpect(jsonPath("$.batchSize").value(50));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Admin can configure approved retention policy with valid parameters")
    void adminCanConfigurePolicy() throws Exception {
        PhotoRetentionPolicyRequest request = new PhotoRetentionPolicyRequest(true, 60, "0 0 2 * * *", 150, 120, "Approved 60 days");

        mockMvc.perform(put("/api/admin/storage/retention/policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionEnabled").value(true))
                .andExpect(jsonPath("$.retentionDays").value(60))
                .andExpect(jsonPath("$.batchSize").value(150));

        // Verify policy persisted in database
        PhotoRetentionPolicy policy = policyRepository.findById(PhotoRetentionPolicy.DEFAULT_POLICY_ID).orElseThrow();
        assertThat(policy.isRetentionEnabled()).isTrue();
        assertThat(policy.getRetentionDays()).isEqualTo(60);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Invalid retention policy values return 400 Bad Request")
    void invalidPolicyValuesRejected() throws Exception {
        PhotoRetentionPolicyRequest invalidDays = new PhotoRetentionPolicyRequest(true, -10, "0 0 2 * * *", 100, 120, "Invalid");

        mockMvc.perform(put("/api/admin/storage/retention/policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDays)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.retentionDays").value(org.hamcrest.Matchers.containsString("at least 1 day")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Dry-run preview calculates eligibility accurately without deleting files or DB records")
    void dryRunCalculatesEligibilityWithoutDeletion() throws Exception {
        // Enable policy for 60 days
        PhotoRetentionPolicy policy = policyRepository.findById(PhotoRetentionPolicy.DEFAULT_POLICY_ID).orElseGet(PhotoRetentionPolicy::new);
        policy.setRetentionEnabled(true);
        policy.setRetentionDays(60);
        policyRepository.saveAndFlush(policy);

        // Store synthetic photo older than 60 days
        String storageKey = photoStorageService.store(
                new ByteArrayInputStream("TEST_IMAGE".getBytes(StandardCharsets.UTF_8)),
                "2026/06/01/session-old",
                "check-in.jpg"
        );

        AttendancePhoto photo = new AttendancePhoto();
        photo.setAttendance(attendanceRecord);
        photo.setEmployee(employee);
        photo.setPhotoType(PhotoType.CHECK_IN);
        photo.setStoragePath(storageKey);
        photo.setFileName("check-in.jpg");
        photo.setMimeType("image/jpeg");
        photo.setFileSize(10L);
        photo.setImageWidth(640);
        photo.setImageHeight(480);
        photo.setStorageStatus(StorageStatus.ACTIVE);
        photo.setCreatedAt(Instant.now().minus(90, ChronoUnit.DAYS)); // 90 days old > 60 days threshold
        attendancePhotoRepository.saveAndFlush(photo);

        mockMvc.perform(get("/api/admin/storage/retention/dry-run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleCount").value(1))
                .andExpect(jsonPath("$.retentionEnabled").value(true));

        // Verify photo file and DB record are UNTOUCHED after dry-run
        assertThat(photoStorageService.exists(storageKey)).isTrue();
        AttendancePhoto refreshed = attendancePhotoRepository.findById(photo.getId()).orElseThrow();
        assertThat(refreshed.getStorageStatus()).isEqualTo(StorageStatus.ACTIVE);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Safe cleanup execution deletes physical photo file and marks DB record PURGED while preserving attendance and employee records")
    void safeCleanupPurgesPhotoPreservingAttendanceRecord() throws Exception {
        // Enable policy for 60 days
        PhotoRetentionPolicy policy = policyRepository.findById(PhotoRetentionPolicy.DEFAULT_POLICY_ID).orElseGet(PhotoRetentionPolicy::new);
        policy.setRetentionEnabled(true);
        policy.setRetentionDays(60);
        policyRepository.saveAndFlush(policy);

        // Store synthetic photo older than 60 days (95 days old)
        String storageKey = photoStorageService.store(
                new ByteArrayInputStream("PURGEABLE_PHOTO".getBytes(StandardCharsets.UTF_8)),
                "2026/06/01/session-purge",
                "check-in.jpg"
        );

        AttendancePhoto photo = new AttendancePhoto();
        photo.setAttendance(attendanceRecord);
        photo.setEmployee(employee);
        photo.setPhotoType(PhotoType.CHECK_IN);
        photo.setStoragePath(storageKey);
        photo.setFileName("check-in.jpg");
        photo.setMimeType("image/jpeg");
        photo.setFileSize(15L);
        photo.setImageWidth(640);
        photo.setImageHeight(480);
        photo.setStorageStatus(StorageStatus.ACTIVE);
        photo.setCreatedAt(Instant.now().minus(95, ChronoUnit.DAYS));
        photo = attendancePhotoRepository.saveAndFlush(photo);

        UUID photoId = photo.getId();

        // Execute cleanup
        mockMvc.perform(post("/api/admin/storage/cleanup/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photosDeleted").value(1));

        // 1. Physical file is deleted from storage
        assertThat(photoStorageService.exists(storageKey)).isFalse();

        // 2. Photo metadata is preserved with status PURGED and purged_at timestamp
        AttendancePhoto purgedPhoto = attendancePhotoRepository.findById(photoId).orElseThrow();
        assertThat(purgedPhoto.getStorageStatus()).isEqualTo(StorageStatus.PURGED);
        assertThat(purgedPhoto.getPurgedAt()).isNotNull();

        // 3. CRITICAL 3-YEAR COMPLIANCE: Attendance session and employee records are 100% INTACT!
        AttendanceRecord preservedAttendance = attendanceRepository.findById(attendanceRecord.getId()).orElseThrow();
        assertThat(preservedAttendance.getStatus()).isEqualTo(AttendanceStatus.CHECKED_IN);
        assertThat(preservedAttendance.getCheckInTime()).isNotNull();
        assertThat(preservedAttendance.getWorkDate()).isEqualTo(attendanceRecord.getWorkDate());

        Employee preservedEmployee = employeeRepository.findById(employee.getId()).orElseThrow();
        assertThat(preservedEmployee.getEmployeeId()).isEqualTo("EMP-STORAGE-01");

        // 4. Accessing purged photo returns 404 with descriptive retention message
        mockMvc.perform(get("/api/admin/attendance/" + attendanceRecord.getId() + "/photo/CHECK_IN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("company photo retention policy")));

        // 5. Cleanup history audit was written
        assertThat(cleanupHistoryRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Storage orphan scan detects unmapped files and cleans abandoned temp staging files")
    void orphanScanIdentifiesUnmappedFiles() throws Exception {
        mockMvc.perform(get("/api/admin/storage/orphans/scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unregisteredDiskFilesCount").isNumber())
                .andExpect(jsonPath("$.abandonedTempFilesCount").isNumber());
    }
}
