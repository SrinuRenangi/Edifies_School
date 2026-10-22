package com.edifies.attendance.modules.employee.controller;

import com.edifies.attendance.common.exception.DuplicateResourceException;
import com.edifies.attendance.common.exception.GlobalExceptionHandler;
import com.edifies.attendance.modules.employee.dto.CreateEmployeeRequest;
import com.edifies.attendance.modules.employee.dto.EmployeeResponse;
import com.edifies.attendance.modules.employee.dto.ResetPinRequest;
import com.edifies.attendance.modules.employee.dto.UpdateStatusRequest;
import com.edifies.attendance.modules.employee.model.EmployeeStatus;
import com.edifies.attendance.modules.employee.service.EmployeeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeService employeeService;

    @Test
    @DisplayName("GET /api/admin/employees returns 200 and list")
    void shouldReturnAllEmployees() throws Exception {
        UUID id = UUID.randomUUID();
        EmployeeResponse response = new EmployeeResponse(
                id, "EMP001", "Alice Smith", "HR",
                EmployeeStatus.ACTIVE, Instant.now(), Instant.now()
        );

        when(employeeService.getAllEmployees(null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/admin/employees"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].employeeId").value("EMP001"))
                .andExpect(jsonPath("$[0].fullName").value("Alice Smith"));
    }

    @Test
    @DisplayName("POST /api/admin/employees creates employee with 201 Created")
    void shouldCreateEmployee() throws Exception {
        UUID id = UUID.randomUUID();
        CreateEmployeeRequest request = new CreateEmployeeRequest("EMP002", "Bob Johnson", "Engineering", "1234");
        EmployeeResponse response = new EmployeeResponse(
                id, "EMP002", "Bob Johnson", "Engineering",
                EmployeeStatus.ACTIVE, Instant.now(), Instant.now()
        );

        when(employeeService.createEmployee(any(CreateEmployeeRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").value("EMP002"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/admin/employees rejects blank employeeId with 400 Bad Request")
    void shouldRejectInvalidInput() throws Exception {
        CreateEmployeeRequest invalidRequest = new CreateEmployeeRequest("", "", "Engineering", "1234");

        mockMvc.perform(post("/api/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.employeeId").exists())
                .andExpect(jsonPath("$.validationErrors.fullName").exists());
    }

    @Test
    @DisplayName("POST /api/admin/employees returns 409 Conflict when duplicate Employee ID exists")
    void shouldHandleDuplicateIdConflict() throws Exception {
        CreateEmployeeRequest request = new CreateEmployeeRequest("EMP001", "Alice Smith", "HR", "1234");

        when(employeeService.createEmployee(any(CreateEmployeeRequest.class)))
                .thenThrow(new DuplicateResourceException("An employee with Employee ID 'EMP001' already exists."));

        mockMvc.perform(post("/api/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An employee with Employee ID 'EMP001' already exists."));
    }

    @Test
    @DisplayName("PATCH /api/admin/employees/{id}/status toggles status with 200 OK")
    void shouldUpdateStatus() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateStatusRequest request = new UpdateStatusRequest(EmployeeStatus.INACTIVE);
        EmployeeResponse response = new EmployeeResponse(
                id, "EMP001", "Alice Smith", "HR",
                EmployeeStatus.INACTIVE, Instant.now(), Instant.now()
        );

        when(employeeService.updateStatus(eq(id), eq(EmployeeStatus.INACTIVE))).thenReturn(response);

        mockMvc.perform(patch("/api/admin/employees/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("POST /api/admin/employees/{id}/reset-pin resets PIN with 204 No Content")
    void shouldResetPin() throws Exception {
        UUID id = UUID.randomUUID();
        ResetPinRequest request = new ResetPinRequest("5678");

        mockMvc.perform(post("/api/admin/employees/" + id + "/reset-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(employeeService).resetPin(id, "5678");
    }
}
