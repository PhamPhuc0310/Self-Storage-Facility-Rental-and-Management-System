package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thực thể ánh xạ bảng PAYMENT_TRANSACTIONS trong cơ sở dữ liệu.
 * Ghi nhận các giao dịch thanh toán cho hóa đơn tiền thuê kho và tiền cọc.
 */
@Entity
@Table(name = "PAYMENT_TRANSACTIONS")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id", updatable = false, nullable = false)
    private UUID transactionId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    /**
     * Loại giao dịch:
     * - PAYMENT: Thu tiền
     * - REFUND: Hoàn tiền
     */
    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    /**
     * Phương thức thanh toán:
     * - BANK_TRANSFER: Chuyển khoản ngân hàng / VietQR
     * - VNPAY: Cổng VNPay
     * - MOMO: Ví MoMo
     * - CASH: Tiền mặt tại quầy
     * - CREDIT_CARD: Thẻ quốc tế Visa/MasterCard
     */
    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "reference_code", length = 100)
    private String referenceCode;

    /**
     * Trạng thái giao dịch:
     * - SUCCESS: Thành công
     * - FAILED: Thất bại
     * - PENDING: Đang xử lý
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "failure_reason", columnDefinition = "NVARCHAR(MAX)")
    private String failureReason;

    public PaymentTransaction() {
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getReferenceCode() {
        return referenceCode;
    }

    public void setReferenceCode(String referenceCode) {
        this.referenceCode = referenceCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
