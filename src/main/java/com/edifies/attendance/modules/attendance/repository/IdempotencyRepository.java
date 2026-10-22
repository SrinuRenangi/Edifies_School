package com.edifies.attendance.modules.attendance.repository;

import com.edifies.attendance.modules.attendance.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {

    Optional<IdempotencyRecord> findByKeyValue(String keyValue);
}
