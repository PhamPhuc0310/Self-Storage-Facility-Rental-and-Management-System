package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    List<PaymentTransaction> findByContractIdOrderByCreatedAtDesc(UUID contractId);
    Optional<PaymentTransaction> findByCreatedByAndIdempotencyKey(UUID createdBy, String idempotencyKey);

    @Query("select p from PaymentTransaction p where p.contractId = :id and p.paymentType = :type and p.status = 'SUCCESS'")
    List<PaymentTransaction> successful(@Param("id") UUID id, @Param("type") String type);

    @Query("select p.contractId, p.paymentType, sum(p.amount) from PaymentTransaction p where p.contractId in :ids and p.status = 'SUCCESS' group by p.contractId, p.paymentType")
    List<Object[]> successfulTotals(@Param("ids") Collection<UUID> ids);

    List<PaymentTransaction> findByContractId(UUID contractId);

    List<PaymentTransaction> findByInvoiceId(UUID invoiceId);

    List<PaymentTransaction> findByInvoiceIdOrderByTransactionDateDesc(UUID invoiceId);

    List<PaymentTransaction> findByInvoiceIdIn(Collection<UUID> invoiceIds);

    List<PaymentTransaction> findByContractIdAndStatus(UUID contractId, String status);

    Optional<PaymentTransaction> findByReferenceCode(String referenceCode);
}
