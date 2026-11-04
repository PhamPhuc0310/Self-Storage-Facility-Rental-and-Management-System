package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.UserFacilityAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFacilityAssignmentRepository extends JpaRepository<UserFacilityAssignment, UUID> {
    boolean existsByFacilityId(UUID facilityId);
    @Query("SELECT DISTINCT a.userId FROM UserFacilityAssignment a WHERE a.facilityId IN :facilities AND a.status = 'ACTIVE' AND a.startDate <= :today AND (a.endDate IS NULL OR a.endDate >= :today)")
    List<UUID> findActiveUserIds(@Param("facilities") List<UUID> facilities, @Param("today") LocalDate today);
    @Query("SELECT DISTINCT f.facilityId FROM Facility f WHERE f.facilityId IN "
            + "(SELECT a.facilityId FROM UserFacilityAssignment a WHERE a.userId = :userId AND a.status = 'ACTIVE' "
            + "AND a.startDate <= :today AND (a.endDate IS NULL OR a.endDate >= :today)) "
            + "OR (f.regionCode IN (SELECT r.regionCode FROM StaffRegionAssignment r WHERE r.userId = :userId AND r.status = 'ACTIVE') "
            + "AND EXISTS (SELECT u.userId FROM User u WHERE u.userId = :userId AND u.status = 'ACTIVE' AND u.role.roleName = 'FACILITY_STAFF'))")
    List<UUID> findActiveFacilityIds(@Param("userId") UUID userId, @Param("today") LocalDate today);

    @Query("SELECT DISTINCT u.userId FROM User u WHERE u.status = 'ACTIVE' AND (u.userId IN "
            + "(SELECT a.userId FROM UserFacilityAssignment a WHERE a.facilityId = :facilityId AND a.status = 'ACTIVE' "
            + "AND a.startDate <= :today AND (a.endDate IS NULL OR a.endDate >= :today)) "
            + "OR (u.role.roleName = 'FACILITY_STAFF' AND EXISTS (SELECT r.assignmentId FROM StaffRegionAssignment r, Facility f "
            + "WHERE r.userId = u.userId AND r.status = 'ACTIVE' AND f.facilityId = :facilityId AND f.regionCode = r.regionCode)))")
    List<UUID> findActiveUserIdsByFacility(@Param("facilityId") UUID facilityId, @Param("today") LocalDate today);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Facility f WHERE f.facilityId = :facilityId AND ("
            + "f.facilityId IN (SELECT a.facilityId FROM UserFacilityAssignment a WHERE a.userId = :userId AND a.status = 'ACTIVE' "
            + "AND a.startDate <= :today AND (a.endDate IS NULL OR a.endDate >= :today)) "
            + "OR (f.regionCode IN (SELECT r.regionCode FROM StaffRegionAssignment r WHERE r.userId = :userId AND r.status = 'ACTIVE') "
            + "AND EXISTS (SELECT u.userId FROM User u WHERE u.userId = :userId AND u.status = 'ACTIVE' AND u.role.roleName = 'FACILITY_STAFF')))")
    boolean isUserAssignedToFacility(@Param("userId") UUID userId, @Param("facilityId") UUID facilityId, @Param("today") LocalDate today);
}
