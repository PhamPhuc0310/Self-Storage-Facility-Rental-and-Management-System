package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.response.RentalContractDetailResponse;
import com.safebox.self_storage.dto.response.RentalContractListItemResponse;
import com.safebox.self_storage.exception.ApiExceptionHandler;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.service.RentalContractService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RentalContractControllerTest {

    private MockMvc mvc;

    @Mock
    private RentalContractService rentalContractService;

    @InjectMocks
    private RentalContractController controller;

    private final UUID customerId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsCustomer() {
        var auth = new UsernamePasswordAuthenticationToken(
                customerId, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void authenticateAsManager() {
        var auth = new UsernamePasswordAuthenticationToken(
                managerId, null, List.of(new SimpleGrantedAuthority("ROLE_FACILITY_MANAGER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private RentalContractListItemResponse sampleListItem() {
        return new RentalContractListItemResponse(
                contractId,
                "SB-CT-2026-TEST01",
                UUID.randomUUID(),
                customerId,
                unitId,
                "TD-101",
                "Tầng 1",
                "Khu A",
                facilityId,
                "SafeBox Thủ Đức",
                "123 Võ Văn Ngân, Thủ Đức",
                "19008868",
                1,
                "Kho Cá Nhân 10m²",
                "Tự quản",
                "10m²",
                new BigDecimal("4.0"),
                new BigDecimal("2.5"),
                new BigDecimal("2.8"),
                new BigDecimal("10.0"),
                new BigDecimal("28.0"),
                "4.0m x 2.5m x 2.8m",
                "/images/demo/standard-storage.png",
                LocalDate.now().minusDays(15),
                LocalDate.now().plusDays(45),
                LocalDate.now().minusDays(15),
                null,
                "15/09/2026 - 15/11/2026",
                new BigDecimal("2500000.00"),
                new BigDecimal("2500000.00"),
                new BigDecimal("5000000.00"),
                new BigDecimal("5000000.00"),
                BigDecimal.ZERO,
                "ACTIVE",
                "Đang thuê",
                "bg-emerald-100 text-emerald-800",
                "PAID",
                "Đã thanh toán đủ",
                "bg-emerald-50 text-emerald-700",
                45,
                false,
                0,
                true,
                LocalDateTime.now().minusDays(15)
        );
    }

    @Test
    @DisplayName("GET /api/customer/rentals - 200 OK khi khách hàng đăng nhập hợp lệ")
    void testGetMyRentals_Success() throws Exception {
        authenticateAsCustomer();
        RentalContractListItemResponse item = sampleListItem();
        when(rentalContractService.getCustomerRentals(eq(customerId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        mvc.perform(get("/api/customer/rentals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].contractNumber").value("SB-CT-2026-TEST01"))
                .andExpect(jsonPath("$.content[0].unitNumber").value("TD-101"))
                .andExpect(jsonPath("$.content[0].facilityName").value("SafeBox Thủ Đức"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.content[0].canScheduleReturn").value(true));
    }

    @Test
    @DisplayName("GET /api/customer/rentals/{id} - 200 OK trả về chi tiết hợp đồng của khách")
    void testGetMyRentalDetail_Success() throws Exception {
        authenticateAsCustomer();
        RentalContractListItemResponse item = sampleListItem();
        RentalContractDetailResponse detail = new RentalContractDetailResponse(
                item,
                "MONTHLY",
                "v1",
                LocalDateTime.now().minusDays(15),
                LocalDateTime.now().minusDays(15),
                "Nguyễn Văn A",
                "vana@safebox.vn",
                "0901234567",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        when(rentalContractService.getCustomerRentalDetail(customerId, contractId)).thenReturn(detail);

        mvc.perform(get("/api/customer/rentals/" + contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract.contractNumber").value("SB-CT-2026-TEST01"))
                .andExpect(jsonPath("$.customerName").value("Nguyễn Văn A"));
    }

    @Test
    @DisplayName("GET /api/customer/rentals/{id} - 403 Forbidden khi khách xem hợp đồng của người khác (BR15.9)")
    void testGetMyRentalDetail_Forbidden() throws Exception {
        authenticateAsCustomer();
        when(rentalContractService.getCustomerRentalDetail(customerId, contractId))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem thông tin hợp đồng này"));

        mvc.perform(get("/api/customer/rentals/" + contractId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/facility/rentals - 200 OK khi Quản lý cơ sở gọi thành công")
    void testGetFacilityRentals_SuccessForManager() throws Exception {
        authenticateAsManager();
        RentalContractListItemResponse item = sampleListItem();
        when(rentalContractService.getFacilityRentals(eq(managerId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        mvc.perform(get("/api/facility/rentals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].contractNumber").value("SB-CT-2026-TEST01"));
    }
}
