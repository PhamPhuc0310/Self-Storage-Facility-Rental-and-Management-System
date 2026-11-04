package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.PriceProposal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceProposalRepository extends JpaRepository<PriceProposal, UUID> {
    List<PriceProposal> findByFacilityIdOrderByProposedAtDesc(UUID facilityId);
    List<PriceProposal> findAllByOrderByProposedAtDesc();
}
