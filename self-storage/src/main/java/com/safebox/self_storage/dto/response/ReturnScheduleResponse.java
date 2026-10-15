package com.safebox.self_storage.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO phản hồi thông tin lịch hẹn trả kho (UC16 - BR16.1, BR16.2).
 */
public record ReturnScheduleResponse(
        UUID handoverId,
        UUID contractId,
        String contractNumber,
        UUID unitId,
        String unitNumber,
        String floor,
        String zone,
        UUID facilityId,
        String facilityName,
        String facilityAddress,
        String facilityPhone,
        String openingTime,
        String closingTime,
        LocalDateTime scheduledAt,
        String status,
        String statusLabel,
        UUID staffId,
        String staffName,
        String note,
        LocalDate contractEndDate,
        boolean canReschedule,
        LocalDateTime createdAt
) {
    public static String getStatusLabel(String status) {
        if (status == null) return "Chưa xác định";
        return switch (status.toUpperCase()) {
            case "SCHEDULED" -> "Đã lên lịch hẹn";
            case "IN_PROGRESS" -> "Đang kiểm tra";
            case "COMPLETED" -> "Đã hoàn tất";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }
}
