package com.safebox.self_storage.dto.support;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO hiển thị chi tiết một phản hồi trong ticket (UC21 - BR21.4).
 */
public record SupportTicketReplyResponse(
        UUID replyId,
        UUID ticketId,
        UUID userId,
        String userRole,
        String userFullName,
        String content,
        LocalDateTime createdAt,
        boolean isMine
) {
}
