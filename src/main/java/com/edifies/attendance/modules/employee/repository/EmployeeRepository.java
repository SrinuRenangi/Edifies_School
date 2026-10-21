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

    List<Employee> findAllByOrderByCreatedAtDesc();

    @Query("SELECT e FROM Employee e WHERE " +
           "LOWER(e.employeeId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.department) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "ORDER BY e.createdAt DESC")
    List<Employee> searchEmployees(@Param("search") String search);
}
