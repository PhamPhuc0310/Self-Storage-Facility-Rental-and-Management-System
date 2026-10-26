package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.ContractPenalty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractPenaltyRepository extends JpaRepository<ContractPenalty, UUID> {

    List<ContractPenalty> findByContractIdOrderByPeriodDateDesc(UUID contractId);

    Optional<ContractPenalty> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("SELECT p FROM ContractPenalty p WHERE p.contractId = :contractId AND p.status IN :statuses ORDER BY p.periodDate DESC")
    List<ContractPenalty> findByContractIdAndStatusIn(@Param("contractId") UUID contractId, @Param("statuses") Collection<String> statuses);

    @Query("SELECT p FROM ContractPenalty p JOIN RentalContract c ON p.contractId = c.contractId "
            + "WHERE c.customerId = :customerId "
            + "ORDER BY p.periodDate DESC")
    Page<ContractPenalty> findByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);

    @Query("SELECT p FROM ContractPenalty p JOIN RentalContract c ON p.contractId = c.contractId "
            + "JOIN StorageUnit u ON c.unitId = u.unitId "
            + "WHERE u.facilityId IN :facilityIds "
            + "ORDER BY p.periodDate DESC")
    Page<ContractPenalty> findByFacilityIds(@Param("facilityIds") Collection<UUID> facilityIds, Pageable pageable);

    @Query("SELECT p FROM ContractPenalty p WHERE p.contractId = :contractId AND p.periodDate = :periodDate")
    Optional<ContractPenalty> findByContractIdAndPeriodDate(@Param("contractId") UUID contractId, @Param("periodDate") LocalDate periodDate);
}
