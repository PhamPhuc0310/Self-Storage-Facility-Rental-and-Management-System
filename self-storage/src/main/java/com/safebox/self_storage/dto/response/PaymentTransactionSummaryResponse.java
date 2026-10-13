package com.safebox.self_storage.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentTransactionSummaryResponse(
    UUID transactionId,
    UUID invoiceId,
    UUID contractId,
    String referenceCode,
    String transactionType,
    String paymentType,
    String paymentMethod,
    String paymentMethodLabel,
    BigDecimal amount,
    String status,
    String statusLabel,
    String failureReason,
    LocalDateTime transactionDate
) {
    public static String getPaymentMethodLabel(String method) {
        if (method == null) return "Chưa xác định";
        return switch (method.toUpperCase()) {
            case "DEMO_ONLINE_PAYMENT" -> "Cổng thanh toán trực tuyến SafeBox";
            case "VNPAY" -> "VNPay";
            case "MOMO" -> "MoMo";
            case "BANK_TRANSFER" -> "Chuyển khoản ngân hàng";
            case "CASH" -> "Tiền mặt tại quầy";
            default -> method;
        };
    }

    public static String getStatusLabel(String status) {
        if (status == null) return "Chưa xác định";
        return switch (status.toUpperCase()) {
            case "SUCCESS" -> "Thành công";
            case "PENDING" -> "Đang xử lý";
            case "FAILED" -> "Thất bại";
            case "REFUNDED" -> "Đã hoàn tiền";
            default -> status;
        };
    }
}
