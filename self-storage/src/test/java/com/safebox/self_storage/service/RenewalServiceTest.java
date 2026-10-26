package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.renewal.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.impl.RenewalServiceImpl;
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
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RenewalServiceTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock private RenewalRepository renewalRepository;
    @Mock private RentalContractRepository rentalContractRepository;
    @Mock private StorageUnitRepository storageUnitRepository;
    @Mock private FacilityRepository facilityRepository;
    @Mock private UserRepository userRepository;
    @Mock private PricingPolicyRepository pricingPolicyRepository;
    @Mock private RentalPolicyRepository rentalPolicyRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private HandoverRecordRepository handoverRecordRepository;
    @Mock private UserFacilityAssignmentRepository userFacilityAssignmentRepository;

    @InjectMocks
    private RenewalServiceImpl renewalService;

    private UUID customerId;
    private UUID otherCustomerId;
    private UUID managerId;
    private UUID contractId;
    private UUID unitId;
    private UUID facilityId;
    private UUID renewalId;

    private RentalContract contract;
    private StorageUnit storageUnit;
    private Facility facility;
    private User customerUser;
    private User managerUser;
    private PricingPolicy pricingPolicy;
    private RentalPolicy rentalPolicy;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        contractId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        facilityId = UUID.randomUUID();
        renewalId = UUID.randomUUID();

        contract = new RentalContract();
        contract.setContractId(contractId);
        contract.setContractNumber("HD-202610-0001");
        contract.setCustomerId(customerId);
        contract.setUnitId(unitId);
        contract.setStartDate(LocalDate.of(2026, 10, 1));
        contract.setEndDate(LocalDate.of(2026, 11, 1));
        contract.setMonthlyRentalFee(new BigDecimal("1000000"));
        contract.setDepositAmount(new BigDecimal("1000000"));
        contract.setStatus("ACTIVE");

        storageUnit = new StorageUnit();
        storageUnit.setUnitId(unitId);
        storageUnit.setFacilityId(facilityId);
        storageUnit.setTypeId(1);
        storageUnit.setUnitNumber("A-101");
        storageUnit.setStatus("OCCUPIED");

        facility = new Facility();
        facility.setFacilityId(facilityId);
        facility.setName("Kho SafeBox Tân Bình");
        facility.setStatus("ACTIVE");

        customerUser = new User();
        customerUser.setUserId(customerId);
        customerUser.setFullName("Nguyễn Văn Khách");

        Role managerRole = new Role();
        managerRole.setRoleId(2);
        managerRole.setRoleName("FACILITY_MANAGER");

        managerUser = new User();
        managerUser.setUserId(managerId);
        managerUser.setFullName("Trần Quản Lý");
        managerUser.setRole(managerRole);

        pricingPolicy = new PricingPolicy();
        pricingPolicy.setPricingId(UUID.randomUUID());
        pricingPolicy.setFacilityId(facilityId);
        pricingPolicy.setTypeId(1);
        pricingPolicy.setMonthlyPrice(new BigDecimal("1000000"));
        pricingPolicy.setStatus("ACTIVE");
        pricingPolicy.setEffectiveFrom(LocalDate.of(2026, 1, 1));

        rentalPolicy = new RentalPolicy();
        rentalPolicy.setPolicyId(UUID.randomUUID());
        rentalPolicy.setFacilityId(facilityId);
        rentalPolicy.setRenewalPaymentHours(24);
        rentalPolicy.setStatus("ACTIVE");
    }

    // =========================================================================
    // BR17.1 Tests: Chỉ CUSTOMER sở hữu hợp đồng ACTIVE mới yêu cầu gia hạn
    // =========================================================================

    @Test
    @DisplayName("BR17.1: Từ chối khi khách hàng yêu cầu gia hạn hợp đồng của người khác")
    void testRequestRenewal_ForbiddenForOtherCustomer() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));

        CreateRenewalRequest request = new CreateRenewalRequest(1);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                renewalService.requestRenewal(otherCustomerId, contractId, request));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertTrue(ex.getMessage().contains("không có quyền"));
    }

    @Test
    @DisplayName("BR17.1: Từ chối khi hợp đồng không ở trạng thái ACTIVE")
    void testRequestRenewal_RejectedWhenContractNotActive() {
        contract.setStatus("PENDING_HANDOVER");
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));

        CreateRenewalRequest request = new CreateRenewalRequest(1);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                renewalService.requestRenewal(customerId, contractId, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("ACTIVE"));
    }

    @Test
    @DisplayName("BR17.1 & BR17.5: Từ chối khi hợp đồng đã có yêu cầu gia hạn đang chờ xử lý")
    void testRequestRenewal_ConflictWhenAlreadyHasActiveRenewal() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));

        Renewal existingRenewal = new Renewal();
        existingRenewal.setStatus("PENDING");
        when(renewalRepository.findByContractIdAndStatusIn(eq(contractId), anyCollection()))
                .thenReturn(List.of(existingRenewal));

        CreateRenewalRequest request = new CreateRenewalRequest(1);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                renewalService.requestRenewal(customerId, contractId, request));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("đang được xử lý"));
    }

    @Test
    @DisplayName("BR17.1 & BR17.3: Từ chối yêu cầu gia hạn khi đang có lịch trả kho mở")
    void testRequestRenewal_ConflictWhenOpenReturnExists() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(renewalRepository.findByContractIdAndStatusIn(eq(contractId), anyCollection()))
                .thenReturn(Collections.emptyList());

        HandoverRecord returnRecord = new HandoverRecord();
        returnRecord.setStatus("SCHEDULED");
        when(handoverRecordRepository.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED"))
                .thenReturn(Optional.of(returnRecord));

        CreateRenewalRequest request = new CreateRenewalRequest(1);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                renewalService.requestRenewal(customerId, contractId, request));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("lịch hẹn trả kho"));
    }

    // =========================================================================
    // BR17.2 & BR17.4 Tests: Nối tiếp ngày kết thúc & Snapshot giá, VAT, ưu đãi
    // =========================================================================

    @Test
    @DisplayName("BR17.2 & BR17.4: Tạo yêu cầu gia hạn thành công, ngày kết thúc mới nối tiếp kỳ cũ, chốt snapshot")
    void testRequestRenewal_Success_CreatesSnapshot() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(renewalRepository.findByContractIdAndStatusIn(eq(contractId), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(handoverRecordRepository.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED"))
                .thenReturn(Optional.empty());
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(pricingPolicyRepository.findByFacilityIdAndStatus(facilityId, "ACTIVE"))
                .thenReturn(List.of(pricingPolicy));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customerUser));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));

        when(renewalRepository.save(any(Renewal.class))).thenAnswer(invocation -> {
            Renewal r = invocation.getArgument(0);
            r.setRenewalId(renewalId);
            return r;
        });

        CreateRenewalRequest request = new CreateRenewalRequest(3, "Gia hạn thêm 3 tháng", new BigDecimal("50000"));

        RenewalResponse response = renewalService.requestRenewal(customerId, contractId, request);

        assertNotNull(response);
        assertEquals(renewalId, response.renewalId());
        // BR17.2: Ngày bắt đầu nối tiếp ngày kết thúc hiện tại: oldEndDate = 2026-11-01, newEndDate = 2027-02-01
        assertEquals(LocalDate.of(2026, 11, 1), response.oldEndDate());
        assertEquals(LocalDate.of(2027, 2, 1), response.newEndDate());
        assertEquals(3, response.extensionMonths());
        // BR17.2: Hợp đồng gốc chưa bị sửa ngày kết thúc khi chỉ mới tạo yêu cầu
        assertEquals(LocalDate.of(2026, 11, 1), contract.getEndDate());

        // BR17.4: Snapshot giá
        assertEquals(new BigDecimal("1000000"), response.monthlyPrice());
        // Subtotal = 1,000,000 * 3 = 3,000,000
        assertEquals(new BigDecimal("3000000"), response.subtotal());
        // VAT = 300,000
        assertEquals(new BigDecimal("300000"), response.vatAmount());
        // Discount = 50,000
        assertEquals(new BigDecimal("50000"), response.discountAmount());
        // Renewal amount = 3,000,000 + 300,000 - 50,000 = 3,250,000
        assertEquals(new BigDecimal("3250000"), response.renewalAmount());
        assertEquals("PENDING", response.status());
    }

    // =========================================================================
    // BR17.3 Tests: Xét duyệt gia hạn và kiểm tra khả dụng / yêu cầu trả kho
    // =========================================================================

    @Test
    @DisplayName("BR17.3: Quản lý không phụ trách cơ sở bị từ chối xét duyệt (403 Forbidden)")
    void testReviewRenewal_ForbiddenForUnauthorizedManager() {
        Renewal renewal = new Renewal();
        renewal.setRenewalId(renewalId);
        renewal.setContractId(contractId);
        renewal.setStatus("PENDING");

        when(renewalRepository.findById(renewalId)).thenReturn(Optional.of(renewal));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(UUID.randomUUID())); // Different facility

        ReviewRenewalRequest request = new ReviewRenewalRequest("APPROVE", null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                renewalService.reviewRenewal(managerId, renewalId, request));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
    }

    @Test
    @DisplayName("BR17.3: Từ chối duyệt gia hạn khi ô kho đang bảo trì")
    void testReviewRenewal_ConflictWhenUnitUnderMaintenance() {
        Renewal renewal = new Renewal();
        renewal.setRenewalId(renewalId);
        renewal.setContractId(contractId);
        renewal.setStatus("PENDING");

        storageUnit.setStatus("MAINTENANCE");

        when(renewalRepository.findById(renewalId)).thenReturn(Optional.of(renewal));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));

        ReviewRenewalRequest request = new ReviewRenewalRequest("APPROVE", null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                renewalService.reviewRenewal(managerId, renewalId, request));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("bảo trì"));
    }

    @Test
    @DisplayName("BR17.3: Từ chối duyệt gia hạn khi có yêu cầu trả kho đang mở")
    void testReviewRenewal_ConflictWhenOpenReturnHandoverExists() {
        Renewal renewal = new Renewal();
        renewal.setRenewalId(renewalId);
        renewal.setContractId(contractId);
        renewal.setStatus("PENDING");

        when(renewalRepository.findById(renewalId)).thenReturn(Optional.of(renewal));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));

        HandoverRecord returnRecord = new HandoverRecord();
        returnRecord.setStatus("SCHEDULED");
        when(handoverRecordRepository.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED"))
                .thenReturn(Optional.of(returnRecord));

        ReviewRenewalRequest request = new ReviewRenewalRequest("APPROVE", null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                renewalService.reviewRenewal(managerId, renewalId, request));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("yêu cầu trả kho"));
    }

    @Test
    @DisplayName("BR17.3: Quản lý từ chối yêu cầu gia hạn kèm lý do")
    void testReviewRenewal_RejectWithReason() {
        Renewal renewal = new Renewal();
        renewal.setRenewalId(renewalId);
        renewal.setContractId(contractId);
        renewal.setStatus("PENDING");
        renewal.setOldEndDate(LocalDate.of(2026, 11, 1));
        renewal.setNewEndDate(LocalDate.of(2026, 12, 1));

        when(renewalRepository.findById(renewalId)).thenReturn(Optional.of(renewal));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));
        when(renewalRepository.save(any(Renewal.class))).thenAnswer(i -> i.getArgument(0));

        ReviewRenewalRequest request = new ReviewRenewalRequest("REJECT", "Kho cần nâng cấp hệ thống PCCC");

        RenewalResponse response = renewalService.reviewRenewal(managerId, renewalId, request);

        assertEquals("REJECTED", response.status());
        assertEquals("Kho cần nâng cấp hệ thống PCCC", response.rejectionReason());
        assertNotNull(response.reviewedAt());
    }

    @Test
    @DisplayName("BR17.3: Phê duyệt thành công, chuyển sang APPROVED và tạo hóa đơn RENEWAL")
    void testReviewRenewal_Approve_CreatesInvoice() {
        Renewal renewal = new Renewal();
        renewal.setRenewalId(renewalId);
        renewal.setContractId(contractId);
        renewal.setStatus("PENDING");
        renewal.setOldEndDate(LocalDate.of(2026, 11, 1));
        renewal.setNewEndDate(LocalDate.of(2026, 12, 1));
        renewal.setExtensionMonths(1);
        renewal.setMonthlyPrice(new BigDecimal("1000000"));
        renewal.setSubtotal(new BigDecimal("1000000"));
        renewal.setVatAmount(new BigDecimal("100000"));
        renewal.setDiscountAmount(BigDecimal.ZERO);
        renewal.setRenewalAmount(new BigDecimal("1100000"));

        when(renewalRepository.findById(renewalId)).thenReturn(Optional.of(renewal));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(userFacilityAssignmentRepository.findActiveFacilityIds(eq(managerId), any(LocalDate.class)))
                .thenReturn(List.of(facilityId));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(handoverRecordRepository.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED"))
                .thenReturn(Optional.empty());
        when(rentalPolicyRepository.findFirstByFacilityIdAndStatusOrderByEffectiveFromDesc(facilityId, "ACTIVE"))
                .thenReturn(Optional.of(rentalPolicy));
        when(renewalRepository.save(any(Renewal.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(managerUser));

        ReviewRenewalRequest request = new ReviewRenewalRequest("APPROVE", null);

        RenewalResponse response = renewalService.reviewRenewal(managerId, renewalId, request);

        assertEquals("APPROVED", response.status());
        assertNotNull(response.paymentDeadline());
        assertEquals(managerId, response.approvedBy());

        // Xác nhận hóa đơn khoản phải thu đã được tạo
        verify(invoiceRepository).save(argThat(inv ->
                "RENEWAL".equals(inv.getInvoiceType())
                        && renewalId.equals(inv.getRenewalId())
                        && "UNPAID".equals(inv.getStatus())
                        && new BigDecimal("1100000").compareTo(inv.getTotalAmount()) == 0
        ));
    }

    // =========================================================================
    // BR17.5 Tests: Chỉ gia hạn hiệu lực sau khi thanh toán; retry idempotency
    // =========================================================================

    @Test
    @DisplayName("BR17.5: Thanh toán phí gia hạn thành công, cập nhật ngày kết thúc mới cho hợp đồng")
    void testPayRenewal_Success_ExtendsContractEndDate() {
        Renewal renewal = new Renewal();
        renewal.setRenewalId(renewalId);
        renewal.setContractId(contractId);
        renewal.setStatus("APPROVED");
        renewal.setOldEndDate(LocalDate.of(2026, 11, 1));
        renewal.setNewEndDate(LocalDate.of(2026, 12, 1));
        renewal.setRenewalAmount(new BigDecimal("1100000"));
        renewal.setPaymentDeadline(LocalDateTime.now(VIETNAM_ZONE).plusHours(24));

        Invoice invoice = new Invoice();
        invoice.setInvoiceId(UUID.randomUUID());
        invoice.setRenewalId(renewalId);
        invoice.setContractId(contractId);
        invoice.setTotalAmount(new BigDecimal("1100000"));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setStatus("UNPAID");

        when(paymentTransactionRepository.findByCreatedByAndIdempotencyKey(eq(customerId), eq("IDEM-001")))
                .thenReturn(Optional.empty());
        when(renewalRepository.findById(renewalId)).thenReturn(Optional.of(renewal));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(invoiceRepository.findFirstByRenewalId(renewalId)).thenReturn(Optional.of(invoice));

        when(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class))).thenAnswer(i -> {
            PaymentTransaction tx = i.getArgument(0);
            tx.setTransactionId(UUID.randomUUID());
            return tx;
        });
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        PayRenewalRequest payRequest = new PayRenewalRequest("BANK_TRANSFER", new BigDecimal("1100000"), "IDEM-001");

        RenewalPaymentResponse paymentResponse = renewalService.payRenewal(customerId, renewalId, payRequest);

        assertNotNull(paymentResponse);
        assertEquals("SUCCESS", paymentResponse.status());
        assertEquals(LocalDate.of(2026, 12, 1), paymentResponse.contractNewEndDate());

        // BR17.5: Kiểm tra trạng thái hợp đồng và ngày kết thúc mới đã chính thức gia hạn
        assertEquals(LocalDate.of(2026, 12, 1), contract.getEndDate());
        verify(rentalContractRepository).save(contract);

        // Kiểm tra hóa đơn chuyển sang PAID
        assertEquals("PAID", invoice.getStatus());
        assertEquals(new BigDecimal("1100000"), invoice.getPaidAmount());
        verify(invoiceRepository).save(invoice);

        // Kiểm tra yêu cầu gia hạn chuyển sang COMPLETED
        assertEquals("COMPLETED", renewal.getStatus());
        verify(renewalRepository).save(renewal);
    }

    @Test
    @DisplayName("BR17.5: Retry với cùng Idempotency Key không tạo giao dịch trùng hay gia hạn 2 lần")
    void testPayRenewal_IdempotentRetry_DoesNotReChargeOrDoubleExtend() {
        PaymentTransaction existingTx = new PaymentTransaction();
        existingTx.setTransactionId(UUID.randomUUID());
        existingTx.setContractId(contractId);
        existingTx.setAmount(new BigDecimal("1100000"));
        existingTx.setPaymentMethod("BANK_TRANSFER");
        existingTx.setStatus("SUCCESS");
        existingTx.setReferenceCode("PAY-RNW-PREVIOUS");
        existingTx.setTransactionDate(LocalDateTime.now(VIETNAM_ZONE));

        when(paymentTransactionRepository.findByCreatedByAndIdempotencyKey(customerId, "IDEM-SAME"))
                .thenReturn(Optional.of(existingTx));

        Renewal renewal = new Renewal();
        renewal.setRenewalId(renewalId);
        renewal.setContractId(contractId);
        renewal.setStatus("COMPLETED");
        renewal.setNewEndDate(LocalDate.of(2026, 12, 1));
        when(renewalRepository.findById(renewalId)).thenReturn(Optional.of(renewal));

        // Hợp đồng đã có ngày kết thúc sau lần thanh toán đầu
        contract.setEndDate(LocalDate.of(2026, 12, 1));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));

        PayRenewalRequest payRequest = new PayRenewalRequest("BANK_TRANSFER", new BigDecimal("1100000"), "IDEM-SAME");

        RenewalPaymentResponse retryResponse = renewalService.payRenewal(customerId, renewalId, payRequest);

        assertNotNull(retryResponse);
        assertEquals("SUCCESS", retryResponse.status());
        assertEquals("PAY-RNW-PREVIOUS", retryResponse.referenceCode());
        assertEquals(LocalDate.of(2026, 12, 1), retryResponse.contractNewEndDate());

        // Không gọi save thêm giao dịch mới hay cộng dồn ngày hợp đồng
        verify(paymentTransactionRepository, never()).saveAndFlush(any());
        verify(rentalContractRepository, never()).save(any());
    }

    @Test
    @DisplayName("BR17.5: Từ chối thanh toán khi đã quá hạn thanh toán (payment deadline)")
    void testPayRenewal_ConflictWhenPaymentDeadlineExpired() {
        Renewal renewal = new Renewal();
        renewal.setRenewalId(renewalId);
        renewal.setContractId(contractId);
        renewal.setStatus("APPROVED");
        renewal.setRenewalAmount(new BigDecimal("1100000"));
        // Hết hạn 1 giờ trước
        renewal.setPaymentDeadline(LocalDateTime.now(VIETNAM_ZONE).minusHours(1));

        when(paymentTransactionRepository.findByCreatedByAndIdempotencyKey(customerId, "IDEM-EXPIRED"))
                .thenReturn(Optional.empty());
        when(renewalRepository.findById(renewalId)).thenReturn(Optional.of(renewal));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));

        PayRenewalRequest payRequest = new PayRenewalRequest("BANK_TRANSFER", new BigDecimal("1100000"), "IDEM-EXPIRED");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                renewalService.payRenewal(customerId, renewalId, payRequest));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("hết hạn thanh toán"));
        assertEquals("EXPIRED", renewal.getStatus());
    }

    // =========================================================================
    // Preview & Cancel Tests
    // =========================================================================

    @Test
    @DisplayName("Xem trước báo giá gia hạn hợp đồng (previewRenewal)")
    void testPreviewRenewal_Success() {
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(handoverRecordRepository.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED"))
                .thenReturn(Optional.empty());
        when(renewalRepository.findByContractIdAndStatusIn(eq(contractId), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(storageUnitRepository.findById(unitId)).thenReturn(Optional.of(storageUnit));
        when(pricingPolicyRepository.findByFacilityIdAndStatus(facilityId, "ACTIVE"))
                .thenReturn(List.of(pricingPolicy));
        when(rentalPolicyRepository.findFirstByFacilityIdAndStatusOrderByEffectiveFromDesc(facilityId, "ACTIVE"))
                .thenReturn(Optional.of(rentalPolicy));

        RenewalPreviewResponse preview = renewalService.previewRenewal(customerId, contractId, 6);

        assertTrue(preview.canRenew());
        assertEquals(6, preview.extensionMonths());
        assertEquals(LocalDate.of(2026, 11, 1), preview.currentEndDate());
        assertEquals(LocalDate.of(2027, 5, 1), preview.newEndDate());
        assertEquals(new BigDecimal("6000000"), preview.subtotal());
        assertEquals(new BigDecimal("600000"), preview.vatAmount());
        assertEquals(new BigDecimal("6600000"), preview.totalAmount());
        assertEquals(24, preview.paymentHours());
    }

    @Test
    @DisplayName("Khách hàng tự hủy yêu cầu gia hạn khi còn ở trạng thái PENDING")
    void testCancelRenewal_Success() {
        Renewal renewal = new Renewal();
        renewal.setRenewalId(renewalId);
        renewal.setContractId(contractId);
        renewal.setStatus("PENDING");

        when(renewalRepository.findById(renewalId)).thenReturn(Optional.of(renewal));
        when(rentalContractRepository.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(renewalRepository.save(any(Renewal.class))).thenAnswer(i -> i.getArgument(0));

        RenewalResponse response = renewalService.cancelRenewal(customerId, renewalId);

        assertEquals("CANCELLED", response.status());
    }
}
