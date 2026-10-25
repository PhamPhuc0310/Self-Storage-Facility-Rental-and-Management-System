package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.support.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.impl.SupportTicketServiceImpl;
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
 * Kiểm thử nghiệp vụ Use Case UC21:
 * "Gửi và theo dõi yêu cầu hỗ trợ" (BR21.1 - BR21.5).
 */
@ExtendWith(MockitoExtension.class)
class UC21SupportTicketTest {

    @Mock
    private SupportTicketRepository supportTicketRepository;
    @Mock
    private SupportTicketReplyRepository supportTicketReplyRepository;
    @Mock
    private RentalContractRepository rentalContractRepository;
    @Mock
    private StorageUnitRepository storageUnitRepository;
    @Mock
    private FacilityRepository facilityRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserFacilityAssignmentRepository userFacilityAssignmentRepository;
    @Mock
    private ActivityLogRepository activityLogRepository;

    @InjectMocks
    private SupportTicketServiceImpl supportTicketService;

    private UUID customerId;
    private UUID otherCustomerId;
    private UUID staffId;
    private UUID contractId;
    private UUID unitId;
    private UUID facilityId;
    private UUID ticketId;

    private RentalContract contract;
    private StorageUnit unit;
    private Facility facility;
    private User customerUser;
    private User otherCustomerUser;
    private User staffUser;
    private SupportTicket ticket;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        contractId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        facilityId = UUID.randomUUID();
        ticketId = UUID.randomUUID();

        Role customerRole = new Role();
        customerRole.setRoleName("CUSTOMER");

        Role staffRole = new Role();
        staffRole.setRoleName("FACILITY_STAFF");

        customerUser = new User();
        customerUser.setUserId(customerId);
        customerUser.setFullName("Nguyễn Văn A");
        customerUser.setRole(customerRole);
        customerUser.setStatus("ACTIVE");

        otherCustomerUser = new User();
        otherCustomerUser.setUserId(otherCustomerId);
        otherCustomerUser.setFullName("Trần Văn B");
        otherCustomerUser.setRole(customerRole);
        otherCustomerUser.setStatus("ACTIVE");

        staffUser = new User();
        staffUser.setUserId(staffId);
        staffUser.setFullName("Lê Nhân Viên");
        staffUser.setRole(staffRole);
        staffUser.setStatus("ACTIVE");

        facility = new Facility();
        facility.setFacilityId(facilityId);
        facility.setName("SafeBox Quận 7");

        unit = new StorageUnit();
        unit.setUnitId(unitId);
        unit.setFacilityId(facilityId);
        unit.setUnitNumber("Q7-M101");

        contract = new RentalContract();
        contract.setContractId(contractId);
        contract.setContractNumber("SB-CT-2026-TEST");
        contract.setCustomerId(customerId);
        contract.setUnitId(unitId);
        contract.setStatus("ACTIVE");

