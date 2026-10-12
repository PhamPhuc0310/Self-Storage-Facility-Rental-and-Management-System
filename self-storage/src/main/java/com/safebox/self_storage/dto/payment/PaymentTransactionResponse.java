package com.safebox.self_storage.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO hiển thị chi tiết một giao dịch thanh toán.
 */
public record PaymentTransactionResponse(
        UUID transactionId,
        UUID invoiceId,
        String transactionType,
        String paymentMethod,
        String paymentMethodLabel,
        BigDecimal amount,
        LocalDateTime transactionDate,
        String referenceCode,
        String status,
        String failureReason
) {
    public static String getPaymentMethodLabel(String method) {
        if (method == null) return "Không xác định";
        return switch (method.trim().toUpperCase()) {
            case "BANK_TRANSFER" -> "Chuyển khoản VietQR";
            case "VNPAY" -> "Cổng thanh toán VNPay";
            case "MOMO" -> "Ví điện tử MoMo";
            case "CASH" -> "Tiền mặt tại quầy";
            case "CREDIT_CARD" -> "Thẻ quốc tế (Visa/MasterCard)";
            default -> method;
        };
    }
}
