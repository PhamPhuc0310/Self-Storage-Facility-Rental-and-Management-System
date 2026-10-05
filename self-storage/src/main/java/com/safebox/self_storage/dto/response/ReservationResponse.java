package com.safebox.self_storage.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO phản hồi chứa toàn bộ thông tin chi tiết của một yêu cầu đặt kho (Reservation):
 * - Thông tin định danh: mã UUID, mã hiển thị (#SB-REQ-YYYY-XXXX)
 * - Thông tin khách hàng: họ tên, email, số điện thoại
 * - Thông tin cơ sở: tên, địa chỉ, số hotline
 * - Thông tin loại kho: tên loại, chế độ lưu trữ, diện tích, hình ảnh
 * - Thông tin hợp đồng dự kiến: giá thuê tháng, số tháng, ngày bắt đầu, ngày kết thúc, tổng tiền thuê, tiền cọc
 * - Trạng thái: mã trạng thái (PENDING, CONFIRMED, REJECTED, CANCELLED) và nhãn hiển thị tiếng Việt
 * - Thông tin thời gian: thời hạn giữ kho, thời gian tạo, thời gian hủy và lý do hủy
 */
public record ReservationResponse(
    UUID reservationId,
    String requestCode,
    UUID customerId,
    String customerName,
    String customerEmail,
    String customerPhone,
    UUID facilityId,
    String facilityName,
    String facilityAddress,
    String facilityPhone,
    Integer typeId,
    String typeName,
    String storageMode,
    String sizeName,
    BigDecimal area,
    String unitTypeImage,
    UUID pricingId,
    BigDecimal monthlyPrice,
    Integer rentalMonths,
    LocalDate startDate,
    LocalDate endDate,
    LocalDateTime holdExpiresAt,
    BigDecimal estimatedRentalAmount,
    BigDecimal estimatedDepositAmount,
    String status,
    String statusLabel,
    LocalDateTime createdAt,
    LocalDateTime cancelledAt,
    String cancellationReason
) {
    /**
     * Hàm tiện ích: Định dạng mã yêu cầu đặt kho hiển thị thân thiện với người dùng (ví dụ: #SB-REQ-2026-A1B2).
     *
     * @param id        Mã định danh UUID của yêu cầu
     * @param createdAt Thời điểm tạo yêu cầu
     * @return Chuỗi mã hiển thị dạng #SB-REQ-YYYY-XXXX
     */
    public static String formatRequestCode(UUID id, LocalDateTime createdAt) {
        if (id == null) return "#SB-REQ-0000";
        int year = (createdAt != null) ? createdAt.getYear() : LocalDate.now().getYear();
        String hex = id.toString().replace("-", "").toUpperCase();
        String suffix = hex.length() >= 4 ? hex.substring(hex.length() - 4) : "0000";
        return String.format("#SB-REQ-%d-%s", year, suffix);
    }

    /**
     * Hàm tiện ích: Chuyển đổi mã trạng thái tiếng Anh sang nhãn hiển thị tiếng Việt.
     *
     * @param status Mã trạng thái (PENDING, CONFIRMED, REJECTED, CANCELLED)
     * @return Nhãn tiếng Việt tương ứng
     */
    public static String getStatusLabel(String status) {
        if (status == null) return "Không xác định";
        return switch (status.toUpperCase()) {
            case "PENDING" -> "Chờ xác nhận";
            case "CONFIRMED", "APPROVED" -> "Đã xác nhận";
            case "REJECTED" -> "Bị từ chối";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }
}
