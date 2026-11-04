package com.safebox.self_storage.controller;

import com.safebox.self_storage.config.SecurityConfig;
import com.safebox.self_storage.entity.Role;
import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.security.JwtService;
import com.safebox.self_storage.service.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PriceManagementController.class,BusinessPolicyController.class,OverduePolicyGovernanceController.class})
@Import(SecurityConfig.class)
class Phase2SecurityTest {
    @Autowired MockMvc mvc;
    @MockBean PriceManagementService prices;
    @MockBean OverduePolicyGovernanceService governance;
    @MockBean StorageUnitTypeRepository types;
    @MockBean RentalPolicyRepository rental;
    @MockBean EarlyReturnPolicyRepository returns;
    @MockBean FacilityRepository facilities;
    @MockBean Sprint2Access access;
    @MockBean JwtService jwt;
    @MockBean AuthService auth;
    private void role(String name) {
        UUID id=UUID.randomUUID(); Role role=new Role();role.setRoleName(name);
        User u=new User();u.setUserId(id);u.setRole(role);u.setStatus("ACTIVE");
        when(jwt.userId("test-token")).thenReturn(id);
        when(jwt.isCurrent("test-token",u)).thenReturn(true);
        when(auth.activeUser(id)).thenReturn(u);
    }
    @Test void managerCannotMutatePriceOrApprovePolicy() throws Exception {
        role("FACILITY_MANAGER"); UUID facility=UUID.randomUUID();
        mvc.perform(post("/api/business/facilities/{id}/prices",facility).header("Authorization","Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/business/price-proposals/{id}/apply",UUID.randomUUID())
                .header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        mvc.perform(post("/api/business/facilities/{id}/early-return-policies",facility)
                .header("Authorization","Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(prices);
    }
    @Test void adminCannotBypassCommercialActions() throws Exception {
        role("SYSTEM_ADMIN");
        mvc.perform(get("/api/business/policies").header("Authorization","Bearer test-token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/business/facilities/{id}/prices",UUID.randomUUID())
                .header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(prices);
    }
    @Test void managerCannotApproveOverduePolicy() throws Exception {
        role("FACILITY_MANAGER");
        UUID facility=UUID.randomUUID(), proposal=UUID.randomUUID();
        mvc.perform(post("/api/business/facilities/{facilityId}/overdue-policy-proposals/{proposalId}/approve",facility,proposal)
                .header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Đã xem xét\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(governance);
    }
    @Test void adminCannotProposeOrApproveOverduePolicy() throws Exception {
        role("SYSTEM_ADMIN"); UUID facility=UUID.randomUUID(), proposal=UUID.randomUUID();
        mvc.perform(post("/api/manager/facilities/{facilityId}/overdue-policy-proposals",facility)
                .header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/business/facilities/{facilityId}/overdue-policy-proposals/{proposalId}/approve",facility,proposal)
                .header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Đã xem xét\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(governance);
    }
}
