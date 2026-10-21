package com.edifies.attendance.modules.employee.dto;

import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;

import java.time.Instant;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String employeeId,
        String fullName,
        String department,
        EmployeeStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeId(),
                employee.getFullName(),
                employee.getDepartment(),
                employee.getStatus(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}
