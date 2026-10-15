package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findFirstByContractIdAndInvoiceType(UUID contractId, String invoiceType);

    List<Invoice> findByContractId(UUID contractId);

    List<Invoice> findByContractIdOrderByIssuedAtDesc(UUID contractId);

    List<Invoice> findByContractIdIn(Collection<UUID> contractIds);

    Optional<Invoice> findFirstByContractIdAndInvoiceTypeOrderByIssuedAtDesc(UUID contractId, String invoiceType);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);
}
