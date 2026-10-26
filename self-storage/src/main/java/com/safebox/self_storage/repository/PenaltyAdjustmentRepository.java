package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.PenaltyAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PenaltyAdjustmentRepository extends JpaRepository<PenaltyAdjustment, UUID> {

    List<PenaltyAdjustment> findByPenaltyIdOrderByAdjustedAtDesc(UUID penaltyId);

    List<PenaltyAdjustment> findByContractIdOrderByAdjustedAtDesc(UUID contractId);
}
