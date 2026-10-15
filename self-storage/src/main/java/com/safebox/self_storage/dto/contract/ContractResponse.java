package com.safebox.self_storage.dto.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO trả về thông tin chi tiết đầy đủ của hợp đồng thuê kho (UC09, UC10).
 * Đáp ứng đầy đủ quy tắc nghiệp vụ BR09.6, BR09.7, BR10.2:
 * - Mã hợp đồng, mã yêu cầu
 * - Thông tin khách hàng
 * - Thông tin cơ sở
 * - Thông tin ô kho vật lý (số kho, tầng, khu, diện tích, loại kho)
 * - Thời hạn thuê (ngày bắt đầu, kết thúc, số tháng)
 * - Giá thuê hàng tháng, tổng tiền thuê, tiền cọc, tổng khoản phải trả
 * - Điều khoản áp dụng
 * - Thông tin xác nhận điện tử của khách hàng
 * - Thông tin phê duyệt của Quản lý cơ sở
 * - Trạng thái và nhãn hiển thị tiếng Việt
 */
public record ContractResponse(
        UUID contractId,
        String contractNumber,
        UUID reservationId,
        String requestCode,
        UUID customerId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String customerIdentityNumber,
        String customerAddress,
        UUID facilityId,
        String facilityName,
        String facilityAddress,
        String facilityPhone,
        UUID unitId,
        String unitNumber,
        String floor,
        String zone,
        Integer typeId,
        String typeName,
        String sizeName,
        BigDecimal area,
        String storageMode,
        LocalDate startDate,
        LocalDate endDate,
        int months,
        BigDecimal monthlyRentalFee,
        BigDecimal totalRentalAmount,
        BigDecimal depositAmount,
        BigDecimal totalPayable,
        String billingCycle,
        String termsVersion,
        List<String> termsSummary,
        LocalDateTime customerSignedAt,
        String customerSignature,
        UUID approvedBy,
        String approvedByName,
        LocalDateTime approvedAt,
        String companySignature,
        String status,
        String statusLabel,
        LocalDateTime createdAt
) {
    public static String getStatusLabel(String status) {
        if (status == null) return "Không xác định";
        return switch (status.trim().toUpperCase()) {
            case "PENDING_CONFIRMATION" -> "Chờ khách xác nhận";
            case "PENDING_HANDOVER" -> "Chờ bàn giao";
            case "ACTIVE" -> "Đang hiệu lực";
            case "CANCELLED" -> "Đã hủy";
            case "TERMINATED" -> "Đã thanh lý";
            case "EXPIRED" -> "Đã hết hạn";
            default -> status;
        };
    }
}
