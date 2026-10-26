package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    List<SupportTicket> findByFacilityIdAndStatusOrderByCreatedAtDesc(UUID facilityId, String status);

    boolean existsByTicketNumber(String ticketNumber);

    List<SupportTicket> findByAssignedToOrderByCreatedAtDesc(UUID assignedTo);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(t) FROM SupportTicket t WHERE t.assignedTo = :staffId AND t.facilityId = :facilityId AND t.status IN :statuses")
    long countOpenWork(@org.springframework.data.repository.query.Param("staffId") UUID staffId,
                       @org.springframework.data.repository.query.Param("facilityId") UUID facilityId,
                       @org.springframework.data.repository.query.Param("statuses") List<String> statuses);
}
