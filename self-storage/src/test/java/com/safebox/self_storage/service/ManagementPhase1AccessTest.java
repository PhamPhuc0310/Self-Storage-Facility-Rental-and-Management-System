package com.safebox.self_storage.service;

import com.safebox.self_storage.controller.AdminAccessController;
import com.safebox.self_storage.controller.BusinessFacilityController;
import com.safebox.self_storage.dto.manager.BusinessFacilityRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManagementPhase1AccessTest {
    @Test void businessManagerCreatesOnlyValidatedUniqueFacility() {
        FacilityRepository facilities=mock(FacilityRepository.class);
        BusinessFacilityController controller=new BusinessFacilityController(facilities,mock(StorageUnitRepository.class),mock(UserFacilityAssignmentRepository.class));
        BusinessFacilityRequest valid=new BusinessFacilityRequest("HCM_Q7_01","HCM_Q7","SafeBox Quận 7","123 Nguyễn Văn Linh, Quận 7",null,10.7,106.7,"INACTIVE");
        when(facilities.saveAndFlush(any(Facility.class))).thenAnswer(i->i.getArgument(0));
        Facility created=controller.create(valid);
        assertEquals("HCM_Q7_01",created.getFacilityCode());
        assertEquals("INACTIVE",created.getStatus());
        assertThrows(BusinessException.class,()->controller.create(new BusinessFacilityRequest("HCM_Q7_02","HCM_Q7","SafeBox Quận 7","123 Nguyễn Văn Linh, Quận 7",null,120.0,106.7,"ACTIVE")));
        when(facilities.existsByFacilityCodeIgnoreCase("HCM_Q7_01")).thenReturn(true);
        assertThrows(BusinessException.class,()->controller.create(valid));
        verify(facilities,times(1)).saveAndFlush(any());
    }
    @Test void cannotRemoveFinalActiveAdministrator() {
        UserRepository users=mock(UserRepository.class);
        Role adminRole=new Role();adminRole.setRoleName("SYSTEM_ADMIN");
        User admin=new User();admin.setUserId(UUID.randomUUID());admin.setRole(adminRole);admin.setStatus("ACTIVE");
        when(users.findById(admin.getUserId())).thenReturn(Optional.of(admin));
        when(users.lockActiveAdministrators()).thenReturn(List.of(admin));
        AdminAccessController controller=new AdminAccessController(users,mock(RoleRepository.class),mock(FacilityRepository.class),
            mock(UserFacilityAssignmentRepository.class),mock(StaffRegionAssignmentRepository.class),
            mock(ActivityLogRepository.class),mock(Sprint2Access.class),mock(EntityManager.class));
        assertThrows(BusinessException.class,()->controller.status(admin.getUserId(),new AdminAccessController.StatusChange("INACTIVE")));
        assertThrows(BusinessException.class,()->controller.role(admin.getUserId(),new AdminAccessController.RoleChange("CUSTOMER")));
        verify(users,never()).save(any());
    }
}
