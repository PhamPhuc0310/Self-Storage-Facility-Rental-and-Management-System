package com.safebox.self_storage.dto.support;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO tóm tắt ticket cho danh sách (UC21 - BR21.1, BR21.3).
 */
public record SupportTicketSummaryResponse(
        UUID ticketId,
        String ticketNumber,
        UUID contractId,
        String contractNumber,
        UUID unitId,
        String unitNumber,
        UUID facilityId,
        String facilityName,
        String issueType,
        String issueTypeLabel,
        String title,
        String status,
        String statusLabel,
        String priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long repliesCount
) {
    public static String getIssueTypeLabel(String type) {
        if (type == null) return "Khác";
        return switch (type.toUpperCase()) {
            case "ACCESS_ISSUE" -> "Khóa & Mã truy cập";
            case "PAYMENT" -> "Thanh toán & Hóa đơn";
            case "UNIT_CONDITION" -> "Tình trạng kho & Thiết bị";
            case "GOODS" -> "Hàng hóa & Lưu kho";
            default -> "Yêu cầu khác";
        };
    }

    public static String getStatusLabel(String status) {
        if (status == null) return "Chờ xử lý";
        return switch (status.toUpperCase()) {
            case "OPEN" -> "Mới tiếp nhận";
            case "IN_PROGRESS" -> "Đang xử lý";
            case "RESOLVED" -> "Đã giải quyết";
            case "CLOSED" -> "Đã đóng";
            default -> status;
        };
    }
}
