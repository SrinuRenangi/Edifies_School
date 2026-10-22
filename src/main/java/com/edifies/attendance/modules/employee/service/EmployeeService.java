package com.edifies.attendance.modules.employee.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edifies.attendance.common.exception.DuplicateResourceException;
import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.modules.employee.dto.CreateEmployeeRequest;
import com.edifies.attendance.modules.employee.dto.EmployeeResponse;
import com.edifies.attendance.modules.employee.dto.UpdateEmployeeRequest;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import com.edifies.attendance.modules.security.service.LoginAttemptService;

@Service
@Transactional
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           PasswordEncoder passwordEncoder,
                           LoginAttemptService loginAttemptService) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> getAllEmployees(String search) {
        List<Employee> employees;
        if (search != null && !search.trim().isEmpty()) {
            employees = employeeRepository.searchEmployees(search.trim());
        } else {
            employees = employeeRepository.findAllByOrderByCreatedAtDesc();
        }
        return employees.stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + id));
        return EmployeeResponse.from(employee);
    }

    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        String normalizedEmployeeId = request.employeeId().trim();
        if (employeeRepository.existsByEmployeeId(normalizedEmployeeId)) {
            throw new DuplicateResourceException("An employee with Employee ID '" + normalizedEmployeeId + "' already exists.");
        }

        String department = (request.department() != null && !request.department().isBlank())
                ? request.department().trim()
                : "General";

        String pinHash = passwordEncoder.encode(request.pin().trim());

        Employee employee = new Employee(
                normalizedEmployeeId,
                request.fullName().trim(),
                department,
                pinHash
        );

        Employee saved = employeeRepository.save(employee);
        log.info("Created new employee: id={}, employeeId='{}', fullName='{}'", 
                saved.getId(), saved.getEmployeeId(), saved.getFullName());
        return EmployeeResponse.from(saved);
    }

    public EmployeeResponse updateEmployee(UUID id, UpdateEmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + id));

        employee.setFullName(request.fullName().trim());
        if (request.department() != null && !request.department().isBlank()) {
            employee.setDepartment(request.department().trim());
        }

        Employee updated = employeeRepository.save(employee);
        log.info("Updated employee details: id={}, employeeId='{}'", updated.getId(), updated.getEmployeeId());
        return EmployeeResponse.from(updated);
        
    }

    public EmployeeResponse updateStatus(UUID id, EmployeeStatus status) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + id));

        employee.setStatus(status);
        Employee updated = employeeRepository.save(employee);
        log.info("Updated employee status: id={}, employeeId='{}', status={}", 
                updated.getId(), updated.getEmployeeId(), updated.getStatus());
        return EmployeeResponse.from(updated);
    }

    public void resetPin(UUID id, String newPin) {
        if (newPin == null || !newPin.matches("^[0-9]{4,6}$")) {
            throw new IllegalArgumentException("PIN must be between 4 and 6 numeric digits");
        }

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + id));

        employee.setPinHash(passwordEncoder.encode(newPin.trim()));
        employeeRepository.save(employee);

        loginAttemptService.resetLockout(employee.getEmployeeId());

        log.info("Administrator reset PIN for employeeId='{}' (UUID={})", 
                employee.getEmployeeId(), employee.getId());
    }
}
