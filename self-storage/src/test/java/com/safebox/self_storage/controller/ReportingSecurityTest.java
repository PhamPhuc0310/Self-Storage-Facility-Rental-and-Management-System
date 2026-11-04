package com.safebox.self_storage.controller;

import com.safebox.self_storage.config.SecurityConfig;
import com.safebox.self_storage.security.JwtService;
import com.safebox.self_storage.service.AuthService;
import com.safebox.self_storage.service.ReportingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportingController.class)
@Import(SecurityConfig.class)
class ReportingSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean ReportingService reports;
    @MockBean JwtService jwt;
    @MockBean AuthService auth;
    final String url = "/api/reports/financial-summary?startDate=2026-10-01&endDate=2026-10-31";

    @Test void anonymousCannotViewReports() throws Exception {
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reports);
    }
    @Test @WithMockUser(roles = "CUSTOMER") void customerCannotViewReports() throws Exception {
        mvc.perform(get(url)).andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }
    @Test @WithMockUser(roles = "BUSINESS_OPERATIONS_MANAGER") void businessManagerCanReachScopedReports() throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk());
        verify(reports).financial(new ReportingService.Filters(java.time.LocalDate.of(2026,10,1), java.time.LocalDate.of(2026,10,31), null));
    }
    @Test @WithMockUser(roles = "FACILITY_MANAGER") void facilityManagerCanReachScopedReports() throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk());
    }
}
