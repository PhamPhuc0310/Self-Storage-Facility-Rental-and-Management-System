package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.TerminationRequest;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TerminationRequestRepository extends JpaRepository<TerminationRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TerminationRequest t where t.id = :id")
    Optional<TerminationRequest> lockById(@Param("id") UUID id);
    boolean existsByContractIdAndTypeAndStatusIn(UUID contractId, String type, Collection<String> statuses);
    Optional<TerminationRequest> findFirstByContractIdAndTypeAndStatusIn(UUID contractId, String type, Collection<String> statuses);
    List<TerminationRequest> findByRequestedByAndTypeOrderByRequestedAtDesc(UUID requestedBy, String type);
    @Query("select t from TerminationRequest t join RentalContract c on t.contractId = c.contractId "
            + "join StorageUnit u on c.unitId = u.unitId where t.type = 'EARLY_RETURN' "
            + "and u.facilityId in :facilities order by t.requestedAt desc")
    List<TerminationRequest> forFacilities(@Param("facilities") Collection<UUID> facilities);
}
