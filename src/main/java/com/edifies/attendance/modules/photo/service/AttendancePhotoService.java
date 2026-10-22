package com.edifies.attendance.modules.photo.service;

import com.edifies.attendance.common.exception.InvalidOperationException;
import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.photo.dto.PhotoValidationResult;
import com.edifies.attendance.modules.photo.model.AttendancePhoto;
import com.edifies.attendance.modules.photo.model.PhotoType;
import com.edifies.attendance.modules.photo.repository.AttendancePhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AttendancePhotoService {

    private static final Logger log = LoggerFactory.getLogger(AttendancePhotoService.class);

    private final PhotoValidationService photoValidationService;
    private final PhotoStorageService photoStorageService;
    private final AttendancePhotoRepository attendancePhotoRepository;

    @org.springframework.beans.factory.annotation.Value("${attendance.photo.low-storage-critical-threshold-bytes:262144000}")
    private long lowStorageCriticalThresholdBytes = 262144000L; // 250 MB

    public AttendancePhotoService(PhotoValidationService photoValidationService,
                                  PhotoStorageService photoStorageService,
                                  AttendancePhotoRepository attendancePhotoRepository) {
        this.photoValidationService = photoValidationService;
        this.photoStorageService = photoStorageService;
        this.attendancePhotoRepository = attendancePhotoRepository;
    }

    /**
     * Pre-validates a photo before database transaction begins.
     */
    public PhotoValidationResult validatePhoto(MultipartFile file) {
        return photoValidationService.validate(file);
    }

    /**
     * Validates and records an attendance photo for an attendance session.
     * Enforces the 1-photo-per-action invariant and stores file into structured local storage.
     */
    public AttendancePhoto recordPhoto(AttendanceRecord attendance,
                                       Employee employee,
                                       PhotoType photoType,
                                       MultipartFile file) {

        // 0. Low storage protection: reject uploads if usable disk space is critically low
        long usableSpace = photoStorageService.getUsableSpaceBytes();
        if (usableSpace > 0 && usableSpace <= lowStorageCriticalThresholdBytes) {
            log.error("Rejecting photo upload: storage usable space ({} bytes) is below critical threshold ({} bytes)",
                    usableSpace, lowStorageCriticalThresholdBytes);
            throw new InvalidOperationException("Critical storage alert: Photo storage disk space is critically low. Uploads paused until storage is expanded.");
        }

        // 1. Validate file content, dimensions, magic bytes, and size
        PhotoValidationResult validationResult = photoValidationService.validate(file);

        // 2. Enforce invariant: Only one photo per action (CHECK_IN or CHECK_OUT)
        if (attendancePhotoRepository.existsByAttendanceIdAndPhotoType(attendance.getId(), photoType)) {
            throw new InvalidOperationException(String.format("A %s photo has already been recorded for this attendance session.", photoType));
        }

        // 3. Construct deterministic, structured storage path: photos/YYYY/MM/DD/<attendanceId>/<action>.jpg
        LocalDate workDate = attendance.getWorkDate();
        String relativeDir = String.format("%d/%02d/%02d/%s",
                workDate.getYear(),
                workDate.getMonthValue(),
                workDate.getDayOfMonth(),
                attendance.getId().toString());

        String extension = switch (validationResult.format().toUpperCase()) {
            case "PNG" -> ".png";
            case "WEBP" -> ".webp";
            default -> ".jpg";
        };

        String fileName = (photoType == PhotoType.CHECK_IN ? "check-in" : "check-out") + extension;

        String storagePath = null;
        try {
            storagePath = photoStorageService.store(file.getInputStream(), relativeDir, fileName);

            // 4. Save metadata to PostgreSQL
            AttendancePhoto photo = new AttendancePhoto(
                    attendance,
                    employee,
                    photoType,
                    storagePath,
                    fileName,
                    validationResult.mimeType(),
                    validationResult.fileSize(),
                    validationResult.width(),
                    validationResult.height()
            );

            AttendancePhoto saved = attendancePhotoRepository.save(photo);

            // 5. Update attendance record indicator flags
            if (photoType == PhotoType.CHECK_IN) {
                attendance.setHasCheckInPhoto(true);
            } else {
                attendance.setHasCheckOutPhoto(true);
            }

            log.info("Attendance photo recorded: photoId='{}', attendanceId='{}', type='{}', storagePath='{}'",
                    saved.getId(), attendance.getId(), photoType, storagePath);

            return saved;
        } catch (Exception e) {
            // Clean up staged file if database persistence or stream reading fails
            if (storagePath != null) {
                try {
                    photoStorageService.delete(storagePath);
                } catch (Exception cleanupEx) {
                    log.warn("Failed to clean up photo file after transaction failure: {}", storagePath, cleanupEx);
                }
            }
            log.error("Failed to persist attendance photo for attendanceId='{}', type='{}'", attendance.getId(), photoType, e);
            if (e instanceof InvalidOperationException ioe) {
                throw ioe;
            }
            throw new InvalidOperationException("Failed to save attendance photo. Attendance was not recorded: " + e.getMessage());
        }
    }

    /**
     * Retrieves an authorized binary photo resource.
     */
    @Transactional(readOnly = true)
    public Resource getAuthorizedPhoto(UUID attendanceId, PhotoType photoType) {
        AttendancePhoto photo = attendancePhotoRepository.findByAttendanceIdAndPhotoType(attendanceId, photoType)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Attendance photo not found for attendanceId='%s' and type='%s'", attendanceId, photoType)));

        if (photo.getStorageStatus() == com.edifies.attendance.modules.photo.model.StorageStatus.PURGED) {
            throw new ResourceNotFoundException("Attendance photo has expired and was removed in accordance with the company photo retention policy.");
        }
        if (photo.getStorageStatus() == com.edifies.attendance.modules.photo.model.StorageStatus.MISSING) {
            throw new ResourceNotFoundException("Attendance photo file is missing from storage.");
        }

        return photoStorageService.retrieve(photo.getStoragePath());
    }

    /**
     * Retrieves photo metadata for an attendance session.
     */
    @Transactional(readOnly = true)
    public Optional<AttendancePhoto> getPhotoMetadata(UUID attendanceId, PhotoType photoType) {
        return attendancePhotoRepository.findByAttendanceIdAndPhotoType(attendanceId, photoType);
    }
}
