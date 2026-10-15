package com.safebox.self_storage.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO đại diện cho một công việc trả kho trong danh sách công việc của nhân viên / quản lý (BR12.6, BR12.7, BR16.3).
 */
public record FacilityReturnTaskItemResponse(
        UUID handoverId,
        UUID contractId,
        String contractNumber,
        String customerName,
        String customerPhone,
        String customerEmail,
        UUID unitId,
        String unitNumber,
        String floor,
        String zone,
        UUID facilityId,
        String facilityName,
        LocalDateTime scheduledAt,
        LocalDateTime actualAt,
        String status,
        String statusLabel,
        UUID assignedStaffId,
        String assignedStaffName,
        boolean isAssignedToCurrentUser,
        String note
) {
    public static String getStatusLabel(String status) {
        if (status == null) return "Chưa xác định";
        return switch (status.toUpperCase()) {
            case "SCHEDULED" -> "Chờ tiếp nhận";
            case "IN_PROGRESS" -> "Đang kiểm tra";
            case "COMPLETED" -> "Đã nhận lại kho";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }
}
