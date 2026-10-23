package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.support.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.SupportTicketService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SupportTicketServiceImpl implements SupportTicketService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> VALID_ISSUE_TYPES = Set.of(
            "ACCESS_ISSUE", "PAYMENT", "UNIT_CONDITION", "GOODS", "OTHER"
    );

    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketReplyRepository supportTicketReplyRepository;
    private final RentalContractRepository rentalContractRepository;
    private final StorageUnitRepository storageUnitRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final UserFacilityAssignmentRepository userFacilityAssignmentRepository;
    private final ActivityLogRepository activityLogRepository;

    public SupportTicketServiceImpl(
            SupportTicketRepository supportTicketRepository,
            SupportTicketReplyRepository supportTicketReplyRepository,
            RentalContractRepository rentalContractRepository,
            StorageUnitRepository storageUnitRepository,
            FacilityRepository facilityRepository,
            UserRepository userRepository,
            UserFacilityAssignmentRepository userFacilityAssignmentRepository,
            ActivityLogRepository activityLogRepository) {
        this.supportTicketRepository = supportTicketRepository;
        this.supportTicketReplyRepository = supportTicketReplyRepository;
        this.rentalContractRepository = rentalContractRepository;
        this.storageUnitRepository = storageUnitRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.userFacilityAssignmentRepository = userFacilityAssignmentRepository;
        this.activityLogRepository = activityLogRepository;
    }

    @Override
    @Transactional
    public SupportTicketDetailResponse createTicket(UUID customerId, CreateSupportTicketRequest request) {
        if (customerId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không được để trống");
        }
        if (request.getContractId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã hợp đồng không được để trống");
        }
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Tiêu đề yêu cầu hỗ trợ không được để trống");
        }
        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Nội dung mô tả sự cố không được để trống");
        }

        // BR21.2: Kiểm tra loại sự cố hợp lệ
        String issueType = (request.getIssueType() != null) ? request.getIssueType().trim().toUpperCase() : "OTHER";
        if (!VALID_ISSUE_TYPES.contains(issueType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Loại sự cố không hợp lệ. Hỗ trợ các loại: ACCESS_ISSUE, PAYMENT, UNIT_CONDITION, GOODS, OTHER");
        }

        // BR21.1: CUSTOMER chỉ tạo ticket gắn với hợp đồng của mình
        RentalContract contract = rentalContractRepository.findByContractId(request.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng"));

        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "Bạn chỉ được tạo yêu cầu hỗ trợ gắn với hợp đồng do chính mình đứng tên");
        }

        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin ô kho"));

        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);

        // BR21.3: Sinh mã ticket duy nhất
        String ticketNumber = generateUniqueTicketNumber();

        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber(ticketNumber);
        ticket.setContractId(contract.getContractId());
        ticket.setCustomerId(customerId);
        ticket.setFacilityId(unit.getFacilityId());
        ticket.setIssueType(issueType);
        ticket.setTitle(sanitizeSensitiveText(request.getTitle().trim()));
        ticket.setDescription(sanitizeSensitiveText(request.getDescription().trim()));
        ticket.setStatus("OPEN");
        ticket.setPriority(request.getPriority() != null ? request.getPriority().toUpperCase() : "NORMAL");
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);

        ticket = supportTicketRepository.save(ticket);

        // Lưu vết kiểm toán ActivityLog
        activityLogRepository.save(new ActivityLog(
                customerId,
                "CREATE_SUPPORT_TICKET",
                "SUPPORT_TICKET",
                ticket.getTicketId(),
                "Khách hàng tạo ticket hỗ trợ " + ticket.getTicketNumber() + " cho hợp đồng " + contract.getContractNumber(),
                now
        ));

        return buildDetailResponse(ticket, contract, unit, customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportTicketSummaryResponse> getMyTickets(UUID customerId) {
        if (customerId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }

        List<SupportTicket> tickets = supportTicketRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        return tickets.stream().map(this::mapToSummaryResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SupportTicketDetailResponse getTicketDetail(UUID currentUserId, UUID ticketId) {
        if (currentUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (ticketId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã ticket không được để trống");
        }

        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy ticket hỗ trợ"));

        // BR21.1: Kiểm tra quyền xem ticket
        validateAccessToTicket(currentUserId, ticket);

        RentalContract contract = rentalContractRepository.findByContractId(ticket.getContractId()).orElse(null);
        StorageUnit unit = (contract != null) ? storageUnitRepository.findById(contract.getUnitId()).orElse(null) : null;

        return buildDetailResponse(ticket, contract, unit, currentUserId);
    }

    @Override
    @Transactional
    public SupportTicketReplyResponse addReply(UUID currentUserId, UUID ticketId, CreateTicketReplyRequest request) {
        if (currentUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (ticketId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã ticket không được để trống");
        }
        if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Nội dung phản hồi không được để trống");
        }

        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy ticket hỗ trợ"));

        // BR21.1: Kiểm tra quyền truy cập
        validateAccessToTicket(currentUserId, ticket);

        // BR21.5: Không cho gửi phản hồi vào ticket đã CLOSED
        if ("CLOSED".equalsIgnoreCase(ticket.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Yêu cầu hỗ trợ này đã được đóng (CLOSED). Không thể gửi thêm phản hồi.");
        }

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Tài khoản người dùng không tồn tại"));

        String role = (user.getRole() != null) ? user.getRole().getRoleName() : "USER";
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);

        // BR21.4: Không hiển thị mật mã nhạy cảm trong phản hồi
        String safeContent = sanitizeSensitiveText(request.getContent().trim());

        SupportTicketReply reply = new SupportTicketReply();
        reply.setTicketId(ticket.getTicketId());
        reply.setUserId(currentUserId);
        reply.setUserRole(role);
        reply.setUserFullName(user.getFullName());
        reply.setContent(safeContent);
        reply.setCreatedAt(now);

        reply = supportTicketReplyRepository.save(reply);

        // Nếu nhân viên/quản lý phản hồi và ticket đang OPEN -> cập nhật sang IN_PROGRESS
        if (!"CUSTOMER".equalsIgnoreCase(role) && "OPEN".equalsIgnoreCase(ticket.getStatus())) {
            ticket.setStatus("IN_PROGRESS");
        }
        ticket.setUpdatedAt(now);
        supportTicketRepository.save(ticket);

        return new SupportTicketReplyResponse(
                reply.getReplyId(),
                reply.getTicketId(),
                reply.getUserId(),
                reply.getUserRole(),
                reply.getUserFullName(),
                reply.getContent(),
                reply.getCreatedAt(),
                true
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportTicketReplyResponse> getTicketReplies(UUID currentUserId, UUID ticketId) {
        if (currentUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (ticketId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã ticket không được để trống");
        }

        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy ticket hỗ trợ"));

        validateAccessToTicket(currentUserId, ticket);

        List<SupportTicketReply> replies = supportTicketReplyRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        return replies.stream()
                .map(r -> new SupportTicketReplyResponse(
                        r.getReplyId(),
                        r.getTicketId(),
                        r.getUserId(),
                        r.getUserRole(),
                        r.getUserFullName(),
                        r.getContent(),
                        r.getCreatedAt(),
                        r.getUserId().equals(currentUserId)
                ))
                .toList();
    }

    @Override
    @Transactional
    public SupportTicketDetailResponse closeTicket(UUID currentUserId, UUID ticketId, String closeReason) {
        if (currentUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (ticketId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã ticket không được để trống");
        }

        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy ticket hỗ trợ"));

        validateAccessToTicket(currentUserId, ticket);

        if ("CLOSED".equalsIgnoreCase(ticket.getStatus())) {
            RentalContract contract = rentalContractRepository.findByContractId(ticket.getContractId()).orElse(null);
            StorageUnit unit = (contract != null) ? storageUnitRepository.findById(contract.getUnitId()).orElse(null) : null;
            return buildDetailResponse(ticket, contract, unit, currentUserId);
        }

        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        ticket.setStatus("CLOSED");
        ticket.setResolvedAt(now);
        if (closeReason != null && !closeReason.isBlank()) {
            ticket.setResolutionNote(closeReason.trim());
        } else if (ticket.getResolutionNote() == null) {
            ticket.setResolutionNote("Yêu cầu đã được đóng bởi người dùng.");
        }
        ticket.setUpdatedAt(now);
        supportTicketRepository.save(ticket);

        activityLogRepository.save(new ActivityLog(
                currentUserId,
                "CLOSE_SUPPORT_TICKET",
                "SUPPORT_TICKET",
                ticket.getTicketId(),
                "Đóng ticket hỗ trợ " + ticket.getTicketNumber() + " | Lý do: " + ticket.getResolutionNote(),
                now
        ));

        RentalContract contract = rentalContractRepository.findByContractId(ticket.getContractId()).orElse(null);
        StorageUnit unit = (contract != null) ? storageUnitRepository.findById(contract.getUnitId()).orElse(null) : null;
        return buildDetailResponse(ticket, contract, unit, currentUserId);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private void validateAccessToTicket(UUID currentUserId, SupportTicket ticket) {
        if (ticket.getCustomerId().equals(currentUserId)) {
            return;
        }

        User user = userRepository.findById(currentUserId).orElse(null);
        if (user == null || user.getRole() == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập yêu cầu hỗ trợ này");
        }

        String role = user.getRole().getRoleName();
        if ("SYSTEM_ADMIN".equalsIgnoreCase(role)) {
            return;
        }

        if ("CUSTOMER".equalsIgnoreCase(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn chỉ được xem yêu cầu hỗ trợ của chính mình");
        }

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        List<UUID> assignedFacilityIds = userFacilityAssignmentRepository.findActiveFacilityIds(currentUserId, today);
        if (assignedFacilityIds.contains(ticket.getFacilityId())) {
            return;
        }

        throw new BusinessException(HttpStatus.FORBIDDEN, "Yêu cầu hỗ trợ không thuộc cơ sở bạn được phân công quản lý");
    }

    private String generateUniqueTicketNumber() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyMMdd");
        String datePrefix = LocalDate.now(VIETNAM_ZONE).format(dtf);
        for (int i = 0; i < 10; i++) {
            String randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            String candidate = "TK-" + datePrefix + "-" + randomPart;
            if (!supportTicketRepository.existsByTicketNumber(candidate)) {
                return candidate;
            }
        }
        return "TK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private String sanitizeSensitiveText(String text) {
        if (text == null) return "";
        // BR21.4: Không hiển thị mật mã truy cập nhạy cảm (PIN, Passcode, Password) trong log hoặc phản hồi công khai
        return text.replaceAll("(?i)(mật khẩu|password|mã pin|pin|passcode)\\s*[:=]\\s*([\\S]+)", "$1: [BẢO MẬT]");
    }

    private SupportTicketSummaryResponse mapToSummaryResponse(SupportTicket ticket) {
        RentalContract contract = rentalContractRepository.findByContractId(ticket.getContractId()).orElse(null);
        StorageUnit unit = (contract != null) ? storageUnitRepository.findById(contract.getUnitId()).orElse(null) : null;
        Facility facility = facilityRepository.findById(ticket.getFacilityId()).orElse(null);

        long repliesCount = supportTicketReplyRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getTicketId()).size();

        return new SupportTicketSummaryResponse(
                ticket.getTicketId(),
                ticket.getTicketNumber(),
                ticket.getContractId(),
                (contract != null) ? contract.getContractNumber() : "N/A",
                (unit != null) ? unit.getUnitId() : null,
                (unit != null) ? unit.getUnitNumber() : "N/A",
                ticket.getFacilityId(),
                (facility != null) ? facility.getName() : "SafeBox Facility",
                ticket.getIssueType(),
                SupportTicketSummaryResponse.getIssueTypeLabel(ticket.getIssueType()),
                ticket.getTitle(),
                ticket.getStatus(),
                SupportTicketSummaryResponse.getStatusLabel(ticket.getStatus()),
                ticket.getPriority(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                repliesCount
        );
    }

    private SupportTicketDetailResponse buildDetailResponse(
            SupportTicket ticket, RentalContract contract, StorageUnit unit, UUID currentUserId) {
        Facility facility = facilityRepository.findById(ticket.getFacilityId()).orElse(null);
        User customer = userRepository.findById(ticket.getCustomerId()).orElse(null);
        User assignedStaff = (ticket.getAssignedTo() != null) ? userRepository.findById(ticket.getAssignedTo()).orElse(null) : null;

        List<SupportTicketReply> rawReplies = supportTicketReplyRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getTicketId());
        List<SupportTicketReplyResponse> replies = rawReplies.stream()
                .map(r -> new SupportTicketReplyResponse(
                        r.getReplyId(),
                        r.getTicketId(),
                        r.getUserId(),
                        r.getUserRole(),
                        r.getUserFullName(),
                        r.getContent(),
                        r.getCreatedAt(),
                        r.getUserId().equals(currentUserId)
                ))
                .toList();

        boolean canReply = !"CLOSED".equalsIgnoreCase(ticket.getStatus());

        return new SupportTicketDetailResponse(
                ticket.getTicketId(),
                ticket.getTicketNumber(),
                ticket.getContractId(),
                (contract != null) ? contract.getContractNumber() : "N/A",
                ticket.getCustomerId(),
                (customer != null) ? customer.getFullName() : "Khách hàng",
                (customer != null) ? customer.getPhone() : "",
                (customer != null) ? customer.getEmail() : "",
                (unit != null) ? unit.getUnitId() : null,
                (unit != null) ? unit.getUnitNumber() : "N/A",
                ticket.getFacilityId(),
                (facility != null) ? facility.getName() : "SafeBox Facility",
                (facility != null) ? facility.getAddress() : "",
                ticket.getIssueType(),
                SupportTicketSummaryResponse.getIssueTypeLabel(ticket.getIssueType()),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                SupportTicketSummaryResponse.getStatusLabel(ticket.getStatus()),
                ticket.getPriority(),
                ticket.getAssignedTo(),
                (assignedStaff != null) ? assignedStaff.getFullName() : "Chưa phân công",
                ticket.getResolvedAt(),
                ticket.getResolutionNote(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                canReply,
                replies
        );
    }
}
