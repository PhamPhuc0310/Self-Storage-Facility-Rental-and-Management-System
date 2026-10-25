package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.support.*;
import com.safebox.self_storage.service.SupportTicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller xử lý các API của Use Case UC21:
 * "Gửi và theo dõi yêu cầu hỗ trợ" (BR21.1 – BR21.5).
 */
@RestController
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    public SupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    private UUID getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        if (principal instanceof String str) {
            try {
                return UUID.fromString(str);
            } catch (IllegalArgumentException ignored) {}
        }
        throw new IllegalStateException("Không thể xác định danh tính người dùng hiện tại từ phiên đăng nhập");
    }

    /**
     * UC21: Khách hàng tạo ticket hỗ trợ gắn với hợp đồng thuê của mình (BR21.1 - BR21.4).
     */
    @PostMapping({"/api/support-tickets", "/api/v1/support-tickets"})
    public ResponseEntity<SupportTicketDetailResponse> createTicket(
            @RequestBody CreateSupportTicketRequest request) {
        UUID customerId = getCurrentUserId();
        SupportTicketDetailResponse response = supportTicketService.createTicket(customerId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * UC21: Khách hàng xem danh sách các yêu cầu hỗ trợ của mình (BR21.1).
     */
    @GetMapping({"/api/support-tickets/my", "/api/v1/support-tickets/my", "/api/support-tickets"})
    public ResponseEntity<List<SupportTicketSummaryResponse>> getMyTickets() {
        UUID customerId = getCurrentUserId();
        List<SupportTicketSummaryResponse> tickets = supportTicketService.getMyTickets(customerId);
        return ResponseEntity.ok(tickets);
    }

    /**
     * UC21: Xem chi tiết yêu cầu hỗ trợ và lịch sử trao đổi (BR21.1, BR21.4).
     */
    @GetMapping({"/api/support-tickets/{ticketId}", "/api/v1/support-tickets/{ticketId}"})
    public ResponseEntity<SupportTicketDetailResponse> getTicketDetail(
            @PathVariable("ticketId") UUID ticketId) {
        UUID currentUserId = getCurrentUserId();
        SupportTicketDetailResponse response = supportTicketService.getTicketDetail(currentUserId, ticketId);
        return ResponseEntity.ok(response);
    }

    /**
     * UC21: Gửi phản hồi vào ticket hỗ trợ (BR21.4, BR21.5).
     */
    @PostMapping({"/api/support-tickets/{ticketId}/replies", "/api/v1/support-tickets/{ticketId}/replies"})
    public ResponseEntity<SupportTicketReplyResponse> addReply(
            @PathVariable("ticketId") UUID ticketId,
            @RequestBody CreateTicketReplyRequest request) {
        UUID currentUserId = getCurrentUserId();
        SupportTicketReplyResponse reply = supportTicketService.addReply(currentUserId, ticketId, request);
        return ResponseEntity.ok(reply);
    }

    /**
     * UC21: Xem danh sách phản hồi của ticket (BR21.1).
     */
    @GetMapping({"/api/support-tickets/{ticketId}/replies", "/api/v1/support-tickets/{ticketId}/replies"})
    public ResponseEntity<List<SupportTicketReplyResponse>> getTicketReplies(
            @PathVariable("ticketId") UUID ticketId) {
        UUID currentUserId = getCurrentUserId();
        List<SupportTicketReplyResponse> replies = supportTicketService.getTicketReplies(currentUserId, ticketId);
        return ResponseEntity.ok(replies);
    }

    /**
     * UC21: Đóng ticket hỗ trợ (BR21.5).
     */
    @PostMapping({"/api/support-tickets/{ticketId}/close", "/api/v1/support-tickets/{ticketId}/close"})
    public ResponseEntity<SupportTicketDetailResponse> closeTicket(
            @PathVariable("ticketId") UUID ticketId,
            @RequestBody(required = false) Map<String, String> body) {
        UUID currentUserId = getCurrentUserId();
        String reason = (body != null) ? body.get("reason") : null;
        SupportTicketDetailResponse response = supportTicketService.closeTicket(currentUserId, ticketId, reason);
        return ResponseEntity.ok(response);
    }
}
