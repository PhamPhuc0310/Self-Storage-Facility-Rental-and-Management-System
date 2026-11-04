package com.safebox.self_storage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.safebox.self_storage.controller.BusinessPolicyController.CreateEarlyReturnPolicyRequest;
import com.safebox.self_storage.entity.EarlyReturnPolicy;
import com.safebox.self_storage.exception.ApiExceptionHandler;
import com.safebox.self_storage.repository.EarlyReturnPolicyRepository;
import com.safebox.self_storage.repository.FacilityRepository;
import com.safebox.self_storage.repository.RentalPolicyRepository;
import com.safebox.self_storage.service.Sprint2Access;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BusinessPolicyControllerTest {

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private RentalPolicyRepository rentals;
    @Mock
    private EarlyReturnPolicyRepository returns;
    @Mock
    private FacilityRepository facilities;
    @Mock
    private Sprint2Access access;

    @InjectMocks
    private BusinessPolicyController controller;

    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Reject when business approval is false or missing")
    void rejectUnapproved() throws Exception {
        CreateEarlyReturnPolicyRequest req = new CreateEarlyReturnPolicyRequest(
                "ERP-V1", LocalDate.now(), null, true, true, false
        );

        mvc.perform(post("/api/business/facilities/{facilityId}/early-return-policies", facilityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Business approval is required before activating an early-return policy."));
    }

    @Test
    @DisplayName("Reject when effectiveTo is before effectiveFrom")
    void rejectInvalidDateRange() throws Exception {
        CreateEarlyReturnPolicyRequest req = new CreateEarlyReturnPolicyRequest(
                "ERP-V1", LocalDate.now(), LocalDate.now().minusDays(1), true, true, true
        );

        mvc.perform(post("/api/business/facilities/{facilityId}/early-return-policies", facilityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Policy effective date range is invalid."));
    }

    @Test
    @DisplayName("Create early return policy successfully")
    void createSuccess() throws Exception {
        when(facilities.existsById(facilityId)).thenReturn(true);
        when(returns.findAll()).thenReturn(List.of());
        when(returns.save(any(EarlyReturnPolicy.class))).thenAnswer(invocation -> {
            EarlyReturnPolicy p = invocation.getArgument(0);
            return p;
        });

        CreateEarlyReturnPolicyRequest req = new CreateEarlyReturnPolicyRequest(
                "ERP-Q7-2026-V1", LocalDate.of(2026, 1, 1), null, true, true, true
        );

        mvc.perform(post("/api/business/facilities/{facilityId}/early-return-policies", facilityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("ERP-Q7-2026-V1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.depositRefundable").value(true))
                .andExpect(jsonPath("$.inspectionDeductionAllowed").value(true));
    }

    @Test
    void rejectOverlappingActivePolicy() throws Exception {
        EarlyReturnPolicy active = new EarlyReturnPolicy();
        active.facilityId = facilityId;
        active.version = "ERP-V1";
        active.status = "ACTIVE";
        active.effectiveFrom = LocalDate.of(2026, 11, 1);
        active.effectiveTo = LocalDate.of(2026, 11, 30);
        when(facilities.existsById(facilityId)).thenReturn(true);
        when(returns.findAll()).thenReturn(List.of(active));
        CreateEarlyReturnPolicyRequest req = new CreateEarlyReturnPolicyRequest(
                "ERP-V2", LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 15), true, true, true);

        mvc.perform(post("/api/business/facilities/{facilityId}/early-return-policies", facilityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
        org.mockito.Mockito.verify(returns, org.mockito.Mockito.never()).save(any(EarlyReturnPolicy.class));
    }
}
