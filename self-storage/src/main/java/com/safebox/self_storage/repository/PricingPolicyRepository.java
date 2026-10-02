package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.PricingPolicy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingPolicyRepository extends JpaRepository<PricingPolicy, UUID> {
    List<PricingPolicy> findByFacilityIdAndStatus(UUID facilityId, String status);
}
