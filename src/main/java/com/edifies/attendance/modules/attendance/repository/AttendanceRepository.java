package com.edifies.attendance.modules.attendance.repository;

import com.edifies.attendance.modules.attendance.model.AttendanceRecord;
import com.edifies.attendance.modules.attendance.model.AttendanceStatus;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID>, JpaSpecificationExecutor<AttendanceRecord> {

    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.id = :employeeId AND a.status = :status")
    Optional<AttendanceRecord> findByEmployeeIdAndStatus(@Param("employeeId") UUID employeeId, @Param("status") AttendanceStatus status);

    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.id = :employeeId AND a.workDate = :workDate")
    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(@Param("employeeId") UUID employeeId, @Param("workDate") LocalDate workDate);

    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.id = :employeeId AND a.workDate = :workDate AND a.status != :excludedStatus")
    Optional<AttendanceRecord> findByEmployeeIdAndWorkDateAndStatusNot(@Param("employeeId") UUID employeeId, @Param("workDate") LocalDate workDate, @Param("excludedStatus") AttendanceStatus excludedStatus);

    @Query("SELECT COUNT(a) > 0 FROM AttendanceRecord a WHERE a.employee.id = :employeeId AND a.workDate = :workDate AND a.status != :excludedStatus")
    boolean existsByEmployeeIdAndWorkDateAndStatusNot(@Param("employeeId") UUID employeeId, @Param("workDate") LocalDate workDate, @Param("excludedStatus") AttendanceStatus excludedStatus);

    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.id = :employeeId ORDER BY a.workDate DESC, a.checkInTime DESC")
    List<AttendanceRecord> findAllByEmployeeId(@Param("employeeId") UUID employeeId);

    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.id = :employeeId AND a.workDate BETWEEN :fromDate AND :toDate ORDER BY a.workDate DESC, a.checkInTime DESC")
    Page<AttendanceRecord> findByEmployeeIdAndWorkDateBetween(
            @Param("employeeId") UUID employeeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee.id = :employeeId ORDER BY a.workDate DESC, a.checkInTime DESC")
    Page<AttendanceRecord> findByEmployeeId(
            @Param("employeeId") UUID employeeId,
            Pageable pageable
    );

    @Query("SELECT a FROM AttendanceRecord a JOIN FETCH a.employee WHERE a.workDate BETWEEN :fromDate AND :toDate ORDER BY a.employee.employeeId ASC, a.workDate ASC, a.checkInTime ASC")
    List<AttendanceRecord> findAllWithEmployeeByWorkDateBetween(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("SELECT a FROM AttendanceRecord a JOIN FETCH a.employee WHERE a.employee.id = :employeeId AND a.workDate BETWEEN :fromDate AND :toDate ORDER BY a.workDate ASC, a.checkInTime ASC")
    List<AttendanceRecord> findAllWithEmployeeByEmployeeIdAndWorkDateBetween(
            @Param("employeeId") UUID employeeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("SELECT COUNT(DISTINCT a.employee.id) FROM AttendanceRecord a WHERE a.workDate = :workDate AND a.status = :status AND a.employee.status = :empStatus")
    long countDistinctEmployeesByWorkDateAndStatusAndEmployeeStatus(
            @Param("workDate") LocalDate workDate,
            @Param("status") AttendanceStatus status,
            @Param("empStatus") EmployeeStatus empStatus
    );

    @Query("SELECT COUNT(a) FROM AttendanceRecord a WHERE a.workDate = :workDate AND a.status = :status")
    long countByWorkDateAndStatus(
            @Param("workDate") LocalDate workDate,
            @Param("status") AttendanceStatus status
    );

    @Query("SELECT COUNT(a) FROM AttendanceRecord a WHERE (a.workDate = :workDate AND a.status = :autoClosedStatus) OR (a.workDate < :workDate AND a.status = :openStatus)")
    long countIncompleteSessions(
            @Param("workDate") LocalDate workDate,
            @Param("autoClosedStatus") AttendanceStatus autoClosedStatus,
            @Param("openStatus") AttendanceStatus openStatus
    );

    @Query("SELECT COUNT(DISTINCT a.employee.id) FROM AttendanceRecord a WHERE a.workDate = :workDate AND a.status != :voidedStatus AND a.employee.status = :empStatus")
    long countDistinctEmployeesWithAttendanceOnWorkDate(
            @Param("workDate") LocalDate workDate,
            @Param("voidedStatus") AttendanceStatus voidedStatus,
            @Param("empStatus") EmployeeStatus empStatus
    );

    @Query("SELECT a FROM AttendanceRecord a JOIN FETCH a.employee WHERE a.workDate <= :workDate ORDER BY a.updatedAt DESC")
    List<AttendanceRecord> findRecentActivityRecords(@Param("workDate") LocalDate workDate, Pageable pageable);

    @Query("SELECT a FROM AttendanceRecord a JOIN FETCH a.employee WHERE (a.workDate = :workDate AND a.status = :autoClosedStatus) OR (a.workDate < :workDate AND a.status = :openStatus) OR (a.workDate = :workDate AND a.status = :openStatus AND a.checkInTime < :overdueThreshold) ORDER BY a.workDate DESC, a.checkInTime DESC")
    List<AttendanceRecord> findAttentionRequiredRecords(
            @Param("workDate") LocalDate workDate,
            @Param("autoClosedStatus") AttendanceStatus autoClosedStatus,
            @Param("openStatus") AttendanceStatus openStatus,
            @Param("overdueThreshold") Instant overdueThreshold,
            Pageable pageable
    );
}
