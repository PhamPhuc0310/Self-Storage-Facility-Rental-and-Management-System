package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.Renewal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository truy xuất dữ liệu từ bảng RENEWALS (Yêu cầu gia hạn hợp đồng thuê kho - UC17).
 */
@Repository
public interface RenewalRepository extends JpaRepository<Renewal, UUID> {

    List<Renewal> findByContractIdOrderByRequestedAtDesc(UUID contractId);

    List<Renewal> findByContractIdAndStatus(UUID contractId, String status);

    List<Renewal> findByContractIdAndStatusIn(UUID contractId, Collection<String> statuses);

    Optional<Renewal> findFirstByContractIdAndStatusInOrderByRequestedAtDesc(
            UUID contractId, Collection<String> statuses);

    @Query("SELECT r FROM Renewal r JOIN RentalContract c ON r.contractId = c.contractId "
            + "WHERE c.customerId = :customerId "
            + "AND (:statuses IS NULL OR r.status IN :statuses) "
            + "ORDER BY r.requestedAt DESC")
    Page<Renewal> findCustomerRenewalsWithFilter(
            @Param("customerId") UUID customerId,
            @Param("statuses") Collection<String> statuses,
            Pageable pageable);

    @Query("SELECT r FROM Renewal r JOIN RentalContract c ON r.contractId = c.contractId "
            + "JOIN StorageUnit u ON c.unitId = u.unitId "
            + "WHERE (:facilityId IS NULL OR u.facilityId = :facilityId) "
            + "AND (:statuses IS NULL OR r.status IN :statuses) "
            + "ORDER BY r.requestedAt DESC")
    Page<Renewal> findFacilityRenewals(
            @Param("facilityId") UUID facilityId,
            @Param("statuses") Collection<String> statuses,
            Pageable pageable);

    @Query("SELECT r FROM Renewal r JOIN RentalContract c ON r.contractId = c.contractId "
            + "JOIN StorageUnit u ON c.unitId = u.unitId "
            + "WHERE u.facilityId IN :facilityIds "
            + "AND (:statuses IS NULL OR r.status IN :statuses) "
            + "ORDER BY r.requestedAt DESC")
    Page<Renewal> findByFacilityIdsAndStatusIn(
            @Param("facilityIds") Collection<UUID> facilityIds,
            @Param("statuses") Collection<String> statuses,
            Pageable pageable);
}
