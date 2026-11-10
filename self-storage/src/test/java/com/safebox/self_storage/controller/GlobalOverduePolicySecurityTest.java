package com.safebox.self_storage.controller;

import com.safebox.self_storage.config.SecurityConfig;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.security.JwtService;
import com.safebox.self_storage.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OverduePolicyGovernanceController.class)
@Import(SecurityConfig.class)
class GlobalOverduePolicySecurityTest {
    @Autowired MockMvc mvc;
    @MockBean OverduePolicyGovernanceService service;
    @MockBean JwtService jwt;
    @MockBean AuthService auth;
    final String url="/api/business/overdue-policies/global";
    @Test void anonymousAndOtherRolesCannotActivateOrReadGlobal() throws Exception {
        mvc.perform(post(url+"/activate").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        for(String name:List.of("SYSTEM_ADMIN","FACILITY_MANAGER","FACILITY_STAFF","CUSTOMER")) {
            UUID id=UUID.randomUUID();Role role=new Role();role.setRoleName(name);User user=new User();user.setUserId(id);user.setRole(role);
            when(jwt.userId(name)).thenReturn(id);when(jwt.isCurrent(name,user)).thenReturn(true);when(auth.activeUser(id)).thenReturn(user);
            mvc.perform(post(url+"/activate").header("Authorization","Bearer "+name).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
            mvc.perform(get(url).header("Authorization","Bearer "+name)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void businessCanReachDirectActivationWithoutProposalEndpoint() throws Exception {
        UUID id=UUID.randomUUID();Role role=new Role();role.setRoleName("BUSINESS_OPERATIONS_MANAGER");User user=new User();user.setUserId(id);user.setRole(role);
        when(jwt.userId("business")).thenReturn(id);when(jwt.isCurrent("business",user)).thenReturn(true);when(auth.activeUser(id)).thenReturn(user);
        when(service.activateGlobal(any())).thenReturn(new RentalPolicy());
        mvc.perform(post(url+"/activate").header("Authorization","Bearer business").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        verify(service).activateGlobal(any());
    }
}
