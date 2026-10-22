package com.edifies.attendance.modules.photo.repository;

import com.edifies.attendance.modules.photo.model.AttendancePhoto;
import com.edifies.attendance.modules.photo.model.PhotoType;
import com.edifies.attendance.modules.photo.model.StorageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendancePhotoRepository extends JpaRepository<AttendancePhoto, UUID> {

    Optional<AttendancePhoto> findByAttendanceIdAndPhotoType(UUID attendanceId, PhotoType photoType);

    boolean existsByAttendanceIdAndPhotoType(UUID attendanceId, PhotoType photoType);

    List<AttendancePhoto> findByAttendanceId(UUID attendanceId);

    List<AttendancePhoto> findByEmployeeId(UUID employeeId);

    List<AttendancePhoto> findByStorageStatusAndCreatedAtBefore(StorageStatus storageStatus, Instant cutoff);

    List<AttendancePhoto> findByStorageStatusAndCreatedAtBeforeOrderByCreatedAtAsc(StorageStatus storageStatus, Instant cutoff, Pageable pageable);

    List<AttendancePhoto> findByStorageStatus(StorageStatus storageStatus);

    long countByStorageStatus(StorageStatus storageStatus);

    long countByStorageStatusAndCreatedAtBefore(StorageStatus storageStatus, Instant cutoff);

    @Query("SELECT COALESCE(SUM(p.fileSize), 0) FROM AttendancePhoto p WHERE p.storageStatus = :status")
    long sumFileSizeByStorageStatus(@Param("status") StorageStatus status);

    @Query("SELECT COALESCE(SUM(p.fileSize), 0) FROM AttendancePhoto p WHERE p.storageStatus = :status AND p.createdAt < :cutoff")
    long sumFileSizeByStorageStatusAndCreatedAtBefore(@Param("status") StorageStatus status, @Param("cutoff") Instant cutoff);
}
