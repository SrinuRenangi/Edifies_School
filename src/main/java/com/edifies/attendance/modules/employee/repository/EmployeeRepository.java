package com.edifies.attendance.modules.employee.repository;

import com.edifies.attendance.modules.employee.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    boolean existsByEmployeeId(String employeeId);

    Optional<Employee> findByEmployeeId(String employeeId);

    Optional<Employee> findByEmployeeIdIgnoreCase(String employeeId);

    List<Employee> findAllByOrderByCreatedAtDesc();

    long countByStatus(com.edifies.attendance.modules.employee.model.EmployeeStatus status);

    @Query("SELECT e FROM Employee e WHERE " +
           "LOWER(e.employeeId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.department) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "ORDER BY e.createdAt DESC")
    List<Employee> searchEmployees(@Param("search") String search);

    @Query("SELECT e FROM Employee e WHERE e.status = :status AND (" +
           "LOWER(e.employeeId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.department) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY e.fullName ASC")
    List<Employee> searchActiveEmployees(
            @Param("status") com.edifies.attendance.modules.employee.model.EmployeeStatus status,
            @Param("query") String query,
            org.springframework.data.domain.Pageable pageable);
}
