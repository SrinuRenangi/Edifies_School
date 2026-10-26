package com.edifies.attendance.modules.employee.controller;

import com.edifies.attendance.modules.employee.dto.CreateEmployeeRequest;
import com.edifies.attendance.modules.employee.dto.EmployeeResponse;
import com.edifies.attendance.modules.employee.dto.UpdateEmployeeRequest;
import com.edifies.attendance.modules.employee.dto.UpdateStatusRequest;
import com.edifies.attendance.modules.employee.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> getAllEmployees(
            @RequestParam(required = false) String search) {
        List<EmployeeResponse> employees = employeeService.getAllEmployees(search);
        return ResponseEntity.ok(employees);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getEmployeeById(@PathVariable UUID id) {
        EmployeeResponse employee = employeeService.getEmployeeById(id);
        return ResponseEntity.ok(employee);
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse created = employeeService.createEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<EmployeeResponse> updateEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        EmployeeResponse updated = employeeService.updateEmployee(id, request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EmployeeResponse> updateEmployeeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        EmployeeResponse updated = employeeService.updateStatus(id, request.status());
        return ResponseEntity.ok(updated);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/reset-pin")
    public ResponseEntity<Void> resetPin(
            @PathVariable UUID id,
            @Valid @RequestBody com.edifies.attendance.modules.employee.dto.ResetPinRequest request) {
        employeeService.resetPin(id, request.newPin());
        return ResponseEntity.noContent().build();
    }
}
