package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.support.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC22 operations on the SUPPORT_TICKETS records created by UC21. */
@Service
public class SupportRequestService {
    private final Sprint2Access access;
    private final SupportTicketService customerTickets;
    private final SupportTicketRepository tickets;
    private final SupportTicketReplyRepository replies;
    private final RentalContractRepository contracts;
    private final UserRepository users;
    private final UserFacilityAssignmentRepository assignments;
    private final FacilityRepository facilities;
    private final ActivityLogRepository logs;

    public SupportRequestService(Sprint2Access access, SupportTicketService customerTickets,
            SupportTicketRepository tickets, SupportTicketReplyRepository replies,
            RentalContractRepository contracts, UserRepository users,
            UserFacilityAssignmentRepository assignments, FacilityRepository facilities,
            ActivityLogRepository logs) {
        this.access = access; this.customerTickets = customerTickets; this.tickets = tickets;
        this.replies = replies; this.contracts = contracts; this.users = users;
        this.assignments = assignments; this.facilities = facilities; this.logs = logs;
    }

    @Transactional
    public SupportRequestView create(CreateSupportRequest input) {
        role("CUSTOMER");
        String type = "ACCESS".equals(input.requestType()) ? "ACCESS_ISSUE" : input.requestType();
        var created = customerTickets.createTicket(access.currentId(), new CreateSupportTicketRequest(
                input.contractId(), type, input.title(), input.description(), input.priority()));
        return view(get(created.ticketId()));
    }

