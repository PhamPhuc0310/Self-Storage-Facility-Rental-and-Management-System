package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.payment.InvoiceResponse;
import com.safebox.self_storage.dto.payment.PayInvoiceRequest;
import com.safebox.self_storage.dto.payment.PayInvoiceResponse;
import com.safebox.self_storage.entity.Invoice;
import com.safebox.self_storage.entity.PaymentTransaction;
import com.safebox.self_storage.entity.RentalContract;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.InvoiceRepository;
import com.safebox.self_storage.repository.PaymentTransactionRepository;
import com.safebox.self_storage.repository.RentalContractRepository;
import com.safebox.self_storage.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private InvoiceRepository invoices;
    @Mock private PaymentTransactionRepository transactions;
    @Mock private RentalContractRepository contracts;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private UUID customerId;
    private UUID contractId;
    private UUID invoiceId;
    private RentalContract contract;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        contractId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();

        contract = new RentalContract();
        contract.setContractId(contractId);
        contract.setContractNumber("HD-20261010-0001");
        contract.setCustomerId(customerId);
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusMonths(3));
        contract.setMonthlyRentalFee(BigDecimal.valueOf(1500000));
        contract.setDepositAmount(BigDecimal.valueOf(1500000));
        contract.setStatus("PENDING_HANDOVER");

        invoice = new Invoice();
        invoice.setInvoiceId(invoiceId);
        invoice.setInvoiceNumber("INV-20261010-0001");
        invoice.setContractId(contractId);
        invoice.setInvoiceType("INITIAL_PAYMENT");
        invoice.setSubtotal(BigDecimal.valueOf(3000000)); // 1.5M cọc + 1.5M thuê
        invoice.setPenaltyAmount(BigDecimal.ZERO);
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(BigDecimal.valueOf(3000000));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setIssuedAt(LocalDateTime.now());
        invoice.setDueDate(LocalDateTime.now().plusDays(3));
        invoice.setStatus("UNPAID");
    }

    @Test
    @DisplayName("Legacy invoice lookup reads the approval-created deposit invoice without inserting a new obligation")
    void getOrCreateInitialInvoice_readsDepositWithoutCreating() {
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));
        invoice.setInvoiceType("DEPOSIT");
        when(invoices.findFirstByContractIdAndInvoiceType(contractId, "DEPOSIT"))
                .thenReturn(Optional.of(invoice));
        when(transactions.findByInvoiceIdOrderByTransactionDateDesc(any())).thenReturn(List.of());

        InvoiceResponse response = paymentService.getOrCreateInitialInvoice(customerId, contractId);

        assertNotNull(response);
        assertEquals("DEPOSIT", response.invoiceType());
        assertEquals(BigDecimal.valueOf(3000000), response.totalAmount());
        assertEquals(BigDecimal.valueOf(3000000), response.depositAmount());
        assertEquals(BigDecimal.ZERO, response.rentalAmount());
        assertEquals("UNPAID", response.status());
        verify(invoices, never()).save(any(Invoice.class));
    }

    @Test
    @DisplayName("Từ chối truy cập hóa đơn khi khách hàng không phải chủ hợp đồng")
    void getOrCreateInitialInvoice_shouldThrowForbidden_whenOtherCustomer() {
        UUID otherCustomer = UUID.randomUUID();
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                paymentService.getOrCreateInitialInvoice(otherCustomer, contractId));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
    }

    @Test
    @DisplayName("Legacy payment endpoint cannot record an unverified combined payment")
    void payInitialInvoice_rejectsLegacyWrite() {
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));
        PayInvoiceRequest request = new PayInvoiceRequest("BANK_TRANSFER", "VQR-998877", "Đã quét VietQR chuyển khoản");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                paymentService.payInitialInvoice(customerId, contractId, request));
        assertEquals(HttpStatus.CONFLICT, ex.status());
        verifyNoInteractions(transactions);
        verify(invoices, never()).save(any(Invoice.class));
    }

    @Test
    @DisplayName("Legacy invoice payment endpoint cannot settle a component outside canonical rules")
    void payInvoice_rejectsLegacyWrite() {
        invoice.setStatus("PAID");
        invoice.setPaidAmount(invoice.getTotalAmount());

        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));
        when(invoices.findById(invoiceId))
                .thenReturn(Optional.of(invoice));

        PayInvoiceRequest request = new PayInvoiceRequest("VNPAY", null, null);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                paymentService.payInvoice(customerId, invoiceId, request));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        verifyNoInteractions(transactions);
    }
}
