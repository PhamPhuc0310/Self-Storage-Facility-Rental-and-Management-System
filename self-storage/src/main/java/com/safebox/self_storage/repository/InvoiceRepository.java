package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findFirstByContractIdAndInvoiceType(UUID contractId, String invoiceType);

    List<Invoice> findByContractId(UUID contractId);

    List<Invoice> findByContractIdIn(Collection<UUID> contractIds);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
}
