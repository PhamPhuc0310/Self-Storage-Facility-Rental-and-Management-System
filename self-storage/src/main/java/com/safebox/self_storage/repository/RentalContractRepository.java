package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.RentalContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentalContractRepository extends JpaRepository<RentalContract, UUID> {

    List<RentalContract> findByCustomerIdOrderByStartDateDesc(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RentalContract c where c.contractId = :id")
    Optional<RentalContract> lockById(@Param("id") UUID id);

    Optional<RentalContract> findByContractId(UUID contractId);

    Optional<RentalContract> findByContractIdAndCustomerId(UUID contractId, UUID customerId);

    Optional<RentalContract> findByContractNumber(String contractNumber);

    Optional<RentalContract> findByReservationId(UUID reservationId);

    List<RentalContract> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Page<RentalContract> findByCustomerId(UUID customerId, Pageable pageable);

    Page<RentalContract> findByCustomerIdAndStatus(UUID customerId, String status, Pageable pageable);

    Page<RentalContract> findByCustomerIdAndStatusIn(UUID customerId, Collection<String> statuses, Pageable pageable);

    @Query("SELECT c FROM RentalContract c WHERE c.customerId = :customerId "
            + "AND (:statuses IS NULL OR c.status IN :statuses) "
            + "AND (:search IS NULL OR LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "ORDER BY c.createdAt DESC")
    Page<RentalContract> findCustomerContractsWithFilter(
            @Param("customerId") UUID customerId,
            @Param("statuses") Collection<String> statuses,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT c FROM RentalContract c JOIN StorageUnit u ON c.unitId = u.unitId "
            + "WHERE u.facilityId = :facilityId "
            + "AND (:statuses IS NULL OR c.status IN :statuses) "
            + "AND (:search IS NULL OR LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "     OR LOWER(u.unitNumber) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "ORDER BY c.createdAt DESC")
    Page<RentalContract> findFacilityContractsWithFilter(
            @Param("facilityId") UUID facilityId,
            @Param("statuses") Collection<String> statuses,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT c FROM RentalContract c JOIN StorageUnit u ON c.unitId = u.unitId "
            + "WHERE c.contractId = :contractId AND u.facilityId = :facilityId")
    Optional<RentalContract> findByContractIdAndFacilityId(
            @Param("contractId") UUID contractId,
            @Param("facilityId") UUID facilityId);
}
