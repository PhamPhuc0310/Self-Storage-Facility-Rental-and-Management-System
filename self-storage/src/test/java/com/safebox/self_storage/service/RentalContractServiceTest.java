package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.response.RentalContractDetailResponse;
import com.safebox.self_storage.dto.response.RentalContractListItemResponse;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.impl.RentalContractServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RentalContractServiceTest {

    @Mock private RentalContractRepository rentalContractRepository;
    @Mock private StorageUnitRepository storageUnitRepository;
    @Mock private StorageUnitTypeRepository storageUnitTypeRepository;
    @Mock private FacilityRepository facilityRepository;
    @Mock private UserRepository userRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private HandoverRecordRepository handoverRecordRepository;
    @Mock private InspectionReportRepository inspectionReportRepository;
    @Mock private UserFacilityAssignmentRepository userFacilityAssignmentRepository;

    @InjectMocks
    private RentalContractServiceImpl rentalContractService;

    private UUID customerId;
    private UUID otherCustomerId;
    private UUID contractId;
    private UUID unitId;
    private UUID facilityId;
    private RentalContract contract;
    private StorageUnit storageUnit;
    private Facility facility;
    private StorageUnitType unitType;
    private User customer;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        contractId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        facilityId = UUID.randomUUID();

        customer = new User();
        customer.setUserId(customerId);
        customer.setFullName("Nguyễn Văn An");
        customer.setEmail("an.nguyen@test.com");
        customer.setPhone("0901234567");

        facility = new Facility();
        facility.setFacilityId(facilityId);
        facility.setName("SafeBox Quận 7");
        facility.setAddress("123 Nguyễn Thị Thập, Tân Phú, Q7, TP.HCM");
        facility.setPhone("19008868");

        unitType = new StorageUnitType();
        unitType.setTypeId(1);
        unitType.setTypeName("Kho Mini 5m²");
        unitType.setLength(new BigDecimal("2.5"));
        unitType.setWidth(new BigDecimal("2.0"));
        unitType.setHeight(new BigDecimal("2.8"));
        unitType.setStorageMode("Tự quản");
        unitType.setSizeName("5m²");

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
        contract.setStartDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(10));
        contract.setEndDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(20));
        contract.setMonthlyRentalFee(new BigDecimal("1500000.00"));
        contract.setDepositAmount(new BigDecimal("1500000.00"));
        contract.setBillingCycle("MONTHLY");
        contract.setTermsVersion("v1");
        contract.setStatus("ACTIVE");
        contract.setCreatedAt(LocalDateTime.now().minusDays(10));
    }

    @Test
    @DisplayName("UC15 - getCustomerRentals trả về danh sách phân trang thành công cho khách hàng")
    void testGetCustomerRentals_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(rentalContractRepository.findCustomerContractsWithFilter(eq(customerId), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(contract), pageable, 1));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(1)).thenReturn(Optional.of(unitType));
        when(invoiceRepository.findByContractId(contractId)).thenReturn(Collections.emptyList());
        when(paymentTransactionRepository.findByContractIdAndStatus(contractId, "SUCCESS")).thenReturn(Collections.emptyList());

        Page<RentalContractListItemResponse> page = rentalContractService.getCustomerRentals(customerId, "ALL", null, pageable);

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        RentalContractListItemResponse item = page.getContent().get(0);
        assertEquals(contractId, item.contractId());
        assertEquals("SB-CT-2026-TEST01", item.contractNumber());
        assertEquals("Q7-M101", item.unitNumber());
        assertEquals("SafeBox Quận 7", item.facilityName());
        assertEquals("Kho Mini 5m²", item.typeName());
        assertEquals("ACTIVE", item.status());
        assertTrue(item.canScheduleReturn(), "Hợp đồng ACTIVE phải cho phép chuyển sang đặt lịch trả kho UC16 (BR15.10)");
        assertFalse(item.isOverdue(), "Hợp đồng chưa hết hạn không được báo quá hạn");
        assertEquals(20, item.daysRemaining(), "Số ngày còn lại phải là 20 ngày (BR15.6)");
    }

    @Test
    @DisplayName("UC15 - BR15.4: Trả danh sách rỗng khi khách chưa có kho thuê, không báo lỗi")
    void testGetCustomerRentals_EmptyList() {
        Pageable pageable = PageRequest.of(0, 10);
        when(rentalContractRepository.findCustomerContractsWithFilter(eq(customerId), any(), isNull(), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        Page<RentalContractListItemResponse> page = rentalContractService.getCustomerRentals(customerId, "ACTIVE", null, pageable);

        assertNotNull(page);
        assertTrue(page.isEmpty());
        assertEquals(0, page.getTotalElements());
    }

    @Test
    @DisplayName("UC15 - BR15.6: Tính chính xác quá hạn khi ngày kết thúc nhỏ hơn ngày hiện tại")
    void testGetCustomerRentals_Overdue() {
        contract.setEndDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(5));
        Pageable pageable = PageRequest.of(0, 10);

        when(rentalContractRepository.findCustomerContractsWithFilter(eq(customerId), any(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(contract), pageable, 1));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(1)).thenReturn(Optional.of(unitType));
        when(invoiceRepository.findByContractId(contractId)).thenReturn(Collections.emptyList());
        when(paymentTransactionRepository.findByContractIdAndStatus(contractId, "SUCCESS")).thenReturn(Collections.emptyList());

        Page<RentalContractListItemResponse> page = rentalContractService.getCustomerRentals(customerId, "ACTIVE", null, pageable);
        RentalContractListItemResponse item = page.getContent().get(0);

        assertTrue(item.isOverdue(), "Phải đánh dấu quá hạn khi ngày kết thúc đã qua");
        assertEquals(5, item.overdueDays(), "Số ngày quá hạn phải là 5 ngày");
        assertEquals(0, item.daysRemaining(), "Số ngày còn lại phải là 0 khi đã quá hạn");
    }

    @Test
    @DisplayName("UC15 - BR15.8: Tình trạng thanh toán tính từ giao dịch SUCCESS")
    void testGetCustomerRentals_PaymentStatusCalculation() {
        Invoice inv = new Invoice();
        inv.setInvoiceId(UUID.randomUUID());
        inv.setContractId(contractId);
        inv.setTotalAmount(new BigDecimal("3000000.00"));
        inv.setPaidAmount(new BigDecimal("3000000.00"));
        inv.setStatus("PAID");

        PaymentTransaction tx = new PaymentTransaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setContractId(contractId);
        tx.setAmount(new BigDecimal("3000000.00"));
        tx.setStatus("SUCCESS");

        Pageable pageable = PageRequest.of(0, 10);
        when(rentalContractRepository.findCustomerContractsWithFilter(eq(customerId), any(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(contract), pageable, 1));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(1)).thenReturn(Optional.of(unitType));
        when(invoiceRepository.findByContractId(contractId)).thenReturn(List.of(inv));
        when(paymentTransactionRepository.findByContractIdAndStatus(contractId, "SUCCESS")).thenReturn(List.of(tx));

        Page<RentalContractListItemResponse> page = rentalContractService.getCustomerRentals(customerId, "ACTIVE", null, pageable);
        RentalContractListItemResponse item = page.getContent().get(0);

        assertEquals("PAID", item.paymentStatus(), "Tình trạng thanh toán phải là PAID khi đã thu đủ");
        assertEquals(0, item.paidAmount().compareTo(new BigDecimal("3000000.00")));
        assertEquals(0, item.remainingAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("UC15 - BR15.9: Chặn truy cập (403 Forbidden) khi khách xem hợp đồng của người khác")
    void testGetCustomerRentalDetail_ForbiddenAccess() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                rentalContractService.getCustomerRentalDetail(otherCustomerId, contractId));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertTrue(ex.getMessage().contains("không có quyền xem"));
    }

    @Test
    @DisplayName("UC15 - getCustomerRentalDetail trả về 404 Not Found khi hợp đồng không tồn tại")
    void testGetCustomerRentalDetail_NotFound() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                rentalContractService.getCustomerRentalDetail(customerId, contractId));

        assertEquals(HttpStatus.NOT_FOUND, ex.status());
    }

    @Test
    @DisplayName("UC15 - BR15.5: Xem chi tiết hợp đồng đầy đủ hóa đơn và biên bản bàn giao")
    void testGetCustomerRentalDetail_Success() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(1)).thenReturn(Optional.of(unitType));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));

        Invoice inv = new Invoice();
        inv.setInvoiceId(UUID.randomUUID());
        inv.setInvoiceNumber("INV-001");
        inv.setContractId(contractId);
        inv.setInvoiceType("INITIAL_RENT");
        inv.setTotalAmount(new BigDecimal("1500000.00"));
        inv.setPaidAmount(new BigDecimal("1500000.00"));
        inv.setStatus("PAID");
        when(invoiceRepository.findByContractId(contractId)).thenReturn(List.of(inv));

        HandoverRecord handover = new HandoverRecord();
        handover.setHandoverId(UUID.randomUUID());
        handover.setContractId(contractId);
        handover.setUnitId(unitId);
        handover.setStaffId(UUID.randomUUID());
        handover.setHandoverType("CHECK_IN");
        handover.setScheduledAt(LocalDateTime.now().minusDays(10));
        handover.setStatus("COMPLETED");
        handover.setAccessMethod("PIN_CODE");
        handover.setAccessIdentifier("123456");
        when(handoverRecordRepository.findByContractIdOrderByScheduledAtDesc(contractId)).thenReturn(List.of(handover));

        when(paymentTransactionRepository.findByContractId(contractId)).thenReturn(Collections.emptyList());
        when(inspectionReportRepository.findByContractIdOrderByInspectionDateDesc(contractId)).thenReturn(Collections.emptyList());

        RentalContractDetailResponse detail = rentalContractService.getCustomerRentalDetail(customerId, contractId);

        assertNotNull(detail);
        assertEquals(contractId, detail.contract().contractId());
        assertEquals("Nguyễn Văn An", detail.customerName());
        assertEquals(1, detail.invoices().size());
        assertEquals("INV-001", detail.invoices().get(0).invoiceNumber());
        assertEquals(1, detail.handoverRecords().size());
        assertEquals("COMPLETED", detail.handoverRecords().get(0).status());
    }

    @Test
    @DisplayName("UC15 - BR15.1: Quản lý cơ sở xem danh sách hợp đồng tại cơ sở được phân công")
    void testGetFacilityRentals_Success() {
        UUID managerId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));
        when(rentalContractRepository.findFacilityContractsWithFilter(eq(facilityId), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(contract), pageable, 1));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(1)).thenReturn(Optional.of(unitType));
        when(invoiceRepository.findByContractId(contractId)).thenReturn(Collections.emptyList());
        when(paymentTransactionRepository.findByContractIdAndStatus(contractId, "SUCCESS")).thenReturn(Collections.emptyList());

        Page<RentalContractListItemResponse> page = rentalContractService.getFacilityRentals(managerId, facilityId, "ALL", null, pageable);

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
    }

    @Test
    @DisplayName("UC15 - BR15.1: Quản lý cơ sở bị chặn khi xem cơ sở không được phân công")
    void testGetFacilityRentals_ForbiddenFacility() {
        UUID managerId = UUID.randomUUID();
        UUID unassignedFacilityId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                rentalContractService.getFacilityRentals(managerId, unassignedFacilityId, "ALL", null, pageable));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
    }
}
