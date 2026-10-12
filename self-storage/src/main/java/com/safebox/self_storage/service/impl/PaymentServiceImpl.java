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
import com.safebox.self_storage.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PaymentServiceImpl implements PaymentService {

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
    @Transactional
    public InvoiceResponse getOrCreateInitialInvoice(UUID customerId, UUID contractId) {
        if (customerId == null || contractId == null) {
            throw new IllegalArgumentException("Thông tin tra cứu hóa đơn không hợp lệ.");
        }

        RentalContract contract = contracts.findById(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê kho."));

        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập hóa đơn của hợp đồng này.");
        }

        // Tìm hóa đơn ban đầu đã tạo trước đó
        Optional<Invoice> existingOpt = invoices.findFirstByContractIdAndInvoiceTypeOrderByIssuedAtDesc(
                contractId, "INITIAL_PAYMENT");
        if (existingOpt.isPresent()) {
            return toInvoiceResponse(existingOpt.get(), contract);
        }

        // Tự động lập hóa đơn thanh toán ban đầu gồm Tiền cọc + Tiền thuê kỳ đầu
        BigDecimal depositAmount = contract.getDepositAmount() != null ? contract.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal monthlyRentalFee = contract.getMonthlyRentalFee() != null ? contract.getMonthlyRentalFee() : BigDecimal.ZERO;
        BigDecimal subtotal = depositAmount.add(monthlyRentalFee);

        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String invoiceNumber;
        do {
            String randomSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            invoiceNumber = "INV-" + datePrefix + "-" + randomSuffix;
        } while (invoices.existsByInvoiceNumber(invoiceNumber));

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setContractId(contract.getContractId());
        invoice.setInvoiceType("INITIAL_PAYMENT");
        invoice.setDescription("Thanh toán tiền đặt cọc (" + formatCurrency(depositAmount) + ") và tiền thuê kỳ đầu ("
                + formatCurrency(monthlyRentalFee) + ") cho hợp đồng " + contract.getContractNumber());
        invoice.setSubtotal(subtotal);
        invoice.setPenaltyAmount(BigDecimal.ZERO);
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(subtotal);
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setIssuedAt(LocalDateTime.now());

        // Hạn thanh toán: ngày bắt đầu hợp đồng hoặc sau 3 ngày kể từ lúc lập
        LocalDateTime due = contract.getStartDate() != null
                ? contract.getStartDate().atStartOfDay()
                : LocalDateTime.now().plusDays(3);
        if (due.isBefore(LocalDateTime.now())) {
            due = LocalDateTime.now().plusDays(1);
        }
        invoice.setDueDate(due);
        invoice.setStatus("UNPAID");

        Invoice saved = invoices.save(invoice);
        return toInvoiceResponse(saved, contract);
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

        // Lấy hoặc tự động tạo hóa đơn ban đầu
        getOrCreateInitialInvoice(customerId, contractId);

        Invoice invoice = invoices.findFirstByContractIdAndInvoiceTypeOrderByIssuedAtDesc(contractId, "INITIAL_PAYMENT")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hóa đơn thanh toán ban đầu."));

        RentalContract contract = contracts.findById(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng."));

        return processPayment(customerId, invoice, contract, request);
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

        return processPayment(customerId, invoice, contract, request);
    }

    private PayInvoiceResponse processPayment(
            UUID customerId,
            Invoice invoice,
            RentalContract contract,
            PayInvoiceRequest request) {

        if ("PAID".equalsIgnoreCase(invoice.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Hóa đơn này đã được thanh toán đầy đủ (Mã HĐ: " + invoice.getInvoiceNumber() + ").");
        }

        if ("CANCELLED".equalsIgnoreCase(invoice.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Hóa đơn này đã bị hủy, không thể thanh toán.");
        }

        BigDecimal remainingAmount = invoice.getTotalAmount().subtract(invoice.getPaidAmount());
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Hóa đơn không còn khoản tiền nào cần thanh toán.");
        }

        // Sinh mã giao dịch nếu chưa có
        String refCode = (request.referenceCode() != null && !request.referenceCode().isBlank())
                ? request.referenceCode().trim()
                : "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setInvoiceId(invoice.getInvoiceId());
        transaction.setTransactionType("PAYMENT");
        transaction.setPaymentMethod(request.paymentMethod().trim().toUpperCase());
        transaction.setAmount(remainingAmount);
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setReferenceCode(refCode);
        transaction.setStatus("SUCCESS");
        transaction.setFailureReason(null);

        PaymentTransaction savedTx = transactions.save(transaction);

        // Cập nhật trạng thái hóa đơn
        invoice.setPaidAmount(invoice.getTotalAmount());
        invoice.setStatus("PAID");
        Invoice updatedInvoice = invoices.save(invoice);

        InvoiceResponse invoiceResp = toInvoiceResponse(updatedInvoice, contract);
        PaymentTransactionResponse txResp = toTransactionResponse(savedTx);

        String methodLabel = PaymentTransactionResponse.getPaymentMethodLabel(request.paymentMethod());
        String msg = "Thanh toán thành công " + formatCurrency(remainingAmount) + " qua " + methodLabel + "!";

        return new PayInvoiceResponse(true, msg, invoiceResp, txResp);
    }

    private InvoiceResponse toInvoiceResponse(Invoice inv, RentalContract contract) {
        List<PaymentTransaction> txList = transactions.findByInvoiceIdOrderByTransactionDateDesc(inv.getInvoiceId());
        List<PaymentTransactionResponse> txResponses = txList.stream().map(this::toTransactionResponse).toList();

        BigDecimal remaining = inv.getTotalAmount().subtract(inv.getPaidAmount());
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        BigDecimal depositAmount = contract != null ? contract.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal rentalAmount = contract != null ? contract.getMonthlyRentalFee() : BigDecimal.ZERO;

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

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 đ";
        Locale vn = new Locale("vi", "VN");
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(vn);
        return currencyFormat.format(amount);
    }
}
