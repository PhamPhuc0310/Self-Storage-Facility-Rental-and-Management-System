package com.safebox.self_storage.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record InvoiceSummaryResponse(
    UUID invoiceId,
    String invoiceNumber,
    String invoiceType,
    String invoiceTypeLabel,
    String description,
    BigDecimal subtotal,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal remainingAmount,
    String status,
    String statusLabel,
    LocalDateTime issuedAt,
    LocalDateTime dueDate
) {
    public static String getInvoiceTypeLabel(String type) {
        if (type == null) return "Hóa đơn khác";
        return switch (type.toUpperCase()) {
            case "DEPOSIT" -> "Tiền cọc";
            case "INITIAL_RENT" -> "Tiền thuê ban đầu";
            case "MONTHLY_RENT" -> "Tiền thuê hàng tháng";
            case "PENALTY" -> "Phí phạt";
            case "REPAIR_FEE" -> "Chi phí sửa chữa";
            default -> type;
        };
    }

    public static String getStatusLabel(String status) {
        if (status == null) return "Chưa xác định";
        return switch (status.toUpperCase()) {
            case "PAID" -> "Đã thanh toán";
            case "UNPAID" -> "Chưa thanh toán";
            case "PARTIALLY_PAID" -> "Thanh toán một phần";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }
}
