package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.contract.AssignableUnitResponse;
import com.safebox.self_storage.dto.contract.ConfirmContractRequest;
import com.safebox.self_storage.dto.contract.ContractResponse;
import com.safebox.self_storage.dto.contract.CreateContractRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.impl.ContractServiceImpl;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock private RentalContractRepository contracts;
    @Mock private ReservationRepository reservations;
    @Mock private StorageUnitRepository units;
    @Mock private StorageUnitTypeRepository unitTypes;
    @Mock private FacilityRepository facilities;
    @Mock private UserRepository users;
    @Mock private PricingPolicyRepository pricing;
    @Mock private UserFacilityAssignmentRepository assignments;

    @InjectMocks
    private ContractServiceImpl contractService;

    private UUID managerId;
    private UUID customerId;
    private UUID facilityId;
    private UUID reservationId;
    private UUID unitId;
    private Integer typeId;
    private UUID pricingId;

    private Facility facility;
    private Reservation reservation;
    private StorageUnit unit;
    private StorageUnitType unitType;
    private User customer;
    private User manager;

    @BeforeEach
    void setUp() {
        managerId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        facilityId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        typeId = 1;
        pricingId = UUID.randomUUID();

        facility = new Facility();
        facility.setFacilityId(facilityId);
        facility.setName("SafeBox Quận 7");
        facility.setAddress("123 Đào Trí, Q7, TP.HCM");
        facility.setPhone("0901234567");
        facility.setStatus("ACTIVE");

        customer = new User();
        customer.setUserId(customerId);
        customer.setFullName("Nguyễn Văn Khách");
        customer.setEmail("khach@safebox.test");
        customer.setPhone("0912345678");

        manager = new User();
        manager.setUserId(managerId);
        manager.setFullName("Trần Quản Lý");

        unitType = new StorageUnitType();
        unitType.setTypeId(typeId);
        unitType.setTypeName("Kho Mini Tiêu Chuẩn");
        unitType.setSizeName("5 m²");
        unitType.setWidth(BigDecimal.valueOf(2.0));
        unitType.setLength(BigDecimal.valueOf(2.5));
        unitType.setStorageMode("STANDARD");

        unit = new StorageUnit();
        unit.setUnitId(unitId);
        unit.setFacilityId(facilityId);
        unit.setTypeId(typeId);
        unit.setUnitNumber("A-101");
        unit.setFloor("Tầng 1");
        unit.setZone("Khu A");
        unit.setStatus("AVAILABLE");

        reservation = new Reservation();
        reservation.setReservationId(reservationId);
        reservation.setCustomerId(customerId);
        reservation.setFacilityId(facilityId);
        reservation.setTypeId(typeId);
        reservation.setPricingId(pricingId);
        reservation.setStartDate(LocalDate.now().plusDays(2));
        reservation.setEndDate(LocalDate.now().plusMonths(3).plusDays(2));
        reservation.setEstimatedRentalAmount(BigDecimal.valueOf(2700000));
        reservation.setEstimatedDepositAmount(BigDecimal.valueOf(900000));
        reservation.setStatus("CONFIRMED");
        reservation.setCreatedAt(LocalDateTime.now().minusDays(1));
    }

    // =========================================================================
    // UC09 Tests (FACILITY_MANAGER)
    // =========================================================================

    @Test
    @DisplayName("UC09 - BR09.1: Từ chối khi quản lý không phụ trách cơ sở của yêu cầu")
    void createContract_shouldThrowForbidden_whenManagerNotAssigned() {
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(assignments.findActiveFacilityIds(eq(managerId), any(LocalDate.class))).thenReturn(List.of(UUID.randomUUID()));

        CreateContractRequest req = new CreateContractRequest(unitId, "TERMS-2026-V1", null);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                contractService.createContract(managerId, reservationId, req));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertTrue(ex.getMessage().contains("không có quyền"));
    }

    @Test
    @DisplayName("UC09 - BR09.1: Từ chối khi yêu cầu chưa ở trạng thái CONFIRMED (ví dụ PENDING)")
    void createContract_shouldThrowBadRequest_whenReservationNotConfirmed() {
        reservation.setStatus("PENDING");
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(assignments.findActiveFacilityIds(eq(managerId), any(LocalDate.class))).thenReturn(List.of(facilityId));
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));

        CreateContractRequest req = new CreateContractRequest(unitId, "TERMS-2026-V1", null);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                contractService.createContract(managerId, reservationId, req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("CONFIRMED"));
    }

    @Test
    @DisplayName("UC09 - BR09.3: Từ chối tạo bản sao khi yêu cầu đã có hợp đồng chưa hủy")
    void createContract_shouldThrowConflict_whenContractAlreadyExists() {
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(assignments.findActiveFacilityIds(eq(managerId), any(LocalDate.class))).thenReturn(List.of(facilityId));
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));

        RentalContract existing = new RentalContract();
        existing.setContractNumber("HD-20261010-0001");
        existing.setUnitId(UUID.randomUUID()); // khác kho
        when(contracts.findActiveOrPendingContractByReservationId(reservationId)).thenReturn(Optional.of(existing));

        CreateContractRequest req = new CreateContractRequest(unitId, "TERMS-2026-V1", null);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                contractService.createContract(managerId, reservationId, req));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("đã có hợp đồng"));
    }

    @Test
    @DisplayName("UC09 - BR09.2: Từ chối khi kho vật lý gán có trạng thái không phải AVAILABLE (ví dụ OCCUPIED)")
    void createContract_shouldThrowBadRequest_whenUnitNotAvailable() {
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(assignments.findActiveFacilityIds(eq(managerId), any(LocalDate.class))).thenReturn(List.of(facilityId));
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));
        when(contracts.findActiveOrPendingContractByReservationId(reservationId)).thenReturn(Optional.empty());

        unit.setStatus("OCCUPIED");
        when(units.findById(unitId)).thenReturn(Optional.of(unit));

        CreateContractRequest req = new CreateContractRequest(unitId, "TERMS-2026-V1", null);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                contractService.createContract(managerId, reservationId, req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("Kho OCCUPIED, MAINTENANCE hoặc INACTIVE không được gán"));
    }

    @Test
    @DisplayName("UC09 - BR09.4: Từ chối khi ô kho có lịch thuê bị trùng lặp")
    void createContract_shouldThrowConflict_whenUnitScheduleOverlaps() {
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(assignments.findActiveFacilityIds(eq(managerId), any(LocalDate.class))).thenReturn(List.of(facilityId));
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));
        when(contracts.findActiveOrPendingContractByReservationId(reservationId)).thenReturn(Optional.empty());
        when(units.findById(unitId)).thenReturn(Optional.of(unit));

        // Giả lập đã có 1 hợp đồng khác trùng lịch
        when(contracts.countOverlappingContractsForUnit(eq(unitId), eq(reservation.getStartDate()), eq(reservation.getEndDate())))
                .thenReturn(1L);

        CreateContractRequest req = new CreateContractRequest(unitId, "TERMS-2026-V1", null);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                contractService.createContract(managerId, reservationId, req));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("trùng lặp"));
    }

    @Test
    @DisplayName("UC09 - BR09.5-BR09.8: Lập hợp đồng thành công, trạng thái PENDING_CONFIRMATION, kho giữ AVAILABLE")
    void createContract_shouldSucceed() {
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(assignments.findActiveFacilityIds(eq(managerId), any(LocalDate.class))).thenReturn(List.of(facilityId));
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));
        when(contracts.findActiveOrPendingContractByReservationId(reservationId)).thenReturn(Optional.empty());
        when(units.findById(unitId)).thenReturn(Optional.of(unit));
        when(contracts.countOverlappingContractsForUnit(eq(unitId), any(), any())).thenReturn(0L);
        when(contracts.existsByContractNumber(anyString())).thenReturn(false);

        when(contracts.save(any(RentalContract.class))).thenAnswer(invocation -> {
            RentalContract c = invocation.getArgument(0);
            c.setContractId(UUID.randomUUID());
            return c;
        });

        when(users.findById(customerId)).thenReturn(Optional.of(customer));
        when(users.findById(managerId)).thenReturn(Optional.of(manager));
        when(unitTypes.findById(typeId)).thenReturn(Optional.of(unitType));

        CreateContractRequest req = new CreateContractRequest(unitId, "TERMS-2026-V1", "Khách hàng VIP");
        ContractResponse res = contractService.createContract(managerId, reservationId, req);

        assertNotNull(res);
        assertNotNull(res.contractNumber());
        assertEquals("PENDING_CONFIRMATION", res.status());
        assertEquals("Chờ khách xác nhận", res.statusLabel());
        assertEquals(unitId, res.unitId());
        assertEquals("A-101", res.unitNumber());
        assertEquals(3, res.months());
        assertEquals(BigDecimal.valueOf(900000).setScale(2), res.monthlyRentalFee());
        assertEquals(BigDecimal.valueOf(2700000).setScale(2), res.totalRentalAmount());
        assertEquals(BigDecimal.valueOf(900000), res.depositAmount());
        assertEquals(BigDecimal.valueOf(3600000).setScale(2), res.totalPayable());

        // Kiểm tra kho vật lý vẫn giữ trạng thái AVAILABLE (BR09.8)
        assertEquals("AVAILABLE", unit.getStatus());
    }

    @Test
    @DisplayName("UC09 - BR09.9: Lấy danh sách ô kho khả dụng loại bỏ các kho bị trùng lịch")
    void getAssignableUnits_shouldFilterOutOverlappingUnits() {
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(assignments.findActiveFacilityIds(eq(managerId), any(LocalDate.class))).thenReturn(List.of(facilityId));
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));

        StorageUnit unit2 = new StorageUnit();
        UUID unit2Id = UUID.randomUUID();
        unit2.setUnitId(unit2Id);
        unit2.setUnitNumber("A-102");
        unit2.setFacilityId(facilityId);
        unit2.setTypeId(typeId);
        unit2.setStatus("AVAILABLE");

        when(units.findByFacilityIdAndTypeIdAndStatusOrderByUnitNumberAsc(facilityId, typeId, "AVAILABLE"))
                .thenReturn(List.of(unit, unit2));

        // Giả lập unit1 (A-101) bị trùng lịch, unit2 (A-102) không bị trùng
        when(contracts.findOverlappingUnitIds(any(), any(), any())).thenReturn(Set.of(unitId));

        List<AssignableUnitResponse> result = contractService.getAssignableUnits(managerId, reservationId);

        assertEquals(1, result.size());
        assertEquals("A-102", result.get(0).unitNumber());
    }

    // =========================================================================
    // UC10 Tests (CUSTOMER)
    // =========================================================================

    @Test
    @DisplayName("UC10 - BR10.1 & BR10.9: Từ chối khi khách hàng truy cập hợp đồng của người khác")
    void getMyContractDetail_shouldThrowForbidden_whenAccessingOtherCustomerContract() {
        UUID otherCustomerId = UUID.randomUUID();
        RentalContract contract = new RentalContract();
        UUID contractId = UUID.randomUUID();
        contract.setContractId(contractId);
        contract.setCustomerId(otherCustomerId); // hợp đồng của người khác

        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                contractService.getMyContractDetail(customerId, contractId));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
    }

    @Test
    @DisplayName("UC10 - BR10.3: Từ chối xác nhận hợp đồng khi khách hàng không tích đồng ý điều khoản")
    void confirmContract_shouldThrowBadRequest_whenTermsNotAgreed() {
        RentalContract contract = new RentalContract();
        UUID contractId = UUID.randomUUID();
        contract.setContractId(contractId);
        contract.setCustomerId(customerId);
        contract.setStatus("PENDING_CONFIRMATION");

        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));

        ConfirmContractRequest req = new ConfirmContractRequest(false, null);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                contractService.confirmContract(customerId, contractId, req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("điều khoản"));
    }

    @Test
    @DisplayName("UC10 - BR10.8: Từ chối xác nhận lại khi hợp đồng đã ở trạng thái PENDING_HANDOVER")
    void confirmContract_shouldThrowConflict_whenAlreadyConfirmed() {
        RentalContract contract = new RentalContract();
        UUID contractId = UUID.randomUUID();
        contract.setContractId(contractId);
        contract.setCustomerId(customerId);
        contract.setStatus("PENDING_HANDOVER"); // đã xác nhận rồi

        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));

        ConfirmContractRequest req = new ConfirmContractRequest(true, "Nguyễn Văn Khách");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                contractService.confirmContract(customerId, contractId, req));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("đã được xác nhận trước đó"));
    }

    @Test
    @DisplayName("UC10 - BR10.4 & BR10.5: Xác nhận hợp đồng thành công, chuyển sang PENDING_HANDOVER, lưu thời điểm và chữ ký")
    void confirmContract_shouldSucceed() {
        RentalContract contract = new RentalContract();
        UUID contractId = UUID.randomUUID();
        contract.setContractId(contractId);
        contract.setContractNumber("HD-20261010-0001");
        contract.setReservationId(reservationId);
        contract.setCustomerId(customerId);
        contract.setUnitId(unitId);
        contract.setStartDate(reservation.getStartDate());
        contract.setEndDate(reservation.getEndDate());
        contract.setMonthlyRentalFee(BigDecimal.valueOf(900000));
        contract.setDepositAmount(BigDecimal.valueOf(900000));
        contract.setBillingCycle("MONTHLY");
        contract.setTermsVersion("TERMS-2026-V1");
        contract.setStatus("PENDING_CONFIRMATION");
        contract.setCreatedAt(LocalDateTime.now().minusHours(2));

        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));
        when(users.findById(customerId)).thenReturn(Optional.of(customer));
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(units.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));
        when(unitTypes.findById(typeId)).thenReturn(Optional.of(unitType));

        when(contracts.save(any(RentalContract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmContractRequest req = new ConfirmContractRequest(true, "Nguyễn Văn Khách - Đồng ý thuê kho");
        ContractResponse res = contractService.confirmContract(customerId, contractId, req);

        assertNotNull(res);
        assertEquals("PENDING_HANDOVER", res.status());
        assertEquals("Chờ bàn giao", res.statusLabel());
        assertNotNull(res.customerSignedAt());
        assertEquals("Nguyễn Văn Khách - Đồng ý thuê kho", res.customerSignature());
    }

    @Test
    void approvedV2ContractCanBeSignedWithoutBreakingPaymentState() {
        RentalContract approved = new RentalContract();
        UUID id = UUID.randomUUID();
        approved.contractId = id; approved.customerId = customerId;
        approved.startDate = LocalDate.of(2026, 11, 1);
        approved.endDate = LocalDate.of(2027, 2, 1);
        approved.monthlyRentalFee = new BigDecimal("900000");
        approved.depositAmount = new BigDecimal("990000");
        approved.termsVersion = "v2"; approved.status = "CONFIRMED";
        when(contracts.findById(id)).thenReturn(Optional.of(approved));
        when(users.findById(customerId)).thenReturn(Optional.of(customer));
        when(contracts.save(approved)).thenReturn(approved);

        ContractResponse response = contractService.confirmContract(customerId, id,
                new ConfirmContractRequest(true, "Customer signature"));

        assertEquals("CONFIRMED", response.status());
        assertEquals("Customer signature", response.customerSignature());
        assertEquals(new BigDecimal("2970000"), response.totalPayable());
    }
}
