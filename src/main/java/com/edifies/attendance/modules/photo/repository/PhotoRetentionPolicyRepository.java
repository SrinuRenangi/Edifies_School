package com.edifies.attendance.modules.photo.repository;

import com.edifies.attendance.modules.photo.model.PhotoRetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PhotoRetentionPolicyRepository extends JpaRepository<PhotoRetentionPolicy, UUID> {
}
