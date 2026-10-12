package com.safebox.self_storage.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO trả về thông tin hóa đơn thanh toán.
 */
public record InvoiceResponse(
        UUID invoiceId,
        String invoiceNumber,
        UUID contractId,
        String contractNumber,
        String invoiceType,
        String invoiceTypeLabel,
        String description,
        BigDecimal subtotal,
        BigDecimal depositAmount,
        BigDecimal rentalAmount,
        BigDecimal penaltyAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        LocalDateTime issuedAt,
        LocalDateTime dueDate,
        String status,
        String statusLabel,
        List<PaymentTransactionResponse> transactions
) {
    public static String getInvoiceTypeLabel(String type) {
        if (type == null) return "Hóa đơn dịch vụ";
        return switch (type.trim().toUpperCase()) {
            case "INITIAL_PAYMENT" -> "Thanh toán cọc & tiền thuê ban đầu";
            case "MONTHLY_RENT" -> "Tiền thuê kho định kỳ";
            case "RENEWAL" -> "Phí gia hạn hợp đồng";
            case "PENALTY" -> "Phí quá hạn / Vi phạm";
            default -> type;
        };
    }

    public static String getStatusLabel(String status) {
        if (status == null) return "Chưa xác định";
        return switch (status.trim().toUpperCase()) {
            case "UNPAID" -> "Chờ thanh toán";
            case "PARTIALLY_PAID" -> "Đã thanh toán một phần";
            case "PAID" -> "Đã thanh toán";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }
}
