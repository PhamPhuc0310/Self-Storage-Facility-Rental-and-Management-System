package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.payment.InvoiceResponse;
import com.safebox.self_storage.dto.payment.PayInvoiceRequest;
import com.safebox.self_storage.dto.payment.PayInvoiceResponse;
import com.safebox.self_storage.dto.payment.PaymentTransactionResponse;
import com.safebox.self_storage.entity.Invoice;
import com.safebox.self_storage.entity.PaymentTransaction;
import com.safebox.self_storage.entity.RentalContract;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.InvoiceRepository;
import com.safebox.self_storage.repository.PaymentTransactionRepository;
import com.safebox.self_storage.repository.RentalContractRepository;
import com.safebox.self_storage.service.InvoicePaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class PaymentServiceImpl implements InvoicePaymentService {

    private final InvoiceRepository invoices;
    private final PaymentTransactionRepository transactions;
    private final RentalContractRepository contracts;

    public PaymentServiceImpl(
            InvoiceRepository invoices,
            PaymentTransactionRepository transactions,
            RentalContractRepository contracts) {
        this.invoices = invoices;
        this.transactions = transactions;
        this.contracts = contracts;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getOrCreateInitialInvoice(UUID customerId, UUID contractId) {
        if (customerId == null || contractId == null) {
            throw new IllegalArgumentException("Thông tin tra cứu hóa đơn không hợp lệ.");
        }

        RentalContract contract = contracts.findById(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê kho."));

        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập hóa đơn của hợp đồng này.");
        }

        // The approval flow owns invoice creation. A GET must never add a second
        // combined obligation beside DEPOSIT and INITIAL_RENT/REMAINING_RENT.
        Invoice invoice = invoices.findFirstByContractIdAndInvoiceType(contractId, "DEPOSIT")
                .or(() -> invoices.findFirstByContractIdAndInvoiceTypeOrderByIssuedAtDesc(contractId, "INITIAL_PAYMENT"))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Hợp đồng chưa có hóa đơn thanh toán."));
        return toInvoiceResponse(invoice, contract);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getContractInvoices(UUID customerId, UUID contractId) {
        if (customerId == null || contractId == null) {
            throw new IllegalArgumentException("Thông tin tra cứu không hợp lệ.");
        }

        RentalContract contract = contracts.findById(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê kho."));

        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập hóa đơn của hợp đồng này.");
        }

        List<Invoice> list = invoices.findByContractIdOrderByIssuedAtDesc(contractId);
        return list.stream().map(inv -> toInvoiceResponse(inv, contract)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceDetail(UUID customerId, UUID invoiceId) {
        if (customerId == null || invoiceId == null) {
            throw new IllegalArgumentException("Thông tin hóa đơn không hợp lệ.");
        }

        Invoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hóa đơn."));

        RentalContract contract = contracts.findById(invoice.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng liên kết."));

        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem hóa đơn này.");
        }

        return toInvoiceResponse(invoice, contract);
    }

    @Override
    @Transactional
    public PayInvoiceResponse payInitialInvoice(UUID customerId, UUID contractId, PayInvoiceRequest request) {
        if (customerId == null || contractId == null || request == null) {
            throw new IllegalArgumentException("Dữ liệu thanh toán không hợp lệ.");
        }

        RentalContract contract = contracts.findById(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng."));
        if (!contract.customerId.equals(customerId))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền thanh toán hợp đồng này.");
        throw new BusinessException(HttpStatus.CONFLICT, "Vui lòng thanh toán tiền cọc tại trang Thanh toán và lịch nhận kho.");
    }

    @Override
    @Transactional
    public PayInvoiceResponse payInvoice(UUID customerId, UUID invoiceId, PayInvoiceRequest request) {
        if (customerId == null || invoiceId == null || request == null) {
            throw new IllegalArgumentException("Dữ liệu thanh toán không hợp lệ.");
        }

        Invoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hóa đơn."));

        RentalContract contract = contracts.findById(invoice.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng."));

        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền thanh toán hóa đơn này.");
        }

        throw new BusinessException(HttpStatus.CONFLICT, "Vui lòng thanh toán tại trang Thanh toán và lịch nhận kho.");
    }

    private InvoiceResponse toInvoiceResponse(Invoice inv, RentalContract contract) {
        List<PaymentTransaction> txList = transactions.findByInvoiceIdOrderByTransactionDateDesc(inv.getInvoiceId());
        List<PaymentTransactionResponse> txResponses = txList.stream().map(this::toTransactionResponse).toList();

        BigDecimal remaining = inv.getTotalAmount().subtract(inv.getPaidAmount());
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        BigDecimal depositAmount = "DEPOSIT".equals(inv.getInvoiceType()) ? inv.getTotalAmount()
                : "INITIAL_PAYMENT".equals(inv.getInvoiceType()) && contract != null ? contract.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal rentalAmount = "INITIAL_PAYMENT".equals(inv.getInvoiceType()) && contract != null
                ? contract.getMonthlyRentalFee() : Set.of("INITIAL_RENT", "REMAINING_RENT").contains(inv.getInvoiceType())
                ? inv.getTotalAmount() : BigDecimal.ZERO;

        return new InvoiceResponse(
                inv.getInvoiceId(),
                inv.getInvoiceNumber(),
                inv.getContractId(),
                contract != null ? contract.getContractNumber() : null,
                inv.getInvoiceType(),
                InvoiceResponse.getInvoiceTypeLabel(inv.getInvoiceType()),
                inv.getDescription(),
                inv.getSubtotal(),
                depositAmount,
                rentalAmount,
                inv.getPenaltyAmount(),
                inv.getDiscountAmount(),
                inv.getTotalAmount(),
                inv.getPaidAmount(),
                remaining,
                inv.getIssuedAt(),
                inv.getDueDate(),
                inv.getStatus(),
                InvoiceResponse.getStatusLabel(inv.getStatus()),
                txResponses
        );
    }

    private PaymentTransactionResponse toTransactionResponse(PaymentTransaction tx) {
        return new PaymentTransactionResponse(
                tx.getTransactionId(),
                tx.getInvoiceId(),
                tx.getTransactionType(),
                tx.getPaymentMethod(),
                PaymentTransactionResponse.getPaymentMethodLabel(tx.getPaymentMethod()),
                tx.getAmount(),
                tx.getTransactionDate(),
                tx.getReferenceCode(),
                tx.getStatus(),
                tx.getFailureReason()
        );
    }

}
