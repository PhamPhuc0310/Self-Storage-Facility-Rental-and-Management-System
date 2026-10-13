package com.safebox.self_storage.controller;

import com.safebox.self_storage.config.SecurityConfig;
import com.safebox.self_storage.dto.AppointmentRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.security.JwtService;
import com.safebox.self_storage.repository.FacilityRepository;
import com.safebox.self_storage.service.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(Sprint2Controller.class)
@Import(SecurityConfig.class)
class Sprint2ControllerTest {
    @Autowired MockMvc mvc;
    @MockBean PaymentService payments;
    @MockBean DepositRefundService refunds;
    @MockBean AppointmentService appointments;
    @MockBean ContractQueryService contracts;
    @MockBean Sprint2Access access;
    @MockBean FacilityRepository facilities;
    @MockBean AuthService auth;
    @MockBean JwtService jwt;
    UUID customerId = UUID.randomUUID(), managerId = UUID.randomUUID(), contractId = UUID.randomUUID();

    @BeforeEach void setup() {
        token("customer",customerId,"CUSTOMER");
        token("manager",managerId,"FACILITY_MANAGER");
    }
    void token(String value, UUID id, String roleName) {
        Role role = new Role(); role.setRoleName(roleName);
        User user = new User(); user.setUserId(id); user.setRole(role); user.setStatus("ACTIVE");
        when(jwt.userId(value)).thenReturn(id);
        when(jwt.isCurrent(eq(value),any(User.class))).thenReturn(true);
        when(auth.activeUser(id)).thenReturn(user);
    }
    @Test void customerCannotCallStaffTasks() throws Exception {
        mvc.perform(get("/api/staff/tasks").header("Authorization","Bearer customer"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(appointments);
    }
    @Test void staffWithoutAssignmentGetsNoFacilityAndCustomerIsForbidden() throws Exception {
        UUID staffId = UUID.randomUUID();
        token("staff", staffId, "FACILITY_STAFF");
        when(access.facilities()).thenReturn(List.of());
        mvc.perform(get("/api/staff/facilities").header("Authorization", "Bearer staff"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/staff/facilities").header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
    }
    @Test void managerGetsEmptyTaskList() throws Exception {
        when(appointments.daily(any(),isNull(),isNull(),isNull())).thenReturn(List.of());
        mvc.perform(get("/api/staff/tasks").param("date","2026-10-11").header("Authorization","Bearer manager"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void onlyManagerCanListAppointmentsAwaitingAssignment() throws Exception {
        when(appointments.awaitingAssignment(isNull())).thenReturn(List.of());
        mvc.perform(get("/api/manager/appointments/awaiting-assignment").header("Authorization", "Bearer manager"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/manager/appointments/awaiting-assignment").header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
        verify(appointments, times(1)).awaitingAssignment(isNull());
    }
    @Test void onlyStaffCanListOwnUpcomingTasks() throws Exception {
        UUID staffId = UUID.randomUUID();
        token("staff", staffId, "FACILITY_STAFF");
        when(appointments.upcomingMine()).thenReturn(List.of());
        mvc.perform(get("/api/staff/tasks/upcoming").header("Authorization", "Bearer staff"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/staff/tasks/upcoming").header("Authorization", "Bearer customer"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/tasks/upcoming").header("Authorization", "Bearer manager"))
                .andExpect(status().isForbidden());
        verify(appointments, times(1)).upcomingMine();
    }
    @Test void foreignContractReturnsForbidden() throws Exception {
        when(payments.summary(contractId)).thenThrow(new BusinessException(HttpStatus.FORBIDDEN,"Bạn không có quyền thao tác với hợp đồng này."));
        mvc.perform(get("/api/contracts/{id}/payments/summary",contractId).header("Authorization","Bearer customer"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }
    @Test void managerRefundEndpointsDoNotInvokeAppointmentCancellation() throws Exception {
        UUID refundId = UUID.randomUUID();
        mvc.perform(put("/api/manager/deposit-refunds/{id}/review", refundId)
                .header("Authorization", "Bearer manager").contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\",\"approvedAmount\":400000,\"note\":\"Đã duyệt\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/manager/deposit-refunds/{id}/complete", refundId)
                .header("Authorization", "Bearer manager").contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionReference\":\"REF-123\"}"))
                .andExpect(status().isOk());
        verify(refunds).review(eq(refundId), any());
        verify(refunds).complete(eq(refundId), any());
        verifyNoInteractions(appointments);
    }
    @Test void bookingAndRescheduleJsonReachServiceWithVietnamOffsetUnchanged() throws Exception {
        String body = "{\"appointmentTime\":\"2026-11-25T09:00:00+07:00\"}";
        mvc.perform(post("/api/contracts/{id}/pickup-appointment", contractId)
                .header("Authorization", "Bearer customer").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/contracts/{id}/pickup-appointment", contractId)
                .header("Authorization", "Bearer customer").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        org.mockito.ArgumentCaptor<AppointmentRequest> create = org.mockito.ArgumentCaptor.forClass(AppointmentRequest.class);
        org.mockito.ArgumentCaptor<AppointmentRequest> change = org.mockito.ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(appointments).create(eq(contractId), create.capture());
        verify(appointments).reschedule(eq(contractId), change.capture());
        assertEquals(ZoneOffset.ofHours(7), create.getValue().appointmentTime().getOffset());
        assertEquals(LocalDateTime.of(2026,11,25,9,0), create.getValue().appointmentTime().toLocalDateTime());
        assertEquals(create.getValue().appointmentTime(), change.getValue().appointmentTime());
    }
}
