package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.CompleteReturnInspectionRequest;
import com.safebox.self_storage.dto.CreateReturnScheduleRequest;
import com.safebox.self_storage.dto.response.FacilityReturnTaskItemResponse;
import com.safebox.self_storage.dto.response.ReturnInspectionDetailResponse;
import com.safebox.self_storage.dto.response.ReturnScheduleResponse;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.impl.ReturnInspectionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReturnInspectionServiceTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock private RentalContractRepository rentalContractRepository;
    @Mock private StorageUnitRepository storageUnitRepository;
    @Mock private FacilityRepository facilityRepository;
    @Mock private UserRepository userRepository;
    @Mock private HandoverRecordRepository handoverRecordRepository;
    @Mock private InspectionReportRepository inspectionReportRepository;
    @Mock private UserFacilityAssignmentRepository userFacilityAssignmentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private EarlyReturnService earlyReturns;
    @Mock private PickupAppointmentRepository pickupAppointmentRepository;
    @Mock private StaffRegionAssignmentRepository staffRegionAssignmentRepository;
    @Mock private ActivityLogRepository activityLogRepository;

    @InjectMocks
    private ReturnInspectionServiceImpl returnInspectionService;

    private UUID customerId;
    private UUID otherCustomerId;
    private UUID staffId;
    private UUID managerId;
    private UUID contractId;
    private UUID unitId;
    private UUID facilityId;
    private UUID handoverId;

    private RentalContract contract;
    private StorageUnit storageUnit;
    private Facility facility;
    private User customer;
    private User staff;
    private User manager;
    private HandoverRecord scheduledHandover;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        contractId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        facilityId = UUID.randomUUID();
        handoverId = UUID.randomUUID();
        lenient().when(handoverRecordRepository.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "COMPLETED"))
                .thenReturn(Optional.empty());

        Role customerRole = new Role();
        customerRole.setRoleId(1);
        customerRole.setRoleName("CUSTOMER");

        Role staffRole = new Role();
        staffRole.setRoleId(2);
        staffRole.setRoleName("FACILITY_STAFF");

        Role managerRole = new Role();
        managerRole.setRoleId(3);
        managerRole.setRoleName("FACILITY_MANAGER");

        customer = new User();
        customer.setUserId(customerId);
        customer.setFullName("Nguyễn Văn An");
        customer.setEmail("an.nguyen@test.com");
        customer.setPhone("0901234567");
        customer.setRole(customerRole);

        staff = new User();
        staff.setUserId(staffId);
        staff.setFullName("Phạm Gia Huy");
        staff.setEmail("huy.pham@safebox.vn");
        staff.setRole(staffRole);
        staff.setStatus("ACTIVE");

        manager = new User();
        manager.setUserId(managerId);
        manager.setFullName("Đặng Hoàng Long");
        manager.setEmail("long.dang@safebox.vn");
        manager.setRole(managerRole);

        facility = new Facility();
        facility.setFacilityId(facilityId);
        facility.setName("SafeBox Quận 7");
        facility.setAddress("123 Nguyễn Thị Thập, Q7, TP.HCM");
        facility.setPhone("19008868");
        facility.setOpeningTime(LocalTime.of(7, 0));
        facility.setClosingTime(LocalTime.of(21, 0));

        storageUnit = new StorageUnit();
        storageUnit.setUnitId(unitId);
        storageUnit.setFacilityId(facilityId);
        storageUnit.setTypeId(1);
        storageUnit.setUnitNumber("Q7-M101");
        storageUnit.setFloor("Tầng 1");
        storageUnit.setZone("Khu A");
        storageUnit.setStatus("OCCUPIED");

        contract = new RentalContract();
        contract.setContractId(contractId);
        contract.setContractNumber("SB-CT-2026-TEST01");
        contract.setReservationId(UUID.randomUUID());
        contract.setCustomerId(customerId);
        contract.setUnitId(unitId);
        contract.setStartDate(LocalDate.now(VIETNAM_ZONE).minusDays(10));
        contract.setEndDate(LocalDate.now(VIETNAM_ZONE).plusDays(5));
        contract.setMonthlyRentalFee(new BigDecimal("1500000.00"));
        contract.setDepositAmount(new BigDecimal("1500000.00"));
        contract.setStatus("ACTIVE");
        lenient().when(rentalContractRepository.lockById(contractId)).thenReturn(Optional.of(contract));

        scheduledHandover = new HandoverRecord();
        scheduledHandover.setHandoverId(handoverId);
        scheduledHandover.setContractId(contractId);
        scheduledHandover.setUnitId(unitId);
        scheduledHandover.setStaffId(staffId);
        scheduledHandover.setHandoverType("RETURN");
        scheduledHandover.setScheduledAt(LocalDateTime.of(contract.getEndDate(), LocalTime.of(10, 0)));
        scheduledHandover.setStatus("SCHEDULED");
    }

    // =========================================================================
    // BR16.1 & BR16.2: Đặt lịch trả kho của khách hàng
    // =========================================================================

    @Test
    void staffWithoutFacilityAccessCannotEnumerateReturnTasksByFacilityId() {
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(staffId), any(LocalDate.class)))
                .thenReturn(List.of());
        BusinessException error = assertThrows(BusinessException.class, () ->
                returnInspectionService.getFacilityReturnAppointments(staffId, facilityId, null));
        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(handoverRecordRepository);
    }

    @Test
    void staffCannotReadAnotherStaffsReturnRecord() {
        scheduledHandover.setStaffId(UUID.randomUUID());
        when(handoverRecordRepository.findById(handoverId)).thenReturn(Optional.of(scheduledHandover));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(staffId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));
        BusinessException error = assertThrows(BusinessException.class, () ->
                returnInspectionService.getReturnInspectionDetail(staffId, handoverId));
        assertEquals(HttpStatus.FORBIDDEN, error.status());
    }

    @Test
    void customerCanReadReturnAppointmentBeforeStaffAssignment() {
        scheduledHandover.setStaffId(null);
        when(handoverRecordRepository.findById(handoverId)).thenReturn(Optional.of(scheduledHandover));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));

        var detail = returnInspectionService.getReturnInspectionDetail(customerId, handoverId);

        assertNull(detail.inspectorId());
        assertEquals("Chưa phân công", detail.inspectorName());
        verify(userRepository, never()).findById(isNull());
    }

    @Test
    @DisplayName("BR16.1, BR16.2: Đặt lịch trả kho thành công khi đúng hạn và trong giờ hoạt động")
    void testScheduleReturnAppointment_Success() {
        LocalDateTime apptTime = LocalDateTime.of(contract.getEndDate(), LocalTime.of(10, 0));
        CreateReturnScheduleRequest request = new CreateReturnScheduleRequest(
                contractId, apptTime, "Khách xin hỗ trợ đẩy hàng", true, true);

        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(handoverRecordRepository.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED"))
                .thenReturn(Optional.empty());
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(handoverRecordRepository.save(any(HandoverRecord.class))).thenAnswer(invocation -> {
            HandoverRecord ho = invocation.getArgument(0);
            ho.setHandoverId(UUID.randomUUID());
            return ho;
        });

        ReturnScheduleResponse response = returnInspectionService.scheduleReturnAppointment(customerId, contractId, request);

        assertNotNull(response);
        assertEquals(contractId, response.contractId());
        assertEquals("SCHEDULED", response.status());
        assertNull(response.staffId());
        assertEquals(apptTime, response.scheduledAt());
        assertEquals("SafeBox Quận 7", response.facilityName());
        verify(handoverRecordRepository).save(any(HandoverRecord.class));
    }

    @Test
    void expiredRentalCanScheduleFutureReturnWithoutChangingContractEnd() {
        LocalDate originalEnd = LocalDate.now(VIETNAM_ZONE).minusDays(4);
        contract.setEndDate(originalEnd);
        contract.setStatus("OVERDUE");
        LocalDateTime appointment = LocalDate.now(VIETNAM_ZONE).plusDays(1).atTime(10, 0);
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(handoverRecordRepository.save(any(HandoverRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var response = returnInspectionService.scheduleReturnAppointment(customerId, contractId,
                new CreateReturnScheduleRequest(contractId, appointment, "", true, true));
        assertEquals("SCHEDULED", response.status());
        assertNull(response.staffId());
        assertEquals(originalEnd, contract.getEndDate());
        assertEquals("OCCUPIED", storageUnit.getStatus());
    }

    @Test
    void managerAssignsRegionEligibleStaffOnce() {
        contract.setStatus("OVERDUE");
        scheduledHandover.setStaffId(null);
        manager.setStatus("ACTIVE");
        facility.setRegionCode("Q7");
        StaffRegionAssignment region = new StaffRegionAssignment();
        region.setRegionCode("Q7");
        when(handoverRecordRepository.lockById(handoverId)).thenReturn(Optional.of(scheduledHandover));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userFacilityAssignmentRepository.isUserAssignedToFacility(eq(managerId), eq(facilityId), any(LocalDate.class))).thenReturn(true);
        when(userRepository.lockById(staffId)).thenReturn(Optional.of(staff));
        when(userFacilityAssignmentRepository.isUserAssignedToFacility(eq(staffId), eq(facilityId), any(LocalDate.class))).thenReturn(true);
        when(staffRegionAssignmentRepository.findByUserIdAndStatus(staffId, "ACTIVE")).thenReturn(List.of(region));
        when(pickupAppointmentRepository.findStaffOverlapping(eq(staffId), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());
        when(handoverRecordRepository.findScheduledReturnConflicts(eq(staffId), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());
        when(handoverRecordRepository.saveAndFlush(any(HandoverRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));

        ReturnScheduleResponse first = returnInspectionService.assignReturnStaff(managerId, handoverId, staffId);
        ReturnScheduleResponse repeat = returnInspectionService.assignReturnStaff(managerId, handoverId, staffId);

        assertEquals(staffId, first.staffId());
        assertEquals(staffId, repeat.staffId());
        verify(handoverRecordRepository, times(1)).saveAndFlush(scheduledHandover);
        verify(activityLogRepository, times(1)).save(any(ActivityLog.class));
    }

    @Test
    void crossFacilityManagerCannotAssignReturn() {
        manager.setStatus("ACTIVE");
        facility.setRegionCode("Q7");
        when(handoverRecordRepository.lockById(handoverId)).thenReturn(Optional.of(scheduledHandover));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userFacilityAssignmentRepository.isUserAssignedToFacility(eq(managerId), eq(facilityId), any(LocalDate.class))).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> returnInspectionService.assignReturnStaff(managerId, handoverId, staffId));
        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verify(handoverRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void managerCannotAssignStaffOutsideRegion() {
        scheduledHandover.setStaffId(null);
        manager.setStatus("ACTIVE");
        facility.setRegionCode("Q7");
        when(handoverRecordRepository.lockById(handoverId)).thenReturn(Optional.of(scheduledHandover));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userFacilityAssignmentRepository.isUserAssignedToFacility(eq(managerId), eq(facilityId), any(LocalDate.class))).thenReturn(true);
        when(userRepository.lockById(staffId)).thenReturn(Optional.of(staff));
        when(userFacilityAssignmentRepository.isUserAssignedToFacility(eq(staffId), eq(facilityId), any(LocalDate.class))).thenReturn(true);
        when(staffRegionAssignmentRepository.findByUserIdAndStatus(staffId, "ACTIVE")).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> returnInspectionService.assignReturnStaff(managerId, handoverId, staffId));
        assertEquals(HttpStatus.CONFLICT, error.status());
        verify(handoverRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("BR16.1: Khách hàng khác cố tình đặt lịch cho hợp đồng không phải của mình -> 403 Forbidden")
    void testScheduleReturnAppointment_ForbiddenForOtherCustomer() {
        LocalDateTime apptTime = LocalDateTime.of(contract.getEndDate(), LocalTime.of(10, 0));
        CreateReturnScheduleRequest request = new CreateReturnScheduleRequest(
                contractId, apptTime, null, true, true);

        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(userRepository.findById(otherCustomerId)).thenReturn(Optional.of(customer));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.scheduleReturnAppointment(otherCustomerId, contractId, request));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertTrue(ex.getMessage().contains("không có quyền"));
    }

    @ParameterizedTest
    @ValueSource(strings={"TERMINATION_PENDING","RETURNED","COMPLETED","CANCELLED"})
    void testScheduleReturnAppointment_NonActiveContract(String status) {
        contract.setStatus(status);
        LocalDateTime apptTime = LocalDateTime.of(contract.getEndDate(), LocalTime.of(10, 0));
        CreateReturnScheduleRequest request = new CreateReturnScheduleRequest(
                contractId, apptTime, null, true, true);

        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.scheduleReturnAppointment(customerId, contractId, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("đang thuê hoặc quá hạn"));
    }

    @Test
    @DisplayName("BR16.2: Đã có lịch trả kho SCHEDULED -> 409 Conflict")
    void testScheduleReturnAppointment_DuplicateScheduled() {
        LocalDateTime apptTime = LocalDateTime.of(contract.getEndDate(), LocalTime.of(10, 0));
        CreateReturnScheduleRequest request = new CreateReturnScheduleRequest(
                contractId, apptTime, null, true, true);

        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(handoverRecordRepository.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED"))
                .thenReturn(Optional.of(scheduledHandover));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.scheduleReturnAppointment(customerId, contractId, request));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("đã có một lịch trả kho"));
    }

    @Test
    @DisplayName("BR16.2: Thời gian hẹn trong quá khứ -> 400 Bad Request")
    void testScheduleReturnAppointment_PastTime() {
        LocalDateTime pastTime = LocalDateTime.now(VIETNAM_ZONE).minusHours(2);
        CreateReturnScheduleRequest request = new CreateReturnScheduleRequest(
                contractId, pastTime, null, true, true);

        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(handoverRecordRepository.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED"))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.scheduleReturnAppointment(customerId, contractId, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("quá khứ"));
    }

    @Test
    @DisplayName("Khách hàng đang thuê có thể đặt lịch sau ngày kết thúc hợp đồng")
    void activeRentalCanScheduleAfterContractEnd() {
        LocalDate wrongDate = contract.getEndDate().plusDays(1);
        LocalDateTime apptTime = LocalDateTime.of(wrongDate, LocalTime.of(10, 0));
        CreateReturnScheduleRequest request = new CreateReturnScheduleRequest(
                contractId, apptTime, null, true, true);

        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(handoverRecordRepository.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED"))
                .thenReturn(Optional.empty());

        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(handoverRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals(apptTime,returnInspectionService.scheduleReturnAppointment(customerId,contractId,request).scheduledAt());
        assertEquals("ACTIVE",contract.status); assertEquals("OCCUPIED",storageUnit.getStatus());
    }

    @Test void activeRentalCanScheduleEarlyWithoutChangingItsEndOrOccupancy() {
        LocalDate originalEnd=contract.endDate;
        LocalDateTime when=LocalDate.now(VIETNAM_ZONE).plusDays(1).atTime(10,0);
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(handoverRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var r=returnInspectionService.scheduleReturnAppointment(customerId,contractId,
                new CreateReturnScheduleRequest(contractId,when,"Chuyển nhà",true,true));
        assertEquals(when,r.scheduledAt()); assertEquals(originalEnd,r.contractEndDate());
        assertNull(r.actualAt()); assertEquals(originalEnd,contract.endDate);
        assertEquals("ACTIVE",contract.status);assertEquals("OCCUPIED",storageUnit.getStatus());
        verify(earlyReturns).prepareReturnSchedule(contract,when,"Chuyển nhà");
        verifyNoInteractions(invoiceRepository);
    }

    @Test
    @DisplayName("BR16.2: Giờ hẹn ngoài giờ mở cửa của cơ sở -> 400 Bad Request")
    void testScheduleReturnAppointment_OutsideFacilityOperatingHours() {
        LocalDateTime earlyTime = LocalDateTime.of(contract.getEndDate(), LocalTime.of(5, 30));
        CreateReturnScheduleRequest request = new CreateReturnScheduleRequest(
                contractId, earlyTime, null, true, true);

        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(handoverRecordRepository.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED"))
                .thenReturn(Optional.empty());
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.scheduleReturnAppointment(customerId, contractId, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("06:00–23:00"));
    }

    // =========================================================================
    // BR16.3 – BR16.10: Kiểm tra thực tế và hoàn tất nhận lại kho
    // =========================================================================

    @Test
    @DisplayName("BR16.4: Nhân viên không thuộc cơ sở cố tình lập biên bản -> 403 Forbidden")
    void testCompleteReturnInspection_ForbiddenForUnassignedStaff() {
        CompleteReturnInspectionRequest request = new CompleteReturnInspectionRequest();
        request.setHandoverId(handoverId);
        request.setLeftoverItems(false);
        request.setAccessReturned(true);
        request.setCustomerConfirmed(true);

        when(handoverRecordRepository.findById(handoverId)).thenReturn(Optional.of(scheduledHandover));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(staffId), any(LocalDate.class)))
                .thenReturn(Collections.emptyList()); // Staff not assigned to this facility

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.completeReturnHandoverAndInspection(staffId, request));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertTrue(ex.getMessage().contains("không được phân công"));
    }

    @Test
    @DisplayName("BR16.6: Kho chưa dọn trống (leftoverItems = true) -> 400 Bad Request")
    void testCompleteReturnInspection_FailsWhenLeftoverItemsExist() {
        CompleteReturnInspectionRequest request = new CompleteReturnInspectionRequest();
        request.setHandoverId(handoverId);
        request.setLeftoverItems(true); // Leftover items remain
        request.setAccessReturned(true);
        request.setCustomerConfirmed(true);

        when(handoverRecordRepository.findById(handoverId)).thenReturn(Optional.of(scheduledHandover));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(staffId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.completeReturnHandoverAndInspection(staffId, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("chưa được dọn trống"));
    }

    @Test
    @DisplayName("BR16.6: Khóa/thẻ chưa thu hồi (accessReturned = false) -> 400 Bad Request")
    void testCompleteReturnInspection_FailsWhenAccessNotReturned() {
        CompleteReturnInspectionRequest request = new CompleteReturnInspectionRequest();
        request.setHandoverId(handoverId);
        request.setLeftoverItems(false);
        request.setAccessReturned(false); // Key/card not returned
        request.setCustomerConfirmed(true);

        when(handoverRecordRepository.findById(handoverId)).thenReturn(Optional.of(scheduledHandover));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(staffId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.completeReturnHandoverAndInspection(staffId, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("thu hồi đầy đủ"));
    }

    @Test
    @DisplayName("BR16.3, BR16.6: Khách hàng chưa xác nhận (chưa tới/chưa đồng ý) -> 400 Bad Request")
    void testCompleteReturnInspection_FailsWhenCustomerNotConfirmed() {
        CompleteReturnInspectionRequest request = new CompleteReturnInspectionRequest();
        request.setHandoverId(handoverId);
        request.setLeftoverItems(false);
        request.setAccessReturned(true);
        request.setCustomerConfirmed(false); // Customer not present or didn't confirm

        when(handoverRecordRepository.findById(handoverId)).thenReturn(Optional.of(scheduledHandover));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(staffId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.completeReturnHandoverAndInspection(staffId, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("xác nhận"));
    }

    @ParameterizedTest
    @ValueSource(strings={"ACTIVE","OVERDUE"})
    void testCompleteReturnInspection_SuccessGoodCondition(String status) {
        contract.setStatus(status);
        scheduledHandover.setScheduledAt(LocalDateTime.now(VIETNAM_ZONE).minusMinutes(1));
        CompleteReturnInspectionRequest request = new CompleteReturnInspectionRequest();
        request.setHandoverId(handoverId);
        request.setLeftoverItems(false);
        request.setCleanlinessStatus("PASS");
        request.setAccessReturned(true);
        request.setAccessMethod("CARD_AND_KEY");
        request.setAccessIdentifier("Lock #LCK-99214, 2 Keys, NFC Card");
        request.setConditionStatus("GOOD");
        request.setEstimatedRepairCost(BigDecimal.ZERO);
        request.setCustomerConfirmed(true);
        request.setCustomerSignature("Nguyễn Văn An");
        request.setStaffSignature("Phạm Gia Huy");
        request.setNote("Kho sạch sẽ, bàn giao đầy đủ");

        when(handoverRecordRepository.findById(handoverId)).thenReturn(Optional.of(scheduledHandover));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(staffId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));
        when(handoverRecordRepository.save(any(HandoverRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(inspectionReportRepository.save(any(InspectionReport.class))).thenAnswer(i -> {
            InspectionReport r = i.getArgument(0);
            r.setInspectionId(UUID.randomUUID());
            return r;
        });
        when(storageUnitRepository.save(any(StorageUnit.class))).thenAnswer(i -> i.getArgument(0));
        when(rentalContractRepository.save(any(RentalContract.class))).thenAnswer(i -> i.getArgument(0));
        when(invoiceRepository.findByContractId(contractId)).thenReturn(Collections.emptyList());

        ReturnInspectionDetailResponse response =
                returnInspectionService.completeReturnHandoverAndInspection(staffId, request);

        assertNotNull(response);
        assertEquals("COMPLETED", response.handoverStatus());
        assertEquals("RETURNED", response.contractStatus());
        assertEquals("INACTIVE", response.unitStatus());
        assertFalse(storageUnit.isReadinessVerified());
        assertEquals("GOOD", response.conditionStatus());
        assertFalse(response.financialObligationsSettled()); // BR16.9: Not closed, deposit held
        assertNotNull(response.financialStatusNotes());

        verify(handoverRecordRepository).save(any(HandoverRecord.class));
        verify(inspectionReportRepository).save(any(InspectionReport.class));
        verify(storageUnitRepository).save(any(StorageUnit.class));
        verify(rentalContractRepository).save(any(RentalContract.class));
    }

    @Test
    @DisplayName("BR16.7: Khi kho bị hỏng (conditionStatus = DAMAGED, repairCost > 0) -> MAINTENANCE & RETURNED")
    void testCompleteReturnInspection_SuccessDamagedCondition() {
        scheduledHandover.setScheduledAt(LocalDateTime.now(VIETNAM_ZONE).minusMinutes(1));
        CompleteReturnInspectionRequest request = new CompleteReturnInspectionRequest();
        request.setHandoverId(handoverId);
        request.setLeftoverItems(false);
        request.setCleanlinessStatus("DIRTY");
        request.setAccessReturned(true);
        request.setConditionStatus("DAMAGED");
        request.setDamageDescription("Vết lõm sâu ở vách trái và thủng trần");
        request.setEstimatedRepairCost(new BigDecimal("650000.00"));
        request.setCustomerConfirmed(true);
        request.setUnitNextStatus("MAINTENANCE");

        when(handoverRecordRepository.findById(handoverId)).thenReturn(Optional.of(scheduledHandover));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(staffId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));
        when(handoverRecordRepository.save(any(HandoverRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(inspectionReportRepository.save(any(InspectionReport.class))).thenAnswer(i -> {
            InspectionReport r = i.getArgument(0);
            r.setInspectionId(UUID.randomUUID());
            return r;
        });
        when(storageUnitRepository.save(any(StorageUnit.class))).thenAnswer(i -> i.getArgument(0));
        when(rentalContractRepository.save(any(RentalContract.class))).thenAnswer(i -> i.getArgument(0));
        when(invoiceRepository.findByContractId(contractId)).thenReturn(Collections.emptyList());

        ReturnInspectionDetailResponse response =
                returnInspectionService.completeReturnHandoverAndInspection(staffId, request);

        assertNotNull(response);
        assertEquals("COMPLETED", response.handoverStatus());
        assertEquals("RETURNED", response.contractStatus());
        assertEquals("MAINTENANCE", response.unitStatus()); // BR16.7
        assertEquals("DAMAGED", response.conditionStatus());
        assertEquals(new BigDecimal("650000.00"), response.estimatedRepairCost());
    }

    @Test
    @DisplayName("BR16.8: Idempotency - Gọi lặp lại không tạo bản ghi mới")
    void testCompleteReturnInspection_Idempotent() {
        scheduledHandover.setStatus("COMPLETED");

        InspectionReport report = new InspectionReport();
        report.setInspectionId(UUID.randomUUID());
        report.setContractId(contractId);
        report.setConditionStatus("GOOD");

        CompleteReturnInspectionRequest request = new CompleteReturnInspectionRequest();
        request.setHandoverId(handoverId);

        when(handoverRecordRepository.findById(handoverId)).thenReturn(Optional.of(scheduledHandover));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(staffId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));
        when(inspectionReportRepository.findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(
                contractId, "RETURN_INSPECTION")).thenReturn(Optional.of(report));

        ReturnInspectionDetailResponse response =
                returnInspectionService.completeReturnHandoverAndInspection(staffId, request);

        assertNotNull(response);
        assertEquals("COMPLETED", response.handoverStatus());
        // Verify that save was NOT called again
        verify(inspectionReportRepository, never()).save(any(InspectionReport.class));
        verify(storageUnitRepository, never()).save(any(StorageUnit.class));
    }
}
