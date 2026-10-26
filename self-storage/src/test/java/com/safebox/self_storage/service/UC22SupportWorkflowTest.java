package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.support.AddSupportMessage;
import com.safebox.self_storage.dto.support.AssignSupportRequest;
import com.safebox.self_storage.dto.support.SupportRequestView;
import com.safebox.self_storage.dto.support.UpdateSupportStatus;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử nghiệp vụ Use Case UC22:
 * "Tiếp nhận, phân công và giải quyết hỗ trợ" (BR22.1 - BR22.5).
 */
@ExtendWith(MockitoExtension.class)
class UC22SupportWorkflowTest {

    @Mock
    private Sprint2Access access;
    @Mock
    private SupportTicketRepository ticketRepository;
    @Mock
    private SupportTicketReplyRepository replyRepository;
    @Mock
    private RentalContractRepository contractRepository;
    @Mock
    private StorageUnitRepository storageUnitRepository;
    @Mock
    private FacilityRepository facilityRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserFacilityAssignmentRepository assignmentRepository;
    @Mock
    private ActivityLogRepository logRepository;

    @InjectMocks
    private SupportRequestService supportRequestService;

    private UUID managerId;
    private UUID staffId;
    private UUID otherStaffId;
    private UUID customerId;
    private UUID facilityId;
    private UUID otherFacilityId;
    private UUID ticketId;
    private UUID contractId;

    private SupportTicket ticket;
    private User staffUser;

    @BeforeEach
    void setUp() {
        managerId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        otherStaffId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        facilityId = UUID.randomUUID();
        otherFacilityId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        contractId = UUID.randomUUID();

        ticket = new SupportTicket();
        ticket.setTicketId(ticketId);
        ticket.setTicketNumber("TK-20261026-0001");
        ticket.setCustomerId(customerId);
        ticket.setContractId(contractId);
        ticket.setFacilityId(facilityId);
        ticket.setIssueType("ACCESS_ISSUE");
        ticket.setTitle("Kẹt mã kho");
        ticket.setDescription("Không mở được khóa");
        ticket.setStatus("OPEN");
        ticket.setPriority("HIGH");
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());

        Role staffRole = new Role();
        staffRole.setRoleName("FACILITY_STAFF");

