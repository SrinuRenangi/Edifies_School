package com.edifies.attendance.modules.attendance.repository;

import com.edifies.attendance.modules.attendance.model.AttendanceCorrection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, UUID>, JpaSpecificationExecutor<AttendanceCorrection> {

    @EntityGraph(attributePaths = {"employee", "attendanceRecord"})
    List<AttendanceCorrection> findByAttendanceRecordIdOrderByCreatedAtDesc(UUID attendanceRecordId);

    @EntityGraph(attributePaths = {"employee", "attendanceRecord"})
    Page<AttendanceCorrection> findByAttendanceRecordIdOrderByCreatedAtDesc(UUID attendanceRecordId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"employee", "attendanceRecord"})
    Page<AttendanceCorrection> findAll(Specification<AttendanceCorrection> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "attendanceRecord"})
    List<AttendanceCorrection> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
