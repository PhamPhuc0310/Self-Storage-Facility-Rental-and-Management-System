package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositRefundService {
    private final Sprint2Access access;
    private final DepositRefundRepository refunds;
    private final PaymentTransactionRepository payments;
    private final InvoiceRepository invoices;
    public DepositRefundService(Sprint2Access access, DepositRefundRepository refunds, PaymentTransactionRepository payments,
                                InvoiceRepository invoices) {
        this.access = access; this.refunds = refunds; this.payments = payments; this.invoices = invoices;
    }
    @Transactional(readOnly = true)
    public List<DepositRefund> mine(UUID contractId) {
        RentalContract c = access.contract(contractId, false);
        if (!"CUSTOMER".equals(access.role()) || !c.customerId.equals(access.currentId()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ khách hàng của hợp đồng được xem yêu cầu hoàn cọc.");
        return refunds.findByContractIdOrderByCreatedAtDesc(contractId);
    }
    @Transactional
    public DepositRefund request(UUID contractId, RefundRequest request) {
        RentalContract c = access.contract(contractId, true);
        if (!"CUSTOMER".equals(access.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ khách hàng được yêu cầu hoàn cọc.");
        if (request == null || request.amount() == null || request.amount().signum() <= 0 || request.reason() == null || request.reason().isBlank())
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số tiền và lý do hoàn cọc không hợp lệ.");
        if (request.amount().stripTrailingZeros().scale() > 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số tiền hoàn phải theo đơn vị VND.");
        if (refunds.existsByContractIdAndStatusIn(contractId, List.of("REQUESTED", "UNDER_REVIEW", "APPROVED")))
            throw new BusinessException(HttpStatus.CONFLICT, "Hợp đồng đang có yêu cầu hoàn cọc chưa xử lý xong.");
        List<PaymentTransaction> received = payments.successful(contractId, "DEPOSIT");
        BigDecimal available = received.stream().map(p -> p.amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(refunds.completedTotal(contractId));
        if (received.isEmpty() || request.amount().compareTo(available) > 0)
            throw new BusinessException(HttpStatus.CONFLICT, "Số tiền yêu cầu vượt tiền cọc đã thu còn có thể hoàn.");
        DepositRefund row = new DepositRefund();
        row.id = UUID.randomUUID(); row.contractId = contractId; row.customerId = c.customerId;
        row.facilityId = access.facilityId(c); row.paymentReference = received.get(0).transactionId;
        row.requestedAmount = request.amount(); row.reason = request.reason().trim();
        row.status = "REQUESTED"; row.createdAt = access.now(); row.updatedAt = row.createdAt;
        return refunds.saveAndFlush(row);
    }
    @Transactional(readOnly = true)
    public List<DepositRefund> managerQueue() {
        if (!"FACILITY_MANAGER".equals(access.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ quản lý cơ sở được xem hoàn cọc.");
        List<UUID> facilities = access.facilities();
        return facilities.isEmpty() ? List.of() : refunds.forManager(facilities,
                List.of("REQUESTED", "UNDER_REVIEW", "APPROVED", "REJECTED", "COMPLETED", "FAILED"));
    }
    private DepositRefund managerRow(UUID id) {
        if (!"FACILITY_MANAGER".equals(access.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ quản lý cơ sở được xét hoàn cọc.");
        DepositRefund row = refunds.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hoàn cọc."));
        access.contract(row.contractId, true);
        if (!access.facilities().contains(row.facilityId)) throw new BusinessException(HttpStatus.FORBIDDEN, "Yêu cầu không thuộc cơ sở được phân công.");
        return row;
    }
    @Transactional
    public DepositRefund review(UUID id, RefundReview request) {
        DepositRefund row = managerRow(id);
        if (request == null || request.decision() == null || !Set.of("UNDER_REVIEW", "APPROVED", "REJECTED", "FAILED").contains(request.decision()))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Quyết định không hợp lệ.");
        if (!Set.of("REQUESTED", "UNDER_REVIEW").contains(row.status)
                && !("APPROVED".equals(row.status) && "FAILED".equals(request.decision())))
            throw new BusinessException(HttpStatus.CONFLICT, "Yêu cầu đã được xét duyệt.");
        if ("FAILED".equals(request.decision()) && !"APPROVED".equals(row.status))
            throw new BusinessException(HttpStatus.CONFLICT, "Chỉ giao dịch hoàn cọc đã duyệt mới có thể ghi nhận thất bại.");
        if ("APPROVED".equals(request.decision())) {
            BigDecimal available = payments.successful(row.contractId, "DEPOSIT").stream().map(p -> p.amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).subtract(refunds.completedTotal(row.contractId));
            if (request.approvedAmount() == null || request.approvedAmount().signum() <= 0
                    || request.approvedAmount().stripTrailingZeros().scale() > 0
                    || request.approvedAmount().compareTo(row.requestedAmount) > 0 || request.approvedAmount().compareTo(available) > 0)
                throw new BusinessException(HttpStatus.CONFLICT, "Số tiền duyệt vượt khoản cọc có thể hoàn.");
            row.approvedAmount = request.approvedAmount();
        }
        row.status = request.decision(); row.reviewerId = access.currentId();
        row.reviewNote = request.note(); row.updatedAt = access.now();
        return refunds.saveAndFlush(row);
    }
    @Transactional
    public DepositRefund complete(UUID id, RefundCompletion request) {
        DepositRefund row = managerRow(id);
        if (!"APPROVED".equals(row.status)) throw new BusinessException(HttpStatus.CONFLICT, "Yêu cầu chưa được duyệt để ghi nhận hoàn tiền.");
        if (request == null || request.transactionReference() == null || request.transactionReference().isBlank())
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cần mã tham chiếu giao dịch hoàn tiền đã thực hiện.");
        if (refunds.existsByRefundTransactionReference(request.transactionReference().trim()))
            throw new BusinessException(HttpStatus.CONFLICT, "Mã giao dịch hoàn tiền đã được sử dụng.");
        BigDecimal received = payments.successful(row.contractId, "DEPOSIT").stream().map(p -> p.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (row.approvedAmount.compareTo(received.subtract(refunds.completedTotal(row.contractId))) > 0)
            throw new BusinessException(HttpStatus.CONFLICT, "Số tiền hoàn vượt khoản cọc còn có thể hoàn.");
        row.status = "COMPLETED"; row.refundTransactionReference = request.transactionReference().trim();
        row.completedAt = access.now(); row.updatedAt = row.completedAt;
        Invoice invoice = invoices.findFirstByContractIdAndInvoiceType(row.contractId, "DEPOSIT")
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Không tìm thấy hóa đơn tiền cọc."));
        invoice.paidAmount = invoice.paidAmount.subtract(row.approvedAmount);
        if (invoice.paidAmount.signum() < 0) throw new BusinessException(HttpStatus.CONFLICT, "Số tiền đã thu không đủ để hoàn cọc.");
        invoice.status = invoice.paidAmount.signum() == 0 ? "UNPAID" : "PARTIAL";
        invoices.save(invoice);
        return refunds.saveAndFlush(row);
    }
}
