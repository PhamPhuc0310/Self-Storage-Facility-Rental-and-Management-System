package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.PickupAppointment;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PickupAppointmentRepository extends JpaRepository<PickupAppointment, UUID> {
    Optional<PickupAppointment> findFirstByContractIdOrderByCreatedAtDesc(UUID id);
    Optional<PickupAppointment> findFirstByContractIdAndStatus(UUID id, String status);
    boolean existsByContractIdAndStatus(UUID id, String status);
    List<PickupAppointment> findByFacilityIdInAndStatusAndAssignedStaffIdIsNullAndAppointmentTimeGreaterThanEqualOrderByAppointmentTimeAsc(
            List<UUID> facilityIds, String status, LocalDateTime from);
    List<PickupAppointment> findTop20ByFacilityIdInAndAssignedStaffIdAndStatusAndAppointmentTimeGreaterThanEqualOrderByAppointmentTimeAsc(
            List<UUID> facilityIds, UUID staffId, String status, LocalDateTime from);
    @Query("select a from PickupAppointment a where a.facilityId in :facilities and a.appointmentTime >= :from and a.appointmentTime < :to and (:staffId is null or a.assignedStaffId = :staffId) and (:status is null or a.status = :status) order by a.appointmentTime")
    List<PickupAppointment> daily(@Param("facilities") List<UUID> facilities, @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to, @Param("staffId") UUID staffId, @Param("status") String status);
}
