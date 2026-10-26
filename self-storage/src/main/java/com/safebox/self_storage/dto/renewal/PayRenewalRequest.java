package com.safebox.self_storage.dto.renewal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO yêu cầu thanh toán gia hạn hợp đồng (UC17 - BR17.5).
 */
public class PayRenewalRequest {

    /**
     * Phương thức thanh toán: BANK_TRANSFER, VNPAY, MOMO, CASH, CREDIT_CARD.
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
     * Khóa bất biến để đảm bảo Idempotency (BR17.5: retry không tạo giao dịch và gia hạn trùng).
     */
    @NotBlank(message = "Mã yêu cầu (idempotency key) không được để trống")
    private String idempotencyKey;

    /**
     * Mã tham chiếu chuyển khoản hoặc cổng thanh toán bên thứ ba (nếu có).
     */
    private String referenceCode;

    public PayRenewalRequest() {
    }

    public PayRenewalRequest(String paymentMethod, BigDecimal amount, String idempotencyKey) {
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    public PayRenewalRequest(String paymentMethod, BigDecimal amount, String idempotencyKey, String referenceCode) {
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
