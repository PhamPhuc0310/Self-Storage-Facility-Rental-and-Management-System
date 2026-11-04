package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.OverduePolicyProposal;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface OverduePolicyProposalRepository extends JpaRepository<OverduePolicyProposal,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from OverduePolicyProposal p where p.proposalId = :id")
    Optional<OverduePolicyProposal> lockById(@Param("id") UUID id);
    List<OverduePolicyProposal> findByFacilityIdOrderByCreatedAtDesc(UUID facilityId);
    List<OverduePolicyProposal> findAllByOrderByCreatedAtDesc();
}
