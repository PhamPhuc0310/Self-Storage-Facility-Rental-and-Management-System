package com.safebox.self_storage.controller;

import com.safebox.self_storage.config.SecurityConfig;
import com.safebox.self_storage.entity.Role;
import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.repository.FacilityRepository;
import com.safebox.self_storage.repository.StorageUnitRepository;
import com.safebox.self_storage.repository.UserFacilityAssignmentRepository;
import com.safebox.self_storage.security.JwtService;
import com.safebox.self_storage.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessFacilityController.class)
@Import(SecurityConfig.class)
class ManagementPhase1SecurityTest {
    @Autowired MockMvc mvc;
    @MockBean FacilityRepository facilities;
    @MockBean StorageUnitRepository units;
    @MockBean UserFacilityAssignmentRepository assignments;
    @MockBean JwtService jwt;
    @MockBean AuthService auth;
    private static final String BODY="{\"facilityCode\":\"HCM_Q7_01\",\"regionCode\":\"HCM_Q7\",\"name\":\"SafeBox Q7\",\"address\":\"123 Nguyen Van Linh\",\"status\":\"INACTIVE\"}";
    @Test void anonymousCannotCreateFacility() throws Exception {
        mvc.perform(post("/api/business/facilities").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verify(facilities,never()).save(any());
    }
    @Test void facilityManagerCannotCreateBusinessFacility() throws Exception {
        UUID id=UUID.randomUUID();Role role=new Role();role.setRoleName("FACILITY_MANAGER");User user=new User();user.setUserId(id);user.setRole(role);
        when(jwt.userId("manager-token")).thenReturn(id);when(jwt.isCurrent("manager-token",user)).thenReturn(true);when(auth.activeUser(id)).thenReturn(user);
        mvc.perform(post("/api/business/facilities").header("Authorization","Bearer manager-token").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verify(facilities,never()).save(any());
    }
}
