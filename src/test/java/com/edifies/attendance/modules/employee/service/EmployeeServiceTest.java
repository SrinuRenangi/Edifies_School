package com.edifies.attendance.modules.employee.service;

import com.edifies.attendance.common.exception.DuplicateResourceException;
import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.modules.employee.dto.CreateEmployeeRequest;
import com.edifies.attendance.modules.employee.dto.EmployeeResponse;
import com.edifies.attendance.modules.employee.dto.UpdateEmployeeRequest;
import com.edifies.attendance.modules.employee.model.Employee;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private com.edifies.attendance.modules.security.service.LoginAttemptService loginAttemptService;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee employee;
    private UUID employeeId;

    @BeforeEach
    void setUp() {
        employeeId = UUID.randomUUID();
        employee = new Employee("EMP001", "John Doe", "Engineering", "hashedPin");
        employee.setId(employeeId);
    }

    @Test
    @DisplayName("Successfully creates new employee")
    void shouldCreateEmployee() {
        CreateEmployeeRequest request = new CreateEmployeeRequest("EMP001", "John Doe", "Engineering", "1234");

        when(employeeRepository.existsByEmployeeId("EMP001")).thenReturn(false);
        when(passwordEncoder.encode("1234")).thenReturn("hashedPin");
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        EmployeeResponse response = employeeService.createEmployee(request);

        assertNotNull(response);
        assertEquals("EMP001", response.employeeId());
        assertEquals("John Doe", response.fullName());
        assertEquals("Engineering", response.department());
        assertEquals(EmployeeStatus.ACTIVE, response.status());
        verify(passwordEncoder).encode("1234");
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    @DisplayName("Rejects creation when Employee ID already exists")
    void shouldRejectDuplicateEmployeeId() {
        CreateEmployeeRequest request = new CreateEmployeeRequest("EMP001", "John Doe", "Engineering", "1234");

        when(employeeRepository.existsByEmployeeId("EMP001")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> employeeService.createEmployee(request)
        );

        assertTrue(exception.getMessage().contains("already exists"));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Updates employee details successfully")
    void shouldUpdateEmployeeDetails() {
        UpdateEmployeeRequest request = new UpdateEmployeeRequest("Johnathan Doe", "Operations");

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        EmployeeResponse response = employeeService.updateEmployee(employeeId, request);

        assertNotNull(response);
        verify(employeeRepository).save(employee);
    }

    @Test
    @DisplayName("Updates employee status to INACTIVE")
    void shouldDeactivateEmployee() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        EmployeeResponse response = employeeService.updateStatus(employeeId, EmployeeStatus.INACTIVE);

        assertNotNull(response);
        assertEquals(EmployeeStatus.INACTIVE, employee.getStatus());
        verify(employeeRepository).save(employee);
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when employee ID does not exist")
    void shouldThrowWhenEmployeeNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(employeeRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> employeeService.getEmployeeById(unknownId));
    }

    @Test
    @DisplayName("Searches employees by name or ID")
    void shouldSearchEmployees() {
        when(employeeRepository.searchEmployees("John")).thenReturn(List.of(employee));

        List<EmployeeResponse> results = employeeService.getAllEmployees("John");

        assertEquals(1, results.size());
        assertEquals("John Doe", results.get(0).fullName());
    }

    @Test
    @DisplayName("Successfully resets employee PIN and resets lockout")
    void shouldResetEmployeePin() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(passwordEncoder.encode("5678")).thenReturn("newHashedPin");

        employeeService.resetPin(employeeId, "5678");

        assertEquals("newHashedPin", employee.getPinHash());
        verify(employeeRepository).save(employee);
        verify(loginAttemptService).resetLockout("EMP001");
    }
}
