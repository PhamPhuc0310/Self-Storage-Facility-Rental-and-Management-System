package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.support.StaffFacilityAssignmentRequest;
import com.safebox.self_storage.dto.support.StaffScopeView;
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
 * Kiểm thử nghiệp vụ Use Case UC23:
 * "Quản lý nhân viên và phạm vi hoạt động" (BR23.1 - BR23.4).
 */
@ExtendWith(MockitoExtension.class)
class UC23StaffScopeTest {

    @Mock
    private Sprint2Access access;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FacilityRepository facilityRepository;
    @Mock
    private UserFacilityAssignmentRepository assignmentRepository;
    @Mock
    private SupportTicketRepository ticketRepository;
    @Mock
    private PickupAppointmentRepository pickupAppointmentRepository;
    @Mock
    private HandoverRecordRepository handoverRecordRepository;
    @Mock
    private ActivityLogRepository logRepository;

    @InjectMocks
    private StaffScopeService staffScopeService;

    private UUID adminId;
    private UUID staffId;
    private UUID nonStaffId;
    private UUID facilityId;
    private User staffUser;
    private User customerUser;
    private Facility facility;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        nonStaffId = UUID.randomUUID();
        facilityId = UUID.randomUUID();

        Role staffRole = new Role();
        staffRole.setRoleName("FACILITY_STAFF");

        staffUser = new User();
        staffUser.setUserId(staffId);
        staffUser.setFullName("Lê Nhân Viên");
        staffUser.setEmail("staff@safebox.vn");
        staffUser.setStatus("ACTIVE");
        staffUser.setRole(staffRole);

        Role customerRole = new Role();
        customerRole.setRoleName("CUSTOMER");

        customerUser = new User();
        customerUser.setUserId(nonStaffId);
        customerUser.setFullName("Trần Khách Hàng");
        customerUser.setStatus("ACTIVE");
        customerUser.setRole(customerRole);

