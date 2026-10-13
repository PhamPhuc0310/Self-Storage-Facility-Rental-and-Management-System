package com.safebox.self_storage.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO đại diện cho một kho/hợp đồng đang thuê trong danh sách quản lý kho (UC15):
 * - Hỗ trợ một khách thuê nhiều kho (BR15.2): mỗi dòng gắn với một hợp đồng và mã kho vật lý riêng biệt
 * - Đầy đủ thông tin: cơ sở, địa chỉ, mã kho, loại kho, kích thước, thời gian thuê, trạng thái hợp đồng, thanh toán (BR15.3)
 * - Số ngày còn lại tính theo Asia/Ho_Chi_Minh; quá hạn hiển thị "Quá hạn", không tự gia hạn hay giải phóng (BR15.6)
 * - Tình trạng thanh toán lấy từ giao dịch thành công (BR15.8)
 * - Cung cấp liên kết chuyển sang đặt lịch trả kho UC16 (BR15.10)
 */
public record RentalContractListItemResponse(
    UUID contractId,
    String contractNumber,
    UUID reservationId,
    UUID customerId,
    UUID unitId,
    String unitNumber,
    String floor,
    String zone,
    UUID facilityId,
    String facilityName,
    String facilityAddress,
    String facilityPhone,
    Integer typeId,
    String typeName,
    String storageMode,
    String sizeName,
    BigDecimal length,
    BigDecimal width,
    BigDecimal height,
    BigDecimal area,
    BigDecimal volume,
    String dimensions,
    String unitTypeImage,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate actualStartDate,
    LocalDate actualEndDate,
    String rentalPeriod,
    BigDecimal monthlyRentalFee,
    BigDecimal depositAmount,
    BigDecimal totalBilledAmount,
    BigDecimal paidAmount,
    BigDecimal remainingAmount,
    String status,
    String statusLabel,
    String statusBadgeClass,
    String paymentStatus,
    String paymentStatusLabel,
    String paymentBadgeClass,
    long daysRemaining,
    boolean isOverdue,
    long overdueDays,
    boolean canScheduleReturn,
    LocalDateTime createdAt
) {
    public static String getStatusLabel(String status) {
        if (status == null) return "Chưa xác định";
        return switch (status.toUpperCase()) {
            case "DRAFT" -> "Bản nháp";
            case "PENDING_CUSTOMER_CONFIRMATION", "PENDING_CONFIRMATION" -> "Chờ khách xác nhận";
            case "CONFIRMED", "PENDING_HANDOVER" -> "Chờ bàn giao";
            case "ACTIVE" -> "Đang thuê";
            case "RETURNING" -> "Đang làm thủ tục trả";
            case "CLOSED", "COMPLETED" -> "Đã kết thúc";
            case "TERMINATED" -> "Đã chấm dứt";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }

    public static String getStatusBadgeClass(String status) {
        if (status == null) return "bg-gray-100 text-gray-800";
        return switch (status.toUpperCase()) {
            case "ACTIVE" -> "bg-emerald-100 text-emerald-800 border-emerald-300";
            case "CONFIRMED", "PENDING_HANDOVER" -> "bg-amber-100 text-amber-800 border-amber-300";
            case "CLOSED", "COMPLETED" -> "bg-blue-100 text-blue-800 border-blue-300";
            case "TERMINATED", "CANCELLED" -> "bg-rose-100 text-rose-800 border-rose-300";
            default -> "bg-gray-100 text-gray-800 border-gray-300";
        };
    }

    public static String getPaymentStatusLabel(String status) {
        if (status == null) return "Chưa thanh toán";
        return switch (status.toUpperCase()) {
            case "PAID" -> "Đã thanh toán đủ";
            case "PARTIALLY_PAID" -> "Thanh toán một phần";
            case "UNPAID" -> "Chưa thanh toán";
            default -> status;
        };
    }

    public static String getPaymentBadgeClass(String status) {
        if (status == null) return "bg-rose-50 text-rose-700 border-rose-200";
        return switch (status.toUpperCase()) {
            case "PAID" -> "bg-emerald-50 text-emerald-700 border-emerald-200";
            case "PARTIALLY_PAID" -> "bg-amber-50 text-amber-700 border-amber-200";
            case "UNPAID" -> "bg-rose-50 text-rose-700 border-rose-200";
            default -> "bg-gray-50 text-gray-700 border-gray-200";
        };
    }
}
