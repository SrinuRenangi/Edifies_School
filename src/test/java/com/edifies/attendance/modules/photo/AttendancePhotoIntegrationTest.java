package com.edifies.attendance.modules.photo;

import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.attendance.repository.AttendanceCorrectionRepository;
import com.edifies.attendance.modules.attendance.repository.AttendanceRepository;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.kiosk.service.KioskSessionService;
import com.edifies.attendance.modules.photo.model.AttendancePhoto;
import com.edifies.attendance.modules.photo.model.PhotoType;
import com.edifies.attendance.modules.photo.repository.AttendancePhotoRepository;
import com.edifies.attendance.modules.photo.service.PhotoStorageService;
import com.edifies.attendance.modules.security.model.AdminUser;
import com.edifies.attendance.modules.security.repository.AdminUserRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = "app.kiosk.device-enforcement-enabled=false")
@AutoConfigureMockMvc
@Transactional
class AttendancePhotoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AttendancePhotoRepository attendancePhotoRepository;

    @Autowired
    private PhotoStorageService photoStorageService;

    @Autowired
    private KioskSessionService kioskSessionService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AttendanceCorrectionRepository attendanceCorrectionRepository;

    private Employee employee;
    private String kioskToken;

    @BeforeEach
    void setUp() {
        attendanceCorrectionRepository.deleteAll();
        attendancePhotoRepository.deleteAll();
        attendanceRepository.deleteAll();
        employeeRepository.deleteAll();

        employee = new Employee();
        employee.setEmployeeId("EMP-PHOTO-101");
        employee.setFullName("Florence Nightingale");
        employee.setDepartment("Healthcare");
        employee.setPinHash(passwordEncoder.encode("1234"));
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee = employeeRepository.saveAndFlush(employee);

        kioskToken = kioskSessionService.createSession(employee).token();
    }

    @AfterEach
    void tearDown() {
        attendanceCorrectionRepository.deleteAll();
        attendancePhotoRepository.deleteAll();
        attendanceRepository.deleteAll();
        employeeRepository.deleteAll();
    }


    private byte[] createSyntheticImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.drawString("INTEGRATION TEST EVIDENCE", 15, 25);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Should reject anonymous access to admin attendance photo retrieval")
    void shouldRejectAnonymousPhotoRetrieval() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/admin/attendance/" + randomId + "/photo/CHECK_IN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should successfully record check-in with verified photo evidence")
    void shouldCheckInWithPhotoEvidence() throws Exception {
        byte[] photoBytes = createSyntheticImage(640, 480);
        MockMultipartFile photoPart = new MockMultipartFile("photo", "camera.jpg", "image/jpeg", photoBytes);

        mockMvc.perform(multipart("/api/kiosk/attendance/check-in")
                        .file(photoPart)
                        .header("X-Kiosk-Session", kioskToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.hasCheckInPhoto").value(true))
                .andExpect(jsonPath("$.hasCheckOutPhoto").value(false));

        // Verify database records
        Optional<AttendanceRecord> recordOpt = attendanceRepository.findByEmployeeIdAndStatus(employee.getId(), AttendanceStatus.CHECKED_IN);
        assertThat(recordOpt).isPresent();
        AttendanceRecord record = recordOpt.get();
        assertThat(record.isHasCheckInPhoto()).isTrue();

        Optional<AttendancePhoto> photoOpt = attendancePhotoRepository.findByAttendanceIdAndPhotoType(record.getId(), PhotoType.CHECK_IN);
        assertThat(photoOpt).isPresent();
        AttendancePhoto photo = photoOpt.get();
        assertThat(photo.getImageWidth()).isEqualTo(640);
        assertThat(photo.getImageHeight()).isEqualTo(480);
        assertThat(photo.getMimeType()).isEqualTo("image/jpeg");

        // Verify storage file exists on disk
        assertThat(photoStorageService.exists(photo.getStoragePath())).isTrue();
    }

    @Test
    @DisplayName("Should successfully record check-out with verified photo evidence")
    void shouldCheckOutWithPhotoEvidence() throws Exception {
        // First, check in
        byte[] checkInBytes = createSyntheticImage(640, 480);
        MockMultipartFile checkInPhoto = new MockMultipartFile("photo", "in.jpg", "image/jpeg", checkInBytes);

        mockMvc.perform(multipart("/api/kiosk/attendance/check-in")
                        .file(checkInPhoto)
                        .header("X-Kiosk-Session", kioskToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        // Now, check out with photo
        byte[] checkOutBytes = createSyntheticImage(640, 480);
        MockMultipartFile checkOutPhoto = new MockMultipartFile("photo", "out.jpg", "image/jpeg", checkOutBytes);

        mockMvc.perform(multipart("/api/kiosk/attendance/check-out")
                        .file(checkOutPhoto)
                        .header("X-Kiosk-Session", kioskToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.hasCheckInPhoto").value(true))
                .andExpect(jsonPath("$.hasCheckOutPhoto").value(true));

        // Verify both photos are present in metadata repository
        Optional<AttendanceRecord> recordOpt = attendanceRepository.findByEmployeeIdAndWorkDate(employee.getId(), LocalDate.now());
        assertThat(recordOpt).isPresent();
        AttendanceRecord record = recordOpt.get();

        assertThat(attendancePhotoRepository.existsByAttendanceIdAndPhotoType(record.getId(), PhotoType.CHECK_IN)).isTrue();
        assertThat(attendancePhotoRepository.existsByAttendanceIdAndPhotoType(record.getId(), PhotoType.CHECK_OUT)).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Administrator should retrieve photo binary with secure headers")
    void adminShouldRetrieveAuthorizedPhotoBinary() throws Exception {
        // Perform check-in with photo
        byte[] photoBytes = createSyntheticImage(640, 480);
        MockMultipartFile photoPart = new MockMultipartFile("photo", "camera.jpg", "image/jpeg", photoBytes);

        mockMvc.perform(multipart("/api/kiosk/attendance/check-in")
                        .file(photoPart)
                        .header("X-Kiosk-Session", kioskToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        AttendanceRecord record = attendanceRepository.findByEmployeeIdAndStatus(employee.getId(), AttendanceStatus.CHECKED_IN).orElseThrow();

        // Admin retrieves photo
        mockMvc.perform(get("/api/admin/attendance/" + record.getId() + "/photo/CHECK_IN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-cache, no-store, must-revalidate"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"check-in.jpg\""))
                .andExpect(content().bytes(photoBytes));
    }

    @Test
    @DisplayName("Corrupt photo upload should fail and NOT create false attendance record")
    void corruptPhotoShouldFailWithoutFalseAttendance() throws Exception {
        byte[] corruptBytes = "NOT_A_VALID_IMAGE_DATA".getBytes();
        MockMultipartFile corruptFile = new MockMultipartFile("photo", "fake.jpg", "image/jpeg", corruptBytes);

        mockMvc.perform(multipart("/api/kiosk/attendance/check-in")
                        .file(corruptFile)
                        .header("X-Kiosk-Session", kioskToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        // Verify zero false attendance created
        assertThat(attendanceRepository.count()).isEqualTo(0);
        assertThat(attendancePhotoRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Idempotent submission with photo returns cached response without duplicate records")
    void idempotentCheckInWithPhoto() throws Exception {
        byte[] photoBytes = createSyntheticImage(640, 480);
        MockMultipartFile photoPart = new MockMultipartFile("photo", "camera.jpg", "image/jpeg", photoBytes);
        String idempotencyKey = UUID.randomUUID().toString();

        // 1st request
        mockMvc.perform(multipart("/api/kiosk/attendance/check-in")
                        .file(photoPart)
                        .header("X-Kiosk-Session", kioskToken)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk());

        // 2nd request with same idempotency key
        mockMvc.perform(multipart("/api/kiosk/attendance/check-in")
                        .file(photoPart)
                        .header("X-Kiosk-Session", kioskToken)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk());

        // Verify exactly 1 attendance record and 1 photo metadata record
        assertThat(attendanceRepository.count()).isEqualTo(1);
        assertThat(attendancePhotoRepository.count()).isEqualTo(1);
    }
}
