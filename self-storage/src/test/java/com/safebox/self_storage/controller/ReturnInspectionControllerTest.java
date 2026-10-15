package com.safebox.self_storage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.safebox.self_storage.dto.CompleteReturnInspectionRequest;
import com.safebox.self_storage.dto.CreateReturnScheduleRequest;
import com.safebox.self_storage.dto.response.FacilityReturnTaskItemResponse;
import com.safebox.self_storage.dto.response.ReturnInspectionDetailResponse;
import com.safebox.self_storage.dto.response.ReturnScheduleResponse;
import com.safebox.self_storage.exception.ApiExceptionHandler;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.service.ReturnInspectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReturnInspectionControllerTest {

    private MockMvc mvc;
    private ObjectMapper objectMapper;

    @Mock
    private ReturnInspectionService returnInspectionService;

    @InjectMocks
    private ReturnInspectionController controller;

    private final UUID customerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID handoverId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UUID userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                )
        );
    }

    @Test
    @DisplayName("POST /api/customer/rentals/{id}/return-schedule: Đặt lịch trả kho thành công -> 200 OK")
    void testScheduleReturn_Success() throws Exception {
        authenticate(customerId, "CUSTOMER");

        LocalDateTime apptTime = LocalDateTime.now().plusDays(3);
        CreateReturnScheduleRequest request = new CreateReturnScheduleRequest(
                contractId, apptTime, "Đã chuẩn bị dọn kho", true, true);

        ReturnScheduleResponse response = new ReturnScheduleResponse(
                handoverId, contractId, "SB-CT-2026-TEST", unitId, "Q7-M101",
                "Tầng 1", "Khu A", facilityId, "SafeBox Quận 7", "123 Q7",
                "0900000000", "07:00", "21:00", apptTime, "SCHEDULED",
                "Đã lên lịch hẹn", staffId, "Nhân viên trực", "Ghi chú",
                LocalDate.now().plusDays(3), true, LocalDateTime.now()
        );

        when(returnInspectionService.scheduleReturnAppointment(eq(customerId), eq(contractId), any()))
                .thenReturn(response);

        mvc.perform(post("/api/customer/rentals/" + contractId + "/return-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractNumber").value("SB-CT-2026-TEST"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @DisplayName("GET /api/customer/rentals/{id}/return-schedule: Xem lịch trả kho thành công -> 200 OK")
    void testGetReturnSchedule_Success() throws Exception {
        authenticate(customerId, "CUSTOMER");

        ReturnScheduleResponse response = new ReturnScheduleResponse(
                handoverId, contractId, "SB-CT-2026-TEST", unitId, "Q7-M101",
                "Tầng 1", "Khu A", facilityId, "SafeBox Quận 7", "123 Q7",
                "0900000000", "07:00", "21:00", LocalDateTime.now().plusDays(1), "SCHEDULED",
                "Đã lên lịch hẹn", staffId, "Nhân viên trực", null,
                LocalDate.now().plusDays(1), true, LocalDateTime.now()
        );

        when(returnInspectionService.getReturnSchedule(eq(customerId), eq(contractId)))
                .thenReturn(response);

        mvc.perform(get("/api/customer/rentals/" + contractId + "/return-schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facilityName").value("SafeBox Quận 7"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @DisplayName("GET /api/facility/returns: Nhân viên xem danh sách lịch trả kho tại cơ sở -> 200 OK")
    void testGetFacilityReturns_Success() throws Exception {
        authenticate(staffId, "FACILITY_STAFF");

        FacilityReturnTaskItemResponse taskItem = new FacilityReturnTaskItemResponse(
                handoverId, contractId, "SB-CT-2026-TEST", "Nguyễn Văn An", "0901234567",
                "an@test.com", unitId, "Q7-M101", "Tầng 1", "Khu A", facilityId,
                "SafeBox Quận 7", LocalDateTime.now().plusDays(1), null, "SCHEDULED",
                "Chờ tiếp nhận", staffId, "Phạm Gia Huy", true, null
        );

        when(returnInspectionService.getFacilityReturnAppointments(eq(staffId), eq(facilityId), eq("SCHEDULED")))
                .thenReturn(List.of(taskItem));

        mvc.perform(get("/api/facility/returns")
                        .param("facilityId", facilityId.toString())
                        .param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$[0].unitNumber").value("Q7-M101"));
    }

    @Test
    @DisplayName("POST /api/facility/returns/{id}/complete: Hoàn tất nhận lại kho -> 200 OK")
    void testCompleteReturnInspection_Success() throws Exception {
        authenticate(staffId, "FACILITY_STAFF");

        CompleteReturnInspectionRequest request = new CompleteReturnInspectionRequest();
        request.setHandoverId(handoverId);
        request.setLeftoverItems(false);
        request.setAccessReturned(true);
        request.setCustomerConfirmed(true);
        request.setConditionStatus("GOOD");

        ReturnInspectionDetailResponse detail = new ReturnInspectionDetailResponse(
                handoverId, UUID.randomUUID(), contractId, "SB-CT-2026-TEST", customerId,
                "Nguyễn Văn An", "0901234567", "an@test.com", unitId, "Q7-M101",
                "Tầng 1", "Khu A", facilityId, "SafeBox Quận 7", "123 Q7",
                LocalDateTime.now(), LocalDateTime.now(), "COMPLETED", "RETURNED",
                "AVAILABLE", "GOOD", null, BigDecimal.ZERO, null, null,
                true, "CARD_AND_KEY", "Lock #LCK-99214", true, "Nguyễn Văn An",
                "Phạm Gia Huy", staffId, "Phạm Gia Huy", new BigDecimal("1500000"),
                0, false, "Tiền cọc lưu giữ theo dõi", null
        );

        when(returnInspectionService.completeReturnHandoverAndInspection(eq(staffId), any()))
                .thenReturn(detail);

        mvc.perform(post("/api/facility/returns/" + handoverId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handoverStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.contractStatus").value("RETURNED"))
                .andExpect(jsonPath("$.unitStatus").value("AVAILABLE"));
    }
}
