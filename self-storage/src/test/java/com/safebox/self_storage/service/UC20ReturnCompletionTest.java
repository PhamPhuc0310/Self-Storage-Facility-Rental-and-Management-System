package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.CompleteContractRequest;
import com.safebox.self_storage.dto.response.ContractCompletionResponse;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.impl.ReturnInspectionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử nghiệp vụ Use Case UC20:
 * "Hoàn tất trả kho, kiểm tra và đóng hợp đồng" (BR20.1 - BR20.5).
 */
@ExtendWith(MockitoExtension.class)
class UC20ReturnCompletionTest {

    @Mock
    private RentalContractRepository rentalContractRepository;
    @Mock
    private StorageUnitRepository storageUnitRepository;
    @Mock
    private FacilityRepository facilityRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HandoverRecordRepository handoverRecordRepository;
    @Mock
    private InspectionReportRepository inspectionReportRepository;
    @Mock
    private UserFacilityAssignmentRepository userFacilityAssignmentRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private DepositRefundRepository depositRefundRepository;
    @Mock
    private ActivityLogRepository activityLogRepository;

    @InjectMocks
    private ReturnInspectionServiceImpl returnInspectionService;

    private UUID contractId;
    private UUID customerId;
    private UUID managerId;
    private UUID staffId;
    private UUID unitId;
    private UUID facilityId;

    private RentalContract contract;
    private StorageUnit unit;
    private Facility facility;
    private User managerUser;
    private User staffUser;
    private User customerUser;
    private Role managerRole;
    private Role staffRole;
    private Role customerRole;

    @BeforeEach
    void setUp() {
        contractId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        facilityId = UUID.randomUUID();

        managerRole = new Role();
        managerRole.setRoleId(2);
        managerRole.setRoleName("FACILITY_MANAGER");

        staffRole = new Role();
        staffRole.setRoleId(3);
        staffRole.setRoleName("FACILITY_STAFF");

        customerRole = new Role();
        customerRole.setRoleId(4);
        customerRole.setRoleName("CUSTOMER");

        managerUser = new User();
        managerUser.setUserId(managerId);
        managerUser.setFullName("Trần Quản Lý");
        managerUser.setRole(managerRole);
        managerUser.setStatus("ACTIVE");

        staffUser = new User();
        staffUser.setUserId(staffId);
        staffUser.setFullName("Lê Nhân Viên");
        staffUser.setRole(staffRole);
        staffUser.setStatus("ACTIVE");

        customerUser = new User();
        customerUser.setUserId(customerId);
        customerUser.setFullName("Nguyễn Khách Hàng");
        customerUser.setRole(customerRole);
        customerUser.setStatus("ACTIVE");

        facility = new Facility();
        facility.setFacilityId(facilityId);
        facility.setName("SafeBox Quận 7");

        unit = new StorageUnit();
        unit.setUnitId(unitId);
        unit.setFacilityId(facilityId);
        unit.setUnitNumber("Q7-U101");
        unit.setStatus("OCCUPIED");

        contract = new RentalContract();
        contract.setContractId(contractId);
        contract.setContractNumber("SB-CT-2026-001");
        contract.setCustomerId(customerId);
        contract.setUnitId(unitId);
        contract.setStatus("RETURNED"); // Đã qua bước kiểm tra nhận lại kho
        contract.setDepositAmount(new BigDecimal("2000000"));
        contract.setStartDate(LocalDate.now().minusMonths(1));
        contract.setEndDate(LocalDate.now());
    }

