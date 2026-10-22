package com.edifies.attendance.modules.attendance.service;

import com.edifies.attendance.modules.attendance.dto.AttendanceResponse;
import com.edifies.attendance.modules.attendance.model.IdempotencyRecord;
import com.edifies.attendance.modules.attendance.repository.IdempotencyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceResponse> getCachedResponse(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        return idempotencyRepository.findById(idempotencyKey.trim())
                .flatMap(record -> {
                    try {
                        AttendanceResponse response = objectMapper.readValue(record.getResponseBody(), AttendanceResponse.class);
                        log.info("Idempotent hit for key='{}': returning cached response for employeeCode='{}'", 
                                idempotencyKey, response.employeeCode());
                        return Optional.of(response);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize cached idempotency payload for key='{}'", idempotencyKey, e);
                        return Optional.empty();
                    }
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResponse(String idempotencyKey, String endpoint, UUID employeeId, int statusCode, AttendanceResponse response) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(response);
            IdempotencyRecord record = new IdempotencyRecord(
                    idempotencyKey.trim(),
                    endpoint,
                    employeeId,
                    statusCode,
                    json
            );
            idempotencyRepository.saveAndFlush(record);
            log.debug("Recorded idempotency key='{}' for endpoint='{}'", idempotencyKey, endpoint);
        } catch (Exception e) {
            log.warn("Could not record idempotency key='{}': {}", idempotencyKey, e.getMessage());
        }
    }
}
