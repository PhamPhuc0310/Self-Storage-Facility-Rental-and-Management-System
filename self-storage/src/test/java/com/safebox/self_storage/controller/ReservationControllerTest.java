package com.safebox.self_storage.controller;

import com.safebox.self_storage.config.SecurityConfig;
import com.safebox.self_storage.dto.CancelReservationRequest;
import com.safebox.self_storage.dto.CreateReservationRequest;
import com.safebox.self_storage.dto.RejectReservationRequest;
import com.safebox.self_storage.dto.response.ReservationResponse;
import com.safebox.self_storage.entity.Role;
import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.security.JwtService;
import com.safebox.self_storage.service.AuthService;
import com.safebox.self_storage.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private ReservationService reservationService;
    @MockBean private AuthService auth;
    @MockBean private JwtService jwt;

    private final UUID customerId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID reservationId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        // Customer setup
        when(jwt.userId("customer-token")).thenReturn(customerId);
        Role customerRole = new Role();
        customerRole.setRoleName("CUSTOMER");
        User customer = new User();
        customer.setUserId(customerId);
        customer.setRole(customerRole);
        customer.setStatus("ACTIVE");
        customer.setEmailVerified(true);
        when(jwt.isCurrent(eq("customer-token"), any(User.class))).thenReturn(true);
        when(auth.activeUser(customerId)).thenReturn(customer);

        // Manager setup
        when(jwt.userId("manager-token")).thenReturn(managerId);
        Role managerRole = new Role();
        managerRole.setRoleName("FACILITY_MANAGER");
        User manager = new User();
        manager.setUserId(managerId);
        manager.setRole(managerRole);
        manager.setStatus("ACTIVE");
        manager.setEmailVerified(true);
        when(jwt.isCurrent(eq("manager-token"), any(User.class))).thenReturn(true);
        when(auth.activeUser(managerId)).thenReturn(manager);
    }

    private ReservationResponse sampleResponse(String status) {
        return new ReservationResponse(
                reservationId,
                "#SB-REQ-2026-0001",
                customerId,
                "Nguyen Van A",
                "vana@safebox.vn",
                "0901234567",
                facilityId,
                "SafeBox Thu Duc",
                "123 Vo Van Ngan",
                "0900000001",
                1,
                "Kho Mini 10m2",
                "STANDARD",
                "10 m²",
                BigDecimal.valueOf(10.0),
                "/images/demo/standard-storage.png",
                UUID.randomUUID(),
                BigDecimal.valueOf(2000000),
                3,
                LocalDate.now(),
                LocalDate.now().plusMonths(3),
                LocalDateTime.now().plusDays(1),
                BigDecimal.valueOf(6000000),
                BigDecimal.valueOf(2000000),
                status,
                ReservationResponse.getStatusLabel(status),
                LocalDateTime.now(),
                null,
                null
        );
    }

    // =========================================================================
    // UC05: Tạo yêu cầu đặt kho
    // =========================================================================
    @Test
    void createReservation_authenticatedCustomer_returnsCreated() throws Exception {
        when(reservationService.createReservation(eq(customerId), any(CreateReservationRequest.class)))
                .thenReturn(sampleResponse("PENDING"));

        String body = String.format("""
                {
                    "facilityId": "%s",
                    "typeId": 1,
                    "startDate": "%s",
                    "months": 3
                }
                """, facilityId, LocalDate.now().plusDays(1));

        mvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestCode").value("#SB-REQ-2026-0001"))
                .andExpect(jsonPath("$.estimatedRentalAmount").value(6000000));
    }

    @Test
    void createReservation_unauthenticated_returnsUnauthorized() throws Exception {
        mvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // UC06: Xem và hủy yêu cầu của tôi
    // =========================================================================
    @Test
    void getMyReservations_customer_returnsList() throws Exception {
        when(reservationService.getMyReservations(eq(customerId), any(), any()))
                .thenReturn(List.of(sampleResponse("PENDING")));

        mvc.perform(get("/api/reservations/my")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestCode").value("#SB-REQ-2026-0001"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void cancelMyReservation_customer_returnsUpdatedReservation() throws Exception {
        when(reservationService.cancelMyReservation(eq(customerId), eq(reservationId), any(CancelReservationRequest.class)))
                .thenReturn(sampleResponse("CANCELLED"));

        mvc.perform(post("/api/reservations/my/" + reservationId + "/cancel")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"Bận việc đột xuất\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // =========================================================================
    // UC08: Xác nhận hoặc từ chối yêu cầu đặt kho
    // =========================================================================
    @Test
    void getAllReservations_manager_returnsList() throws Exception {
        when(reservationService.getAllReservations(any(), any(), any()))
                .thenReturn(List.of(sampleResponse("PENDING")));

        mvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestCode").value("#SB-REQ-2026-0001"));
    }

    @Test
    void getAllReservations_customer_forbidden() throws Exception {
        mvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveReservation_manager_returnsConfirmed() throws Exception {
        when(reservationService.approveReservation(reservationId))
                .thenReturn(sampleResponse("CONFIRMED"));

        mvc.perform(post("/api/reservations/" + reservationId + "/approve")
                        .header("Authorization", "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void rejectReservation_manager_returnsRejected() throws Exception {
        when(reservationService.rejectReservation(eq(reservationId), any(RejectReservationRequest.class)))
                .thenReturn(sampleResponse("REJECTED"));

        mvc.perform(post("/api/reservations/" + reservationId + "/reject")
                        .header("Authorization", "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"Không đủ thông tin hàng hóa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