        staffUser = new User();
        staffUser.setUserId(staffId);
        staffUser.setFullName("Nhân viên hỗ trợ");
        staffUser.setStatus("ACTIVE");
        staffUser.setRole(staffRole);
    }

    @Test
    @DisplayName("BR22.1, BR22.2: Manager phân công ticket cho staff cùng cơ sở thành công")
    void testAssignTicketSuccess() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facilityId));
        when(access.today()).thenReturn(LocalDate.now());
        when(access.now()).thenReturn(LocalDateTime.now());
        when(access.currentId()).thenReturn(managerId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(assignmentRepository.isUserAssignedToFacility(staffId, facilityId, access.today())).thenReturn(true);
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(i -> i.getArgument(0));

        SupportRequestView view = supportRequestService.assign(ticketId, new AssignSupportRequest(staffId));

        assertNotNull(view);
        assertEquals(staffId, ticket.getAssignedTo());
        verify(logRepository).save(any(ActivityLog.class));
    }

    @Test
    @DisplayName("BR22.1: Manager cơ sở khác cố gắng phân công ticket bị từ chối 403 Forbidden")
    void testAssignTicketByManagerOfOtherFacilityDenied() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(otherFacilityId)); // Khác facilityId của ticket
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportRequestService.assign(ticketId, new AssignSupportRequest(staffId)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Ticket không thuộc cơ sở bạn quản lý"));
    }

    @Test
    @DisplayName("BR22.2: Manager phân công staff không hoạt động tại cơ sở của ticket bị từ chối 403 Forbidden")
    void testAssignTicketStaffNotInFacilityDenied() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facilityId));
        when(access.today()).thenReturn(LocalDate.now());
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(assignmentRepository.isUserAssignedToFacility(staffId, facilityId, access.today())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportRequestService.assign(ticketId, new AssignSupportRequest(staffId)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Nhân viên không hoạt động tại cơ sở của ticket"));
    }

    @Test
    @DisplayName("BR22.2: Staff không có quyền tự phân công ticket cho mình (chỉ Manager được phân công)")
    void testStaffCannotSelfAssignTicket() {
        when(access.role()).thenReturn("FACILITY_STAFF");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportRequestService.assign(ticketId, new AssignSupportRequest(staffId)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    @DisplayName("BR22.3: Không thể phân công ticket đã CLOSED (409 Conflict)")
    void testCannotAssignClosedTicket() {
        ticket.setStatus("CLOSED");
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facilityId));
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(assignmentRepository.isUserAssignedToFacility(staffId, facilityId, access.today())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportRequestService.assign(ticketId, new AssignSupportRequest(staffId)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Không thể phân công ticket đã đóng"));
    }

    @Test
    @DisplayName("BR22.3: Staff được giao xử lý chuyển trạng thái hợp lệ OPEN -> IN_PROGRESS")
    void testStaffUpdateStatusToInProgress() {
        ticket.setAssignedTo(staffId);
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(staffId);
        when(access.now()).thenReturn(LocalDateTime.now());
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(i -> i.getArgument(0));

        SupportRequestView view = supportRequestService.status(ticketId, new UpdateSupportStatus("IN_PROGRESS", "Đã tiếp nhận"));

        assertNotNull(view);
        assertEquals("IN_PROGRESS", ticket.getStatus());
    }

    @Test
    @DisplayName("BR22.4: Staff chuyển sang RESOLVED mà thiếu nội dung giải quyết bị từ chối 400 Bad Request")
    void testStaffResolveWithoutNoteThrowsBadRequest() {
        ticket.setStatus("IN_PROGRESS");
        ticket.setAssignedTo(staffId);
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(staffId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportRequestService.status(ticketId, new UpdateSupportStatus("RESOLVED", "   ")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Phải có nội dung giải quyết trước khi hoàn tất ticket"));
    }

    @Test
    @DisplayName("BR22.3, BR22.4: Staff hoàn tất RESOLVED kèm nội dung giải quyết thành công")
    void testStaffResolveWithNoteSuccess() {
        ticket.setStatus("IN_PROGRESS");
        ticket.setAssignedTo(staffId);
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(staffId);
        when(access.now()).thenReturn(LocalDateTime.now());
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(i -> i.getArgument(0));

        SupportRequestView view = supportRequestService.status(ticketId,
                new UpdateSupportStatus("RESOLVED", "Đã cấp lại mã truy cập mới cho khách"));

        assertEquals("RESOLVED", ticket.getStatus());
        assertEquals("Đã cấp lại mã truy cập mới cho khách", ticket.getResolutionNote());
        assertNotNull(ticket.getResolvedAt());
        verify(replyRepository).save(any(SupportTicketReply.class));
        verify(logRepository).save(any(ActivityLog.class));
    }

    @Test
    @DisplayName("BR22.2: Staff cố gắng thao tác trên ticket của staff khác bị từ chối 403 Forbidden")
    void testStaffCannotActOnOtherStaffTicket() {
        ticket.setAssignedTo(otherStaffId); // Giao cho staff khác
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(staffId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportRequestService.status(ticketId, new UpdateSupportStatus("IN_PROGRESS", "Nhận việc")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Bạn chưa được phân công ticket này"));
    }

    @Test
    @DisplayName("BR22.3: Chuyển trạng thái nhảy cóc (OPEN -> CLOSED) bị từ chối 409 Conflict")
    void testInvalidStatusTransitionThrowsConflict() {
        ticket.setAssignedTo(staffId);
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(staffId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportRequestService.status(ticketId, new UpdateSupportStatus("CLOSED", "Đóng luôn")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("không hợp lệ"));
    }

    @Test
    @DisplayName("BR22.3: Customer mở lại ticket RESOLVED kèm lý do thành công")
    void testCustomerReopenResolvedTicketSuccess() {
        ticket.setStatus("RESOLVED");
        ticket.setResolutionNote("Đã cấp lại mã");
        ticket.setResolvedAt(LocalDateTime.now());

        when(access.role()).thenReturn("CUSTOMER");
        when(access.currentId()).thenReturn(customerId);
        when(access.now()).thenReturn(LocalDateTime.now());
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(i -> i.getArgument(0));

        SupportRequestView view = supportRequestService.status(ticketId,
                new UpdateSupportStatus("OPEN", "Mã mới vẫn không mở được cửa"));

        assertEquals("OPEN", ticket.getStatus());
        assertNull(ticket.getResolutionNote());
        assertNull(ticket.getResolvedAt());
        verify(replyRepository).save(any(SupportTicketReply.class));
    }

    @Test
    @DisplayName("BR22.3: Customer mở lại ticket nhưng bỏ trống lý do bị từ chối 400 Bad Request")
    void testCustomerReopenWithoutReasonThrowsBadRequest() {
        ticket.setStatus("RESOLVED");
        when(access.role()).thenReturn("CUSTOMER");
        when(access.currentId()).thenReturn(customerId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportRequestService.status(ticketId, new UpdateSupportStatus("OPEN", "")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Vui lòng nêu lý do mở lại"));
    }

    @Test
    @DisplayName("BR22.5: Thêm phản hồi vào ticket đã CLOSED bị từ chối 409 Conflict")
    void testReplyToClosedTicketThrowsConflict() {
        ticket.setStatus("CLOSED");
        when(access.role()).thenReturn("CUSTOMER");
        when(access.currentId()).thenReturn(customerId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportRequestService.reply(ticketId, new AddSupportMessage("Tôi muốn hỏi thêm")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Không thể phản hồi ticket đã đóng"));
    }
}
