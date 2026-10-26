package com.safebox.self_storage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.safebox.self_storage.dto.renewal.*;
import com.safebox.self_storage.exception.ApiExceptionHandler;
import com.safebox.self_storage.service.RenewalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RenewalControllerTest {

    private MockMvc customerMvc;
    private MockMvc managerMvc;
    private ObjectMapper objectMapper;

    @Mock
    private RenewalService renewalService;

    @InjectMocks
    private RenewalCustomerController customerController;

    @InjectMocks
    private RenewalManagerController managerController;

    private final UUID customerId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();
    private final UUID renewalId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        customerMvc = MockMvcBuilders.standaloneSetup(customerController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        managerMvc = MockMvcBuilders.standaloneSetup(managerController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsCustomer() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                customerId, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void authenticateAsManager() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                managerId, null, List.of(new SimpleGrantedAuthority("ROLE_FACILITY_MANAGER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("CUSTOMER: GET /api/customer/contracts/{contractId}/renewals/preview")
    void testPreviewRenewalEndpoint() throws Exception {
        authenticateAsCustomer();

        RenewalPreviewResponse response = new RenewalPreviewResponse(
                contractId,
                "HD-202610-0001",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 2, 1),
                3,
                new BigDecimal("1000000"),
                new BigDecimal("3000000"),
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                new BigDecimal("3300000"),
                24,
                true,
                "Ước tính phí gia hạn thành công"
        );

        when(renewalService.previewRenewal(eq(customerId), eq(contractId), eq(3)))
                .thenReturn(response);

        customerMvc.perform(get("/api/customer/contracts/{contractId}/renewals/preview", contractId)
                        .param("months", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractNumber").value("HD-202610-0001"))
                .andExpect(jsonPath("$.extensionMonths").value(3))
                .andExpect(jsonPath("$.totalAmount").value(3300000))
                .andExpect(jsonPath("$.canRenew").value(true));
    }

    @Test
    @DisplayName("CUSTOMER: POST /api/customer/contracts/{contractId}/renewals")
    void testRequestRenewalEndpoint() throws Exception {
        authenticateAsCustomer();

        CreateRenewalRequest request = new CreateRenewalRequest(3, "Gia hạn thêm 3 tháng", BigDecimal.ZERO);
        RenewalResponse response = new RenewalResponse(
                renewalId,
                contractId,
                "HD-202610-0001",
                customerId,
                "Nguyễn Văn Khách",
                UUID.randomUUID(),
                "SafeBox Tân Bình",
                "A-101",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 2, 1),
                3,
                new BigDecimal("1000000"),
                new BigDecimal("3000000"),
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                new BigDecimal("3300000"),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                "PENDING",
                "Chờ quản lý xét duyệt",
                null,
                null,
                null,
                null,
                false,
                true
        );

        when(renewalService.requestRenewal(eq(customerId), eq(contractId), any(CreateRenewalRequest.class)))
                .thenReturn(response);

        customerMvc.perform(post("/api/customer/contracts/{contractId}/renewals", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.renewalId").value(renewalId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.extensionMonths").value(3));
    }

    @Test
    @DisplayName("CUSTOMER: POST /api/customer/renewals/{renewalId}/pay")
    void testPayRenewalEndpoint() throws Exception {
        authenticateAsCustomer();

        PayRenewalRequest request = new PayRenewalRequest("BANK_TRANSFER", new BigDecimal("3300000"), "IDEM-12345");
        RenewalPaymentResponse response = new RenewalPaymentResponse(
                UUID.randomUUID(),
                renewalId,
                contractId,
                "PAY-RNW-20261021-001",
                new BigDecimal("3300000"),
                "BANK_TRANSFER",
                "SUCCESS",
                LocalDateTime.now(),
                LocalDate.of(2027, 2, 1),
                "Thanh toán phí gia hạn thành công."
        );

        when(renewalService.payRenewal(eq(customerId), eq(renewalId), any(PayRenewalRequest.class)))
                .thenReturn(response);

        customerMvc.perform(post("/api/customer/renewals/{renewalId}/pay", renewalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(3300000))
                .andExpect(jsonPath("$.referenceCode").value("PAY-RNW-20261021-001"));
    }

    @Test
    @DisplayName("MANAGER: GET /api/manager/renewals")
    void testGetFacilityRenewalsEndpoint() throws Exception {
        authenticateAsManager();

        when(renewalService.getFacilityRenewals(eq(managerId), org.mockito.ArgumentMatchers.nullable(UUID.class), org.mockito.ArgumentMatchers.nullable(String.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 10), 0));

        managerMvc.perform(get("/api/manager/renewals")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("MANAGER: POST /api/manager/renewals/{renewalId}/approve")
    void testApproveRenewalEndpoint() throws Exception {
        authenticateAsManager();

        RenewalResponse response = new RenewalResponse(
                renewalId,
                contractId,
                "HD-202610-0001",
                customerId,
                "Nguyễn Văn Khách",
                UUID.randomUUID(),
                "SafeBox Tân Bình",
                "A-101",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 2, 1),
                3,
                new BigDecimal("1000000"),
                new BigDecimal("3000000"),
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                new BigDecimal("3300000"),
                LocalDateTime.now(),
                LocalDateTime.now(),
                managerId,
                "Trần Quản Lý",
                LocalDateTime.now().plusHours(24),
                "APPROVED",
                "Đã duyệt - Chờ thanh toán",
                null,
                UUID.randomUUID(),
                "INV-RNW-001",
                "UNPAID",
                true,
                true
        );

        when(renewalService.reviewRenewal(eq(managerId), eq(renewalId), any(ReviewRenewalRequest.class)))
                .thenReturn(response);

        managerMvc.perform(post("/api/manager/renewals/{renewalId}/approve", renewalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-RNW-001"));
    }

    @Test
    @DisplayName("MANAGER: POST /api/manager/renewals/{renewalId}/reject")
    void testRejectRenewalEndpoint() throws Exception {
        authenticateAsManager();

        RenewalResponse response = new RenewalResponse(
                renewalId,
                contractId,
                "HD-202610-0001",
                customerId,
                "Nguyễn Văn Khách",
                UUID.randomUUID(),
                "SafeBox Tân Bình",
                "A-101",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 2, 1),
                3,
                new BigDecimal("1000000"),
                new BigDecimal("3000000"),
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                new BigDecimal("3300000"),
                LocalDateTime.now(),
                LocalDateTime.now(),
                managerId,
                "Trần Quản Lý",
                null,
                "REJECTED",
                "Bị từ chối",
                "Cần nâng cấp kho",
                null,
                null,
                null,
                false,
                false
        );

        when(renewalService.reviewRenewal(eq(managerId), eq(renewalId), any(ReviewRenewalRequest.class)))
                .thenReturn(response);

        managerMvc.perform(post("/api/manager/renewals/{renewalId}/reject", renewalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Cần nâng cấp kho"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Cần nâng cấp kho"));
    }
}
