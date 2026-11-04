package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    Optional<SupportTicket> findByTicketNumber(String ticketNumber);

    List<SupportTicket> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Page<SupportTicket> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    List<SupportTicket> findByContractIdOrderByCreatedAtDesc(UUID contractId);

    List<SupportTicket> findByFacilityIdOrderByCreatedAtDesc(UUID facilityId);

    List<SupportTicket> findByFacilityIdInOrderByCreatedAtDesc(java.util.Collection<UUID> facilityIds);

    List<SupportTicket> findByAssignedToOrderByCreatedAtDesc(UUID staffId);

    @Query("select count(t) from SupportTicket t where t.assignedTo = :staffId "
            + "and t.facilityId = :facilityId and t.status in :statuses")
    long countOpenWork(@Param("staffId") UUID staffId, @Param("facilityId") UUID facilityId,
                       @Param("statuses") java.util.Collection<String> statuses);

    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    List<SupportTicket> findByFacilityIdAndStatusOrderByCreatedAtDesc(UUID facilityId, String status);

    boolean existsByTicketNumber(String ticketNumber);
}
