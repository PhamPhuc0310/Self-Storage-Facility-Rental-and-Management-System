package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    List<PaymentTransaction> findByContractId(UUID contractId);

    List<PaymentTransaction> findByInvoiceId(UUID invoiceId);

    List<PaymentTransaction> findByInvoiceIdIn(Collection<UUID> invoiceIds);

    List<PaymentTransaction> findByContractIdAndStatus(UUID contractId, String status);

    Optional<PaymentTransaction> findByReferenceCode(String referenceCode);
}
