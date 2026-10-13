package com.safebox.self_storage.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record HandoverRecordSummaryResponse(
    UUID handoverId,
    String handoverType,
    String handoverTypeLabel,
    LocalDateTime scheduledAt,
    LocalDateTime actualAt,
    String accessMethod,
    String accessIdentifier,
    String note,
    String status,
    String statusLabel
) {
    public static String getHandoverTypeLabel(String type) {
        if (type == null) return "Bàn giao";
        return switch (type.toUpperCase()) {
            case "CHECK_IN" -> "Bàn giao nhận kho";
            case "CHECK_OUT" -> "Bàn giao trả kho";
            default -> type;
        };
    }

    public static String getStatusLabel(String status) {
        if (status == null) return "Chưa xác định";
        return switch (status.toUpperCase()) {
            case "SCHEDULED" -> "Đã lên lịch";
            case "COMPLETED" -> "Đã hoàn tất";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }
}