        facility = new Facility();
        facility.setFacilityId(facilityId);
        facility.setName("SafeBox Quận 1");
        facility.setAddress("123 Lê Lợi, Q1, TP.HCM");
        facility.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("BR23.1, BR23.2: System Admin cấp quyền cơ sở cho staff thành công")
    void testGrantStaffFacilitySuccess() {
        when(access.role()).thenReturn("SYSTEM_ADMIN");
        when(access.currentId()).thenReturn(adminId);
        when(access.today()).thenReturn(LocalDate.now());
        when(access.now()).thenReturn(LocalDateTime.now());

        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(facilityRepository.existsById(facilityId)).thenReturn(true);
        when(assignmentRepository.isUserAssignedToFacility(staffId, facilityId, access.today())).thenReturn(false);
        when(assignmentRepository.findActiveFacilityIds(staffId, access.today())).thenReturn(List.of(facilityId));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));

        StaffScopeView view = staffScopeService.grant(staffId, new StaffFacilityAssignmentRequest(facilityId));

        assertNotNull(view);
        assertEquals(staffId, view.userId());
        verify(assignmentRepository).save(any(UserFacilityAssignment.class));
        verify(logRepository).save(any(ActivityLog.class));
    }

    @Test
    @DisplayName("BR23.2: Cấp phạm vi cơ sở đã active là idempotent, không tạo bản ghi trùng")
    void testGrantStaffFacilityIdempotent() {
        when(access.role()).thenReturn("SYSTEM_ADMIN");
        when(access.today()).thenReturn(LocalDate.now());

        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(facilityRepository.existsById(facilityId)).thenReturn(true);
        when(assignmentRepository.isUserAssignedToFacility(staffId, facilityId, access.today())).thenReturn(true);
        when(assignmentRepository.findActiveFacilityIds(staffId, access.today())).thenReturn(List.of(facilityId));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));

        StaffScopeView view = staffScopeService.grant(staffId, new StaffFacilityAssignmentRequest(facilityId));

        assertNotNull(view);
        verify(assignmentRepository, never()).save(any(UserFacilityAssignment.class));
    }

    @Test
    @DisplayName("BR23.1, BR23.4: Manager không có quyền cấp quyền cơ sở cho staff (403 Forbidden)")
    void testManagerCannotGrantStaffFacility() {
        when(access.role()).thenReturn("FACILITY_MANAGER");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                staffScopeService.grant(staffId, new StaffFacilityAssignmentRequest(facilityId)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Chỉ quản trị viên hệ thống"));
    }

    @Test
    @DisplayName("BR23.1: Cấp cơ sở không tồn tại bị từ chối 404 Not Found")
    void testGrantNonExistentFacilityThrowsNotFound() {
        when(access.role()).thenReturn("SYSTEM_ADMIN");
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(facilityRepository.existsById(facilityId)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                staffScopeService.grant(staffId, new StaffFacilityAssignmentRequest(facilityId)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertTrue(ex.getMessage().contains("Không tìm thấy cơ sở"));
    }

    @Test
    @DisplayName("BR23.1: Cấp cơ sở cho user không phải FACILITY_STAFF bị từ chối 400 Bad Request")
    void testGrantToNonStaffUserThrowsBadRequest() {
        when(access.role()).thenReturn("SYSTEM_ADMIN");
        when(userRepository.findById(nonStaffId)).thenReturn(Optional.of(customerUser));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                staffScopeService.grant(nonStaffId, new StaffFacilityAssignmentRequest(facilityId)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Tài khoản không phải nhân viên cơ sở"));
    }

    @Test
    @DisplayName("BR23.3: Thu hồi phạm vi cơ sở khi staff không còn việc đang mở thành công")
    void testRevokeStaffFacilitySuccess() {
        when(access.role()).thenReturn("SYSTEM_ADMIN");
        when(access.currentId()).thenReturn(adminId);
        when(access.today()).thenReturn(LocalDate.now());
        when(access.now()).thenReturn(LocalDateTime.now());

        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(ticketRepository.countOpenWork(eq(staffId), eq(facilityId), anyList())).thenReturn(0L);
        when(pickupAppointmentRepository.countScheduledAppointments(staffId, facilityId)).thenReturn(0L);
        when(handoverRecordRepository.countPendingHandoverWork(staffId, facilityId)).thenReturn(0L);

        UserFacilityAssignment activeAssignment = new UserFacilityAssignment(
                UUID.randomUUID(), staffId, facilityId, adminId, LocalDate.now().minusDays(10), null, "ACTIVE");

        when(assignmentRepository.findActiveAssignments(staffId, facilityId, access.today()))
                .thenReturn(List.of(activeAssignment));

        StaffScopeView view = staffScopeService.revoke(staffId, facilityId);

        assertNotNull(view);
        assertEquals("INACTIVE", activeAssignment.getStatus());
        assertEquals(LocalDate.now().minusDays(1), activeAssignment.getEndDate());
        verify(assignmentRepository).saveAll(anyList());
        verify(logRepository).save(any(ActivityLog.class));
    }

    @Test
    @DisplayName("BR23.3: Thu hồi khi staff còn ticket hỗ trợ đang mở bị từ chối 409 Conflict")
    void testRevokeStaffWithOpenTicketsThrowsConflict() {
        when(access.role()).thenReturn("SYSTEM_ADMIN");
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(ticketRepository.countOpenWork(eq(staffId), eq(facilityId), anyList())).thenReturn(2L); // 2 ticket mở

        BusinessException ex = assertThrows(BusinessException.class, () ->
                staffScopeService.revoke(staffId, facilityId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Nhân viên còn ticket đang mở"));
    }

    @Test
    @DisplayName("BR23.3: Thu hồi khi staff còn lịch nhận kho SCHEDULED bị từ chối 409 Conflict")
    void testRevokeStaffWithScheduledPickupsThrowsConflict() {
        when(access.role()).thenReturn("SYSTEM_ADMIN");
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(ticketRepository.countOpenWork(eq(staffId), eq(facilityId), anyList())).thenReturn(0L);
        when(pickupAppointmentRepository.countScheduledAppointments(staffId, facilityId)).thenReturn(1L); // 1 lịch hẹn

        BusinessException ex = assertThrows(BusinessException.class, () ->
                staffScopeService.revoke(staffId, facilityId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Nhân viên còn lịch nhận kho"));
    }

    @Test
    @DisplayName("BR23.3: Thu hồi khi staff còn biên bản kiểm tra trả kho mở bị từ chối 409 Conflict")
    void testRevokeStaffWithOpenHandoversThrowsConflict() {
        when(access.role()).thenReturn("SYSTEM_ADMIN");
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(ticketRepository.countOpenWork(eq(staffId), eq(facilityId), anyList())).thenReturn(0L);
        when(pickupAppointmentRepository.countScheduledAppointments(staffId, facilityId)).thenReturn(0L);
        when(handoverRecordRepository.countPendingHandoverWork(staffId, facilityId)).thenReturn(1L); // 1 biên bản trả kho

        BusinessException ex = assertThrows(BusinessException.class, () ->
                staffScopeService.revoke(staffId, facilityId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Nhân viên còn biên bản kiểm tra trả kho"));
    }

    @Test
    @DisplayName("BR23.1: Non-admin cố gắng thu hồi bị từ chối 403 Forbidden")
    void testNonAdminCannotRevokeStaffFacility() {
        when(access.role()).thenReturn("FACILITY_STAFF");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                staffScopeService.revoke(staffId, facilityId));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Chỉ quản trị viên hệ thống"));
    }

    @Test
    @DisplayName("BR23.3: Thu hồi khi staff không có phân công tại cơ sở bị từ chối 404 Not Found")
    void testRevokeNonExistentAssignmentThrowsNotFound() {
        when(access.role()).thenReturn("SYSTEM_ADMIN");
        when(access.today()).thenReturn(LocalDate.now());

        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(ticketRepository.countOpenWork(eq(staffId), eq(facilityId), anyList())).thenReturn(0L);
        when(pickupAppointmentRepository.countScheduledAppointments(staffId, facilityId)).thenReturn(0L);
        when(handoverRecordRepository.countPendingHandoverWork(staffId, facilityId)).thenReturn(0L);
        when(assignmentRepository.findActiveAssignments(staffId, facilityId, access.today())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                staffScopeService.revoke(staffId, facilityId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertTrue(ex.getMessage().contains("Nhân viên không có phạm vi hoạt động tại cơ sở này"));
    }
}
