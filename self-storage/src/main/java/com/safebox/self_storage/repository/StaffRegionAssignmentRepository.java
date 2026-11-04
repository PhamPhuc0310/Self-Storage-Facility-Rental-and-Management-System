package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.StaffRegionAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRegionAssignmentRepository extends JpaRepository<StaffRegionAssignment, UUID> {
    List<StaffRegionAssignment> findByUserIdAndStatus(UUID userId, String status);
    boolean existsByUserIdAndRegionCodeAndStatus(UUID userId, String regionCode, String status);
}
