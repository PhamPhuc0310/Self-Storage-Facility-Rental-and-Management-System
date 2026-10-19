package com.safebox.self_storage.controller;

import com.safebox.self_storage.config.SecurityConfig;
import com.safebox.self_storage.entity.Role;
import com.safebox.self_storage.entity.RentalContract;
import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.security.JwtService;
import com.safebox.self_storage.service.AuthService;
import com.safebox.self_storage.service.ContractQueryService;
import com.safebox.self_storage.service.ReservationApprovalService;
import java.util.UUID;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationApprovalController.class)
@Import(SecurityConfig.class)
class ReservationApprovalControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ReservationApprovalService approval;
    @MockBean ContractQueryService contracts;
    @MockBean AuthService auth;
    @MockBean JwtService jwt;
    UUID managerId = UUID.randomUUID();
    UUID reservationId = UUID.randomUUID();

    @BeforeEach void setUp() {
        when(jwt.userId("manager-token")).thenReturn(managerId);
        Role role = new Role(); role.setRoleName("FACILITY_MANAGER");
        User manager = new User(); manager.setUserId(managerId); manager.setRole(role);
        manager.setStatus("ACTIVE"); manager.setEmailVerified(true);
        when(jwt.isCurrent(eq("manager-token"), any(User.class))).thenReturn(true);
        when(auth.activeUser(managerId)).thenReturn(manager);
    }

    @Test void unavailableUnitReturnsConflictMessage() throws Exception {
        when(approval.approve(reservationId))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "Không còn ô kho phù hợp."));
        mvc.perform(post("/api/manager/reservations/{id}/confirm", reservationId)
                        .header("Authorization", "Bearer manager-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Không còn ô kho phù hợp."));
    }

    @Test void successfulConfirmationReturnsCreatedContract() throws Exception {
        RentalContract contract = new RentalContract(); contract.contractId = UUID.randomUUID();
        when(approval.approve(reservationId)).thenReturn(contract);
        when(contracts.get(contract.contractId)).thenReturn(new ContractQueryService.ContractView(
                contract.contractId, reservationId, "SB-CT-TEST", "SafeBox", "A-01",
                LocalDate.of(2026, 10, 19), LocalDate.of(2027, 10, 19), "CONFIRMED"));
        mvc.perform(post("/api/manager/reservations/{id}/confirm", reservationId)
                        .header("Authorization", "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()))
                .andExpect(jsonPath("$.contractId").value(contract.contractId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test void unexpectedFailureReturnsSafeMessage() throws Exception {
        when(approval.approve(reservationId)).thenThrow(new RuntimeException("private SQL detail"));
        mvc.perform(post("/api/manager/reservations/{id}/confirm", reservationId)
                        .header("Authorization", "Bearer manager-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Không thể xử lý yêu cầu. Vui lòng thử lại hoặc liên hệ hỗ trợ."));
    }
}