    @Test
    @DisplayName("BR20.1 & BR20.2: Chặn tài khoản CUSTOMER không được gọi đóng hợp đồng -> 403 Forbidden")
    void testCompleteContract_CustomerForbidden() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customerUser));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.completeContractAndFinalizeUnit(customerId, contractId, new CompleteContractRequest()));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertTrue(ex.getMessage().contains("Chỉ nhân viên hoặc quản lý cơ sở"));
    }

    @Test
    @DisplayName("BR20.2: Chặn nhân viên/quản lý không thuộc cơ sở của kho -> 403 Forbidden")
    void testCompleteContract_StaffWrongFacility() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(staffId), any(LocalDate.class)))
                .thenReturn(List.of(UUID.randomUUID()));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.completeContractAndFinalizeUnit(staffId, contractId, new CompleteContractRequest()));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertTrue(ex.getMessage().contains("không được phân công làm việc tại cơ sở"));
    }

    @Test
    @DisplayName("BR20.4: Chặn đóng hợp đồng khi hợp đồng chưa hoàn tất trả kho (đang ACTIVE) -> 409 Conflict")
    void testCompleteContract_NotReturnedYet() {
        contract.setStatus("ACTIVE");
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(managerUser));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));
        when(inspectionReportRepository.findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(contractId, "RETURN_INSPECTION"))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.completeContractAndFinalizeUnit(managerId, contractId, new CompleteContractRequest()));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("chưa hoàn tất bước kiểm tra nhận lại kho"));
    }

    @Test
    @DisplayName("BR20.4: Chặn đóng hợp đồng khi còn hóa đơn chưa thanh toán -> 409 Conflict")
    void testCompleteContract_UnpaidInvoicesRemain() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(managerUser));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));

        InspectionReport report = new InspectionReport();
        report.setConditionStatus("GOOD");
        when(inspectionReportRepository.findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(contractId, "RETURN_INSPECTION"))
                .thenReturn(Optional.of(report));

        Invoice unpaidInvoice = new Invoice();
        unpaidInvoice.setStatus("UNPAID");
        when(invoiceRepository.findByContractId(contractId)).thenReturn(List.of(unpaidInvoice));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.completeContractAndFinalizeUnit(managerId, contractId, new CompleteContractRequest()));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("chưa thanh toán tất toán"));
    }

    @Test
    @DisplayName("BR20.4: Chặn đóng hợp đồng khi có yêu cầu hoàn cọc đang trong quá trình xét duyệt -> 409 Conflict")
    void testCompleteContract_PendingRefundExists() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(managerUser));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));

        InspectionReport report = new InspectionReport();
        report.setConditionStatus("GOOD");
        when(inspectionReportRepository.findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(contractId, "RETURN_INSPECTION"))
                .thenReturn(Optional.of(report));

        when(invoiceRepository.findByContractId(contractId)).thenReturn(List.of());
        when(depositRefundRepository.existsByContractIdAndStatusIn(eq(contractId), any())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                returnInspectionService.completeContractAndFinalizeUnit(managerId, contractId, new CompleteContractRequest()));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("hoàn cọc đang chờ duyệt hoặc chi trả"));
    }

    @Test
    @DisplayName("BR20.3 & BR20.4: Hoàn tất đóng hợp đồng thành công khi kho tốt -> unit AVAILABLE, contract COMPLETED, audit log được ghi")
    void testCompleteContract_Success_UnitAvailable() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(managerUser));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customerUser));

        InspectionReport report = new InspectionReport();
        report.setConditionStatus("GOOD");
        report.setEstimatedRepairCost(BigDecimal.ZERO);
        when(inspectionReportRepository.findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(contractId, "RETURN_INSPECTION"))
                .thenReturn(Optional.of(report));

        when(invoiceRepository.findByContractId(contractId)).thenReturn(List.of());
        when(depositRefundRepository.existsByContractIdAndStatusIn(eq(contractId), any())).thenReturn(false);
        when(depositRefundRepository.findByContractIdOrderByCreatedAtDesc(contractId)).thenReturn(List.of());

        CompleteContractRequest req = new CompleteContractRequest("Đã nghiệm thu và tất toán xong.", "AVAILABLE");
        ContractCompletionResponse res = returnInspectionService.completeContractAndFinalizeUnit(managerId, contractId, req);

        assertNotNull(res);
        assertEquals("COMPLETED", res.contractStatus());
        assertEquals("AVAILABLE", res.unitStatus());
        assertEquals(contractId, res.contractId());

        verify(storageUnitRepository).save(argThat(u -> "AVAILABLE".equals(u.getStatus())));
        verify(rentalContractRepository).save(argThat(c -> "COMPLETED".equals(c.getStatus()) && c.getActualEndDate() != null));
        verify(activityLogRepository).save(any(ActivityLog.class));
    }

    @Test
    @DisplayName("BR20.3: Nếu kho bị hư hại (DAMAGED / chi phí sửa chữa > 0) -> unit chuyển sang MAINTENANCE")
    void testCompleteContract_Success_UnitDamagedGoesToMaintenance() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(managerUser));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customerUser));

        InspectionReport report = new InspectionReport();
        report.setConditionStatus("DAMAGED");
        report.setDamageDescription("Ổ khóa bị vỡ, tường kho bị trầy xước sâu");
        report.setEstimatedRepairCost(new BigDecimal("500000"));
        when(inspectionReportRepository.findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(contractId, "RETURN_INSPECTION"))
                .thenReturn(Optional.of(report));

        when(invoiceRepository.findByContractId(contractId)).thenReturn(List.of());
        when(depositRefundRepository.existsByContractIdAndStatusIn(eq(contractId), any())).thenReturn(false);
        when(depositRefundRepository.findByContractIdOrderByCreatedAtDesc(contractId)).thenReturn(List.of());

        ContractCompletionResponse res = returnInspectionService.completeContractAndFinalizeUnit(
                managerId, contractId, new CompleteContractRequest("Kho cần sửa chữa trước khi cho thuê lại.", null));

        assertEquals("COMPLETED", res.contractStatus());
        assertEquals("MAINTENANCE", res.unitStatus());
        verify(storageUnitRepository).save(argThat(u -> "MAINTENANCE".equals(u.getStatus())));
    }

    @Test
    @DisplayName("BR20.4: Idempotency (Retry) - Gọi lại khi hợp đồng đã COMPLETED -> trả kết quả thành công không bị conflict")
    void testCompleteContract_IdempotentRetry() {
        contract.setStatus("COMPLETED");
        unit.setStatus("AVAILABLE");

        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(managerUser));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customerUser));

        ContractCompletionResponse res = returnInspectionService.completeContractAndFinalizeUnit(
                managerId, contractId, new CompleteContractRequest());

        assertNotNull(res);
        assertEquals("COMPLETED", res.contractStatus());
        assertTrue(res.message().contains("trước đó"));
        verify(rentalContractRepository, never()).save(any());
    }
}
