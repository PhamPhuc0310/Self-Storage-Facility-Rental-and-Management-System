package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.UserFacilityAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFacilityAssignmentRepository extends JpaRepository<UserFacilityAssignment, UUID> {
    @Query("SELECT a.facilityId FROM UserFacilityAssignment a WHERE a.userId = :userId AND a.status = 'ACTIVE' "
            + "AND a.startDate <= :today AND (a.endDate IS NULL OR a.endDate >= :today)")
    List<UUID> findActiveFacilityIds(@Param("userId") UUID userId, @Param("today") LocalDate today);
}
