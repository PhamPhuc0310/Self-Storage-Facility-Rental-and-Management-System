package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.support.*;

import java.util.List;
import java.util.UUID;

/**
 * Service xử lý nghiệp vụ Use Case UC21:
 * "Gửi và theo dõi yêu cầu hỗ trợ" (BR21.1 – BR21.5).
 */
public interface SupportTicketService {

    /**
     * Khách hàng tạo mới một ticket hỗ trợ gắn với hợp đồng thuê của mình (BR21.1 - BR21.4).
     */
    SupportTicketDetailResponse createTicket(UUID customerId, CreateSupportTicketRequest request);

    /**
     * Khách hàng xem danh sách các ticket hỗ trợ do chính mình tạo (BR21.1).
     */
    List<SupportTicketSummaryResponse> getMyTickets(UUID customerId);

    /**
     * Xem thông tin chi tiết một ticket và lịch sử trao đổi (BR21.1, BR21.4).
     */
    SupportTicketDetailResponse getTicketDetail(UUID currentUserId, UUID ticketId);

    /**
     * Gửi phản hồi vào ticket hỗ trợ (BR21.4, BR21.5).
     */
    SupportTicketReplyResponse addReply(UUID currentUserId, UUID ticketId, CreateTicketReplyRequest request);

    /**
     * Lấy toàn bộ danh sách các phản hồi của một ticket (BR21.1).
     */
    List<SupportTicketReplyResponse> getTicketReplies(UUID currentUserId, UUID ticketId);

    /**
     * Khách hàng hoặc nhân viên đóng ticket hỗ trợ (BR21.5).
     */
    SupportTicketDetailResponse closeTicket(UUID currentUserId, UUID ticketId, String closeReason);
}
