package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByContractIdOrderByIssuedAtDesc(UUID contractId);

    Optional<Invoice> findFirstByContractIdAndInvoiceTypeOrderByIssuedAtDesc(UUID contractId, String invoiceType);

    boolean existsByInvoiceNumber(String invoiceNumber);
}
