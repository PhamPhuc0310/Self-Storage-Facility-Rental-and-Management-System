package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.EarlyReturnSettlement;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EarlyReturnSettlementRepository extends JpaRepository<EarlyReturnSettlement, UUID> {
    Optional<EarlyReturnSettlement> findByTerminationId(UUID terminationId);
}
