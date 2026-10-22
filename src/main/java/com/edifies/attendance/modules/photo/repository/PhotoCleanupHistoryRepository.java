package com.edifies.attendance.modules.photo.repository;

import com.edifies.attendance.modules.photo.model.CleanupRunStatus;
import com.edifies.attendance.modules.photo.model.PhotoCleanupHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhotoCleanupHistoryRepository extends JpaRepository<PhotoCleanupHistory, UUID> {

    Page<PhotoCleanupHistory> findAllByOrderByStartedAtDesc(Pageable pageable);

    Optional<PhotoCleanupHistory> findTopByStatusOrderByStartedAtDesc(CleanupRunStatus status);

    Optional<PhotoCleanupHistory> findTopByOrderByStartedAtDesc();
}
