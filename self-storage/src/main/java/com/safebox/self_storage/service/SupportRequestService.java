package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.support.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service xử lý nghiệp vụ Use Case UC22:
 * "Tiếp nhận, phân công và giải quyết hỗ trợ" (BR22.1 – BR22.5).
 * Đồng bộ lưu trữ và truy vấn trên bảng SUPPORT_TICKETS và SUPPORT_TICKET_REPLIES của UC21.
 */
@Service
public class SupportRequestService {

    private final Sprint2Access access;
    private final SupportTicketRepository ticketRepository;
    private final SupportTicketReplyRepository replyRepository;
    private final RentalContractRepository contractRepository;
    private final StorageUnitRepository storageUnitRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final UserFacilityAssignmentRepository assignmentRepository;
    private final ActivityLogRepository logRepository;

    public SupportRequestService(Sprint2Access access,
                                 SupportTicketRepository ticketRepository,
                                 SupportTicketReplyRepository replyRepository,
                                 RentalContractRepository contractRepository,
                                 StorageUnitRepository storageUnitRepository,
                                 FacilityRepository facilityRepository,
                                 UserRepository userRepository,
                                 UserFacilityAssignmentRepository assignmentRepository,
                                 ActivityLogRepository logRepository) {
        this.access = access;
        this.ticketRepository = ticketRepository;
        this.replyRepository = replyRepository;
        this.contractRepository = contractRepository;
        this.storageUnitRepository = storageUnitRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.logRepository = logRepository;
    }

    /**
     * Tạo ticket hỗ trợ mới gắn với hợp đồng thuê của khách hàng.
     */
    @Transactional
    public SupportRequestView create(CreateSupportRequest request) {
        requireRole("CUSTOMER");

        RentalContract contract = contractRepository.findById(request.contractId())
                .orElseThrow(() -> notFound("Không tìm thấy hợp đồng."));

        if (!access.currentId().equals(contract.customerId)) {
            throw forbidden("Bạn chỉ được tạo ticket cho hợp đồng của mình.");
        }

        StorageUnit unit = storageUnitRepository.findById(contract.unitId)
                .orElseThrow(() -> notFound("Không tìm thấy ô kho của hợp đồng."));

        LocalDateTime now = access.now();
        String ticketNumber = generateTicketNumber();

        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber(ticketNumber);
        ticket.setContractId(contract.contractId);
        ticket.setCustomerId(access.currentId());
        ticket.setFacilityId(unit.getFacilityId());
        ticket.setIssueType(request.requestType());
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setPriority(request.priority());
        ticket.setStatus("OPEN");
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);

        ticket = ticketRepository.save(ticket);

