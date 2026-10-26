package com.safebox.self_storage.dto.overdue;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO yêu cầu thanh toán phí phạt quá hạn (UC18 - BR18.3).
 */
public class PayPenaltyRequest {

    /**
     * Phương thức thanh toán: CASH, BANK_TRANSFER, VNPAY, MOMO.
     */
    @NotBlank(message = "Phương thức thanh toán không được để trống")
    private String paymentMethod;

    /**
     * Số tiền thanh toán (VND).
     */
    @NotNull(message = "Số tiền thanh toán không được để trống")
    @DecimalMin(value = "1000", message = "Số tiền thanh toán tối thiểu là 1,000 VND")
    private BigDecimal amount;

    /**
     * Mã định danh bất biến (Idempotency Key).
     */
    @NotBlank(message = "Mã yêu cầu (idempotency key) không được để trống")
    private String idempotencyKey;

    /**
     * Mã tham chiếu ngân hàng hoặc phiếu thu.
     */
    private String referenceCode;

    public PayPenaltyRequest() {
    }

    public PayPenaltyRequest(String paymentMethod, BigDecimal amount, String idempotencyKey, String referenceCode) {
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.referenceCode = referenceCode;
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getReferenceCode() {
        return referenceCode;
    }

    public void setReferenceCode(String referenceCode) {
        this.referenceCode = referenceCode;
    }
}