        ticket = new SupportTicket();
        ticket.setTicketId(ticketId);
        ticket.setTicketNumber("TK-261023-A1B2C3");
        ticket.setContractId(contractId);
        ticket.setCustomerId(customerId);
        ticket.setFacilityId(facilityId);
        ticket.setIssueType("ACCESS_ISSUE");
        ticket.setTitle("Quên mã PIN ổ khóa");
        ticket.setDescription("Tôi không thể mở cửa kho vì quên mã");
        ticket.setStatus("OPEN");
        ticket.setPriority("NORMAL");
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("BR21.1: Chặn khách hàng tạo ticket gắn với hợp đồng của người khác -> 403 Forbidden")
    void testCreateTicket_NotOwnerOfContract() {
        // contract thuộc về customerId, nhưng otherCustomerId gọi API
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));

        CreateSupportTicketRequest request = new CreateSupportTicketRequest(
                contractId, "ACCESS_ISSUE", "Sự cố khóa", "Mô tả...", "NORMAL"
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportTicketService.createTicket(otherCustomerId, request));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertTrue(ex.getMessage().contains("chính mình đứng tên"));
    }

    @Test
    @DisplayName("BR21.2: Chặn loại sự cố không hợp lệ -> 400 Bad Request")
    void testCreateTicket_InvalidIssueType() {
        CreateSupportTicketRequest request = new CreateSupportTicketRequest(
                contractId, "UNKNOWN_TYPE", "Tiêu đề", "Mô tả", "NORMAL"
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportTicketService.createTicket(customerId, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("Loại sự cố không hợp lệ"));
    }

    @Test
    @DisplayName("BR21.3 & BR21.4: Tạo ticket thành công -> sinh mã TK-*, lọc bảo mật mật mã nhạy cảm, trạng thái OPEN")
    void testCreateTicket_Success_WithSensitiveDataSanitized() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customerUser));
        when(supportTicketRepository.existsByTicketNumber(anyString())).thenReturn(false);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            t.setTicketId(ticketId);
            return t;
        });

        // Request có chứa thông tin nhạy cảm (mật mã/password)
        CreateSupportTicketRequest request = new CreateSupportTicketRequest(
                contractId, "ACCESS_ISSUE", "Quên password: mysecret123", "Mã PIN: 9988 không mở được", "HIGH"
        );

        SupportTicketDetailResponse response = supportTicketService.createTicket(customerId, request);

        assertNotNull(response);
        assertEquals("OPEN", response.status());
        assertTrue(response.ticketNumber().startsWith("TK-"));
        // Xác nhận mật mã đã được lọc
        assertFalse(response.title().contains("mysecret123"));
        assertTrue(response.title().contains("[BẢO MẬT]"));
        assertFalse(response.description().contains("9988"));

        verify(supportTicketRepository).save(any(SupportTicket.class));
        verify(activityLogRepository).save(any(ActivityLog.class));
    }

    @Test
    @DisplayName("BR21.1: Khách hàng chỉ xem được ticket của mình, chặn người khác xem -> 403 Forbidden")
    void testGetTicketDetail_OtherCustomerForbidden() {
        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(otherCustomerId)).thenReturn(Optional.of(otherCustomerUser));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportTicketService.getTicketDetail(otherCustomerId, ticketId));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertTrue(ex.getMessage().contains("chỉ được xem yêu cầu hỗ trợ của chính mình"));
    }

    @Test
    @DisplayName("BR21.1: Nhân viên cơ sở được xem ticket thuộc cơ sở mình quản lý")
    void testGetTicketDetail_StaffAssignedFacilitySuccess() {
        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(staffId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId)); // Thuộc facility của ticket
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customerUser));
        when(supportTicketReplyRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of());

        SupportTicketDetailResponse response = supportTicketService.getTicketDetail(staffId, ticketId);

        assertNotNull(response);
        assertEquals(ticketId, response.ticketId());
        assertEquals("Quên mã PIN ổ khóa", response.title());
    }

    @Test
    @DisplayName("BR21.5: Chặn không cho gửi phản hồi vào ticket đã CLOSED -> 409 Conflict")
    void testAddReply_ClosedTicket_Conflict() {
        ticket.setStatus("CLOSED");
        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        CreateTicketReplyRequest request = new CreateTicketReplyRequest("Tôi vẫn cần hỗ trợ!");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                supportTicketService.addReply(customerId, ticketId, request));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("đã được đóng (CLOSED)"));
        verify(supportTicketReplyRepository, never()).save(any());
    }

    @Test
    @DisplayName("BR21.4 & BR21.5: Gửi phản hồi thành công vào ticket OPEN/IN_PROGRESS -> lưu lịch sử trao đổi")
    void testAddReply_Success() {
        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customerUser));
        when(supportTicketReplyRepository.save(any(SupportTicketReply.class))).thenAnswer(inv -> {
            SupportTicketReply r = inv.getArgument(0);
            r.setReplyId(UUID.randomUUID());
            return r;
        });

        CreateTicketReplyRequest request = new CreateTicketReplyRequest("Nhờ nhân viên kiểm tra giúp tôi sớm.");
        SupportTicketReplyResponse replyResponse = supportTicketService.addReply(customerId, ticketId, request);

        assertNotNull(replyResponse);
        assertEquals("Nhờ nhân viên kiểm tra giúp tôi sớm.", replyResponse.content());
        assertEquals(customerId, replyResponse.userId());
        assertEquals("CUSTOMER", replyResponse.userRole());

        verify(supportTicketReplyRepository).save(any(SupportTicketReply.class));
    }

    @Test
    @DisplayName("BR21.5: Đóng ticket thành công -> chuyển sang CLOSED, lưu vết ActivityLog")
    void testCloseTicket_Success() {
        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customerUser));
        when(supportTicketReplyRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of());

        SupportTicketDetailResponse response = supportTicketService.closeTicket(
                customerId, ticketId, "Vấn đề đã được nhân viên giải quyết xong.");

        assertNotNull(response);
        assertEquals("CLOSED", response.status());
        verify(activityLogRepository).save(any(ActivityLog.class));
    }
}