    @Transactional(readOnly = true)
    public List<SupportRequestView> mine() {
        role("CUSTOMER");
        return tickets.findByCustomerIdOrderByCreatedAtDesc(access.currentId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<SupportRequestView> queue() {
        List<SupportTicket> rows;
        if ("FACILITY_MANAGER".equals(access.role())) {
            List<UUID> ids = access.facilities();
            rows = ids.isEmpty() ? List.of() : tickets.findByFacilityIdInOrderByCreatedAtDesc(ids);
        } else if ("FACILITY_STAFF".equals(access.role())) {
            rows = tickets.findByAssignedToOrderByCreatedAtDesc(access.currentId()).stream()
                    .filter(t -> access.facilities().contains(t.getFacilityId())).toList();
        } else throw deny("Chỉ nhân viên hoặc quản lý cơ sở được xem hàng đợi.");
        return rows.stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public SupportRequestView detail(UUID id) {
        SupportTicket ticket = get(id); read(ticket); return view(ticket);
    }

    @Transactional
    public SupportRequestView assign(UUID id, AssignSupportRequest input) {
        role("FACILITY_MANAGER");
        SupportTicket ticket = get(id); manager(ticket);
        if ("CLOSED".equals(ticket.getStatus())) throw conflict("Không thể phân công ticket đã đóng.");
        User staff = users.findById(input.staffId()).orElseThrow(() -> notFound("Không tìm thấy nhân viên."));
        if (!"ACTIVE".equals(staff.getStatus()) || staff.getRole() == null
                || !"FACILITY_STAFF".equals(staff.getRole().getRoleName())
                || !assignments.isUserAssignedToFacility(input.staffId(), ticket.getFacilityId(), access.today())) {
            throw deny("Nhân viên không hoạt động tại cơ sở của ticket.");
        }
        ticket.setAssignedTo(input.staffId()); ticket.setUpdatedAt(access.now());
        tickets.save(ticket); audit("ASSIGN_SUPPORT_TICKET", ticket, "staffId=" + input.staffId());
        return view(ticket);
    }

    @Transactional
    public SupportRequestView status(UUID id, UpdateSupportStatus input) {
        SupportTicket ticket = get(id);
        String current = ticket.getStatus(), next = input.status();
        if ("OPEN".equals(next)) {
            if (!"CUSTOMER".equals(access.role()) || !access.currentId().equals(ticket.getCustomerId())
                    || !"RESOLVED".equals(current)) throw deny("Chỉ khách hàng được mở lại ticket đã giải quyết.");
            if (blank(input.note())) throw bad("Cần lý do mở lại ticket.");
            ticket.setResolvedAt(null); ticket.setResolutionNote(null);
            addMessage(ticket, input.note());
        } else {
            worker(ticket);
            if (!("OPEN".equals(current) && "IN_PROGRESS".equals(next)
                    || "IN_PROGRESS".equals(current) && "RESOLVED".equals(next)
                    || "RESOLVED".equals(current) && "CLOSED".equals(next))) {
                throw conflict("Chuyển trạng thái ticket không hợp lệ.");
            }
            if (("RESOLVED".equals(next) || "CLOSED".equals(next))
                    && blank(input.note()) && blank(ticket.getResolutionNote())) {
                throw bad("Phải có nội dung giải quyết trước khi hoàn tất ticket.");
            }
            if (!blank(input.note())) {
                ticket.setResolutionNote(sanitize(input.note())); addMessage(ticket, input.note());
            }
            if ("RESOLVED".equals(next)) ticket.setResolvedAt(access.now());
        }
        ticket.setStatus(next); ticket.setUpdatedAt(access.now()); tickets.save(ticket);
        audit("UPDATE_SUPPORT_STATUS", ticket, "status=" + next);
        return view(ticket);
    }

    @Transactional
    public SupportRequestView reply(UUID id, AddSupportMessage input) {
        SupportTicket ticket = get(id); read(ticket);
        if ("CLOSED".equals(ticket.getStatus())) throw conflict("Không thể phản hồi ticket đã đóng.");
        addMessage(ticket, input.message());
        if (!"CUSTOMER".equals(access.role()) && "OPEN".equals(ticket.getStatus())) {
            ticket.setStatus("IN_PROGRESS");
        }
        ticket.setUpdatedAt(access.now()); tickets.save(ticket);
        audit("REPLY_SUPPORT_TICKET", ticket, "");
        return view(ticket);
    }

    private void addMessage(SupportTicket ticket, String content) {
        SupportTicketReply reply = new SupportTicketReply();
        reply.setTicketId(ticket.getTicketId()); reply.setUserId(access.currentId());
        reply.setUserRole(access.role()); reply.setUserFullName(access.currentUser().getFullName());
        reply.setContent(sanitize(content)); reply.setCreatedAt(access.now()); replies.save(reply);
    }

    private String sanitize(String text) {
        return text.trim().replaceAll("(?i)(mật khẩu|password|mã pin|pin|passcode)\\s*[:=]\\s*([^\\s]+)", "$1: [BẢO MẬT]");
    }

    private SupportRequestView view(SupportTicket ticket) {
        RentalContract contract = contracts.findById(ticket.getContractId()).orElse(null);
        Facility facility = facilities.findById(ticket.getFacilityId()).orElse(null);
        User customer = users.findById(ticket.getCustomerId()).orElse(null);
        User staff = ticket.getAssignedTo() == null ? null : users.findById(ticket.getAssignedTo()).orElse(null);
        var messages = replies.findByTicketIdOrderByCreatedAtAsc(ticket.getTicketId()).stream()
                .map(r -> new SupportRequestView.MessageView(r.getReplyId(), r.getUserId(),
                        r.getUserFullName(), r.getContent(), r.getCreatedAt())).toList();
        return new SupportRequestView(ticket.getTicketId(), ticket.getContractId(),
                contract == null ? "" : contract.getContractNumber(), ticket.getFacilityId(),
                facility == null ? "" : facility.getName(), ticket.getIssueType(), ticket.getTitle(),
                ticket.getDescription(), ticket.getPriority(), ticket.getStatus(), ticket.getCreatedAt(),
                ticket.getResolvedAt(), ticket.getResolutionNote(), ticket.getCustomerId(),
                customer == null ? "" : customer.getFullName(), ticket.getAssignedTo(),
                staff == null ? null : staff.getFullName(), messages);
    }

    private SupportTicket get(UUID id) {
        return tickets.findById(id).orElseThrow(() -> notFound("Không tìm thấy ticket hỗ trợ."));
    }
    private void role(String expected) {
        if (!expected.equals(access.role())) throw deny("Bạn không có quyền thực hiện thao tác này.");
    }
    private void manager(SupportTicket ticket) {
        if (!access.facilities().contains(ticket.getFacilityId())) throw deny("Ticket không thuộc cơ sở bạn quản lý.");
    }
    private void worker(SupportTicket ticket) {
        if ("FACILITY_MANAGER".equals(access.role())) manager(ticket);
        else if (!"FACILITY_STAFF".equals(access.role())
                || !access.currentId().equals(ticket.getAssignedTo())
                || !access.facilities().contains(ticket.getFacilityId())) throw deny("Bạn chưa được phân công ticket này.");
    }
    private void read(SupportTicket ticket) {
        if ("CUSTOMER".equals(access.role()) && access.currentId().equals(ticket.getCustomerId())) return;
        worker(ticket);
    }
    private void audit(String action, SupportTicket ticket, String detail) {
        logs.save(new ActivityLog(access.currentId(), action, "SUPPORT_TICKET", ticket.getTicketId(), detail, access.now()));
    }
    private boolean blank(String text) { return text == null || text.isBlank(); }
    private BusinessException bad(String text) { return new BusinessException(HttpStatus.BAD_REQUEST, text); }
    private BusinessException deny(String text) { return new BusinessException(HttpStatus.FORBIDDEN, text); }
    private BusinessException conflict(String text) { return new BusinessException(HttpStatus.CONFLICT, text); }
    private BusinessException notFound(String text) { return new BusinessException(HttpStatus.NOT_FOUND, text); }
}