        audit("CREATE_SUPPORT_REQUEST", ticket, "Ticket " + ticket.getTicketNumber() + " created");
        return toView(ticket);
    }

    /**
     * Khách hàng xem danh sách ticket của chính mình.
     */
    @Transactional(readOnly = true)
    public List<SupportRequestView> mine() {
        requireRole("CUSTOMER");
        return ticketRepository.findByCustomerIdOrderByCreatedAtDesc(access.currentId())
                .stream()
                .map(this::toView)
                .toList();
    }

    /**
     * Manager xem hàng đợi tại cơ sở phụ trách, hoặc Staff xem các ticket được phân công.
     */
    @Transactional(readOnly = true)
    public List<SupportRequestView> queue() {
        String role = access.role();
        List<SupportTicket> rows;

        if ("FACILITY_MANAGER".equals(role)) {
            List<UUID> managedFacilities = access.facilities();
            if (managedFacilities.isEmpty()) {
                rows = List.of();
            } else {
                rows = ticketRepository.findByFacilityIdInOrderByCreatedAtDesc(managedFacilities);
            }
        } else if ("FACILITY_STAFF".equals(role)) {
            rows = ticketRepository.findByAssignedToOrderByCreatedAtDesc(access.currentId());
        } else {
            throw forbidden("Chỉ nhân viên hoặc quản lý cơ sở được xem hàng đợi.");
        }

        return rows.stream().map(this::toView).toList();
    }

    /**
     * Xem chi tiết ticket và lịch sử trao đổi.
     */
    @Transactional(readOnly = true)
    public SupportRequestView detail(UUID id) {
        SupportTicket ticket = getTicket(id);
        verifyReadPermission(ticket);
        return toView(ticket);
    }

    /**
     * BR22.1, BR22.2: Manager phân công ticket cho nhân viên tại cơ sở.
     */
    @Transactional
    public SupportRequestView assign(UUID id, AssignSupportRequest request) {
        requireRole("FACILITY_MANAGER");
        SupportTicket ticket = getTicket(id);

        // BR22.1: Manager chỉ phân công ticket tại cơ sở mình quản lý
        requireManagerOfFacility(ticket.getFacilityId());

        User staff = userRepository.findById(request.staffId())
                .orElseThrow(() -> notFound("Không tìm thấy nhân viên."));

        // BR22.2: Staff phải là tài khoản ACTIVE, vai trò FACILITY_STAFF và được phân công tại cơ sở của ticket
        if (!"ACTIVE".equals(staff.getStatus())
                || staff.getRole() == null
                || !"FACILITY_STAFF".equals(staff.getRole().getRoleName())
                || !assignmentRepository.isUserAssignedToFacility(request.staffId(), ticket.getFacilityId(), access.today())) {
            throw forbidden("Nhân viên không hoạt động tại cơ sở của ticket.");
        }

        if ("CLOSED".equals(ticket.getStatus())) {
            throw conflict("Không thể phân công ticket đã đóng.");
        }

        UUID previousStaffId = ticket.getAssignedTo();
        ticket.setAssignedTo(request.staffId());
        ticket.setUpdatedAt(access.now());
        ticketRepository.save(ticket);

        audit("ASSIGN_SUPPORT_REQUEST", ticket,
                "staffId=" + request.staffId() + (previousStaffId != null ? ", previousStaffId=" + previousStaffId : ""));

        return toView(ticket);
    }

    /**
     * BR22.3, BR22.4: Chuyển trạng thái ticket và cập nhật nội dung giải quyết.
     */
    @Transactional
    public SupportRequestView status(UUID id, UpdateSupportStatus request) {
        SupportTicket ticket = getTicket(id);
        String nextStatus = request.status();
        String currentStatus = ticket.getStatus();
        String role = access.role();

        if ("OPEN".equals(nextStatus)) {
            // BR22.3: Chỉ khách hàng sở hữu ticket mới được mở lại ticket khi đang ở RESOLVED
            if (!"CUSTOMER".equals(role) || !access.currentId().equals(ticket.getCustomerId())) {
                throw forbidden("Chỉ khách hàng tạo ticket mới được mở lại.");
            }
            if (!"RESOLVED".equals(currentStatus)) {
                throw conflict("Chỉ được mở lại ticket khi đang ở trạng thái Đã giải quyết (RESOLVED).");
            }
            if (isBlank(request.note())) {
                throw badRequest("Vui lòng nêu lý do mở lại ticket.");
            }

            ticket.setStatus("OPEN");
            ticket.setResolvedAt(null);
            ticket.setResolutionNote(null);
            ticket.setUpdatedAt(access.now());
            addReplyMessage(ticket, request.note());
        } else {
            // Phải là Staff hoặc Manager
            if (!"FACILITY_STAFF".equals(role) && !"FACILITY_MANAGER".equals(role)) {
                throw forbidden("Bạn không có quyền xử lý ticket.");
            }

            if ("FACILITY_MANAGER".equals(role)) {
                requireManagerOfFacility(ticket.getFacilityId());
            } else {
                // BR22.2: Staff chỉ xử lý ticket được giao cho mình
                if (!access.currentId().equals(ticket.getAssignedTo())) {
                    throw forbidden("Bạn chưa được phân công ticket này.");
                }
            }

            // BR22.3: Chuyển trạng thái tuần tự hợp lệ: OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED
            boolean validTransition =
                    ("OPEN".equals(currentStatus) && "IN_PROGRESS".equals(nextStatus)) ||
                    ("IN_PROGRESS".equals(currentStatus) && "RESOLVED".equals(nextStatus)) ||
                    ("RESOLVED".equals(currentStatus) && "CLOSED".equals(nextStatus));

            if (!validTransition) {
                throw conflict("Chuyển trạng thái ticket không hợp lệ từ " + currentStatus + " sang " + nextStatus + ".");
            }

            // BR22.4: Không được đóng ticket hoặc hoàn tất khi thiếu nội dung giải quyết
            if (("RESOLVED".equals(nextStatus) || "CLOSED".equals(nextStatus))
                    && isBlank(request.note())
                    && isBlank(ticket.getResolutionNote())) {
                throw badRequest("Phải có nội dung giải quyết trước khi hoàn tất ticket.");
            }

            if (!isBlank(request.note())) {
                ticket.setResolutionNote(request.note().trim());
                addReplyMessage(ticket, request.note().trim());
            }

            ticket.setStatus(nextStatus);
            ticket.setUpdatedAt(access.now());

            if ("RESOLVED".equals(nextStatus)) {
                ticket.setResolvedAt(access.now());
            }
        }

        ticketRepository.save(ticket);
        audit("UPDATE_SUPPORT_STATUS", ticket, "status=" + ticket.getStatus());
        return toView(ticket);
    }

    /**
     * BR22.5: Thêm phản hồi / trao đổi trong ticket hỗ trợ.
     */
    @Transactional
    public SupportRequestView reply(UUID id, AddSupportMessage request) {
        SupportTicket ticket = getTicket(id);
        verifyReadPermission(ticket);

        if ("CLOSED".equals(ticket.getStatus())) {
            throw conflict("Không thể phản hồi ticket đã đóng.");
        }

        String role = access.role();
        if ("FACILITY_STAFF".equals(role) && !access.currentId().equals(ticket.getAssignedTo())) {
            throw forbidden("Bạn chưa được phân công ticket này.");
        }
        if ("FACILITY_MANAGER".equals(role)) {
            requireManagerOfFacility(ticket.getFacilityId());
        }

        addReplyMessage(ticket, request.message());
        ticket.setUpdatedAt(access.now());
        ticketRepository.save(ticket);

        audit("REPLY_SUPPORT_REQUEST", ticket, "New reply added");
        return toView(ticket);
    }

    private void verifyReadPermission(SupportTicket ticket) {
        String role = access.role();
        if ("CUSTOMER".equals(role) && access.currentId().equals(ticket.getCustomerId())) {
            return;
        }
        if ("FACILITY_STAFF".equals(role) && access.currentId().equals(ticket.getAssignedTo())) {
            return;
        }
        if ("FACILITY_MANAGER".equals(role) && access.facilities().contains(ticket.getFacilityId())) {
            return;
        }
        throw forbidden("Bạn không có quyền xem ticket này.");
    }

    private SupportTicket getTicket(UUID id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy ticket hỗ trợ."));
    }

    private void requireRole(String expectedRole) {
        if (!expectedRole.equals(access.role())) {
            throw forbidden("Bạn không có quyền thực hiện thao tác này.");
        }
    }

    private void requireManagerOfFacility(UUID facilityId) {
        if (!access.facilities().contains(facilityId)) {
            throw forbidden("Ticket không thuộc cơ sở bạn quản lý.");
        }
    }

    private void addReplyMessage(SupportTicket ticket, String messageText) {
        User author = userRepository.findById(access.currentId()).orElse(null);
        SupportTicketReply reply = new SupportTicketReply();
        reply.setReplyId(UUID.randomUUID());
        reply.setTicketId(ticket.getTicketId());
        reply.setUserId(access.currentId());
        reply.setUserRole(access.role());
        reply.setUserFullName(author != null ? author.getFullName() : access.role());
        reply.setContent(messageText.trim());
        reply.setCreatedAt(access.now());
        replyRepository.save(reply);
    }

    private void audit(String actionType, SupportTicket ticket, String details) {
        logRepository.save(new ActivityLog(
                access.currentId(),
                actionType,
                "SUPPORT_TICKET",
                ticket.getTicketId(),
                details,
                access.now()
        ));
    }

    private SupportRequestView toView(SupportTicket ticket) {
        RentalContract contract = contractRepository.findById(ticket.getContractId()).orElse(null);
        Facility facility = facilityRepository.findById(ticket.getFacilityId()).orElse(null);
        User customer = userRepository.findById(ticket.getCustomerId()).orElse(null);
        User staff = ticket.getAssignedTo() != null ? userRepository.findById(ticket.getAssignedTo()).orElse(null) : null;

        List<SupportTicketReply> replies = replyRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getTicketId());
        List<SupportRequestView.MessageView> messageViews = replies.stream()
                .map(r -> new SupportRequestView.MessageView(
                        r.getReplyId(),
                        r.getUserId(),
                        r.getUserFullName() != null ? r.getUserFullName() : "",
                        r.getContent(),
                        r.getCreatedAt()
                ))
                .toList();

        return new SupportRequestView(
                ticket.getTicketId(),
                ticket.getContractId(),
                contract != null ? contract.contractNumber : "",
                ticket.getFacilityId(),
                facility != null ? facility.getName() : "",
                ticket.getIssueType(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getResolvedAt(),
                ticket.getResolutionNote(),
                ticket.getCustomerId(),
                customer != null ? customer.getFullName() : "",
                ticket.getAssignedTo(),
                staff != null ? staff.getFullName() : null,
                messageViews
        );
    }

    private String generateTicketNumber() {
        String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomSuffix = String.format("%04d", new Random().nextInt(10000));
        return "TK-" + datePrefix + "-" + randomSuffix;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, message);
    }
}
