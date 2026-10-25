package com.safebox.self_storage.dto.support;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO chi tiết đầy đủ của ticket gồm thông tin hợp đồng, kho và toàn bộ lịch sử phản hồi (UC21 - BR21.1 - BR21.5).
 */
public record SupportTicketDetailResponse(
        UUID ticketId,
        String ticketNumber,
        UUID contractId,
        String contractNumber,
        UUID customerId,
        String customerName,
        String customerPhone,
        String customerEmail,
        UUID unitId,
        String unitNumber,
        UUID facilityId,
        String facilityName,
        String facilityAddress,
        String issueType,
        String issueTypeLabel,
        String title,
        String description,
        String status,
        String statusLabel,
        String priority,
        UUID assignedTo,
        String assignedStaffName,
        LocalDateTime resolvedAt,
        String resolutionNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canReply,
        List<SupportTicketReplyResponse> replies
) {
}
