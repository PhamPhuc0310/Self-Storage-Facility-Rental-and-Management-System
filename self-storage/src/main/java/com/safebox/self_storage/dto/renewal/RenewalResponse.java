package com.safebox.self_storage.dto.renewal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO phản hồi thông tin chi tiết của một yêu cầu gia hạn hợp đồng thuê kho (UC17).
 */
public record RenewalResponse(
        UUID renewalId,
        UUID contractId,
        String contractNumber,
        UUID customerId,
        String customerName,
        UUID facilityId,
        String facilityName,
        String unitNumber,
        LocalDate oldEndDate,
        LocalDate newEndDate,
        Integer extensionMonths,
        BigDecimal monthlyPrice,
        BigDecimal subtotal,
        BigDecimal vatAmount,
        BigDecimal discountAmount,
        BigDecimal renewalAmount,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        UUID approvedBy,
        String approvedByName,
        LocalDateTime paymentDeadline,
        String status,
        String statusLabel,
        String rejectionReason,
        UUID invoiceId,
        String invoiceNumber,
        String invoiceStatus,
        boolean canPay,
        boolean canCancel
) {
    public static String getStatusLabel(String status) {
        if (status == null) return "Chưa xác định";
        return switch (status.toUpperCase()) {
            case "PENDING" -> "Chờ quản lý xét duyệt";
            case "APPROVED" -> "Đã duyệt - Chờ thanh toán";
            case "COMPLETED" -> "Đã gia hạn thành công";
            case "REJECTED" -> "Bị từ chối";
            case "CANCELLED" -> "Đã hủy";
            case "EXPIRED" -> "Hết hạn thanh toán";
            default -> status;
        };
    }
}
