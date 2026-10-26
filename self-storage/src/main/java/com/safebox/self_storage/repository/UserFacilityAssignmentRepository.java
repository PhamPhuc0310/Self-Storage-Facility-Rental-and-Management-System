package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.UserFacilityAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFacilityAssignmentRepository extends JpaRepository<UserFacilityAssignment, UUID> {
    @Query("SELECT DISTINCT a.userId FROM UserFacilityAssignment a WHERE a.facilityId IN :facilities AND a.status = 'ACTIVE' AND a.startDate <= :today AND (a.endDate IS NULL OR a.endDate >= :today)")
    List<UUID> findActiveUserIds(@Param("facilities") List<UUID> facilities, @Param("today") LocalDate today);
    @Query("SELECT DISTINCT a.facilityId FROM UserFacilityAssignment a WHERE a.userId = :userId AND a.status = 'ACTIVE' "
            + "AND a.startDate <= :today AND (a.endDate IS NULL OR a.endDate >= :today)")
    List<UUID> findActiveFacilityIds(@Param("userId") UUID userId, @Param("today") LocalDate today);

    @Query("SELECT a.userId FROM UserFacilityAssignment a WHERE a.facilityId = :facilityId AND a.status = 'ACTIVE' "
            + "AND a.startDate <= :today AND (a.endDate IS NULL OR a.endDate >= :today)")
    List<UUID> findActiveUserIdsByFacility(@Param("facilityId") UUID facilityId, @Param("today") LocalDate today);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM UserFacilityAssignment a "
            + "WHERE a.userId = :userId AND a.facilityId = :facilityId AND a.status = 'ACTIVE' "
            + "AND a.startDate <= :today AND (a.endDate IS NULL OR a.endDate >= :today)")
    boolean isUserAssignedToFacility(@Param("userId") UUID userId, @Param("facilityId") UUID facilityId, @Param("today") LocalDate today);

    @Query("SELECT a FROM UserFacilityAssignment a WHERE a.userId = :userId AND a.facilityId = :facilityId AND a.status = 'ACTIVE' AND a.startDate <= :today AND (a.endDate IS NULL OR a.endDate >= :today)")
    List<UserFacilityAssignment> findActiveAssignments(@Param("userId") UUID userId, @Param("facilityId") UUID facilityId, @Param("today") LocalDate today);
}
