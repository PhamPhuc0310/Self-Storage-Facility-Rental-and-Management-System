package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.repository.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties={"spring.datasource.url=jdbc:h2:mem:phase1_region;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"})
class ManagementPhase1RegionScopeTest {
    @Autowired UserRepository users; @Autowired RoleRepository roles; @Autowired FacilityRepository facilities;
    @Autowired StaffRegionAssignmentRepository regions; @Autowired UserFacilityAssignmentRepository assignments;
    @Test void regionalGrantAppliesOnlyToActiveStaffAndRevocationIsImmediate() {
        Role staffRole=new Role();staffRole.setRoleId(91);staffRole.setRoleName("FACILITY_STAFF");roles.saveAndFlush(staffRole);
        Role managerRole=new Role();managerRole.setRoleId(92);managerRole.setRoleName("FACILITY_MANAGER");roles.saveAndFlush(managerRole);
        User staff=user(staffRole),manager=user(managerRole);
        Facility hcm=new Facility();hcm.setName("SafeBox Q7");hcm.setAddress("123 Nguyen Van Linh");hcm.setStatus("ACTIVE");hcm.setRegionCode("HCM_Q7");facilities.saveAndFlush(hcm);
        Facility hanoi=new Facility();hanoi.setName("SafeBox HN");hanoi.setAddress("123 Tran Duy Hung");hanoi.setStatus("ACTIVE");hanoi.setRegionCode("HN_CG");facilities.saveAndFlush(hanoi);
        StaffRegionAssignment grant=new StaffRegionAssignment();grant.setAssignmentId(UUID.randomUUID());grant.setUserId(staff.getUserId());grant.setRegionCode("HCM_Q7");grant.setStatus("ACTIVE");grant.setAssignedBy(manager.getUserId());grant.setAssignedAt(LocalDateTime.now());regions.saveAndFlush(grant);
        assertEquals(java.util.List.of(hcm.getFacilityId()),assignments.findActiveFacilityIds(staff.getUserId(),LocalDate.now()));
        assertFalse(assignments.isUserAssignedToFacility(staff.getUserId(),hanoi.getFacilityId(),LocalDate.now()));
        grant.setStatus("INACTIVE");regions.saveAndFlush(grant);
        assertTrue(assignments.findActiveFacilityIds(staff.getUserId(),LocalDate.now()).isEmpty());
        grant.setUserId(manager.getUserId());grant.setStatus("ACTIVE");regions.saveAndFlush(grant);
        assertTrue(assignments.findActiveFacilityIds(manager.getUserId(),LocalDate.now()).isEmpty());
    }
    private User user(Role role){User u=new User();u.setUserId(UUID.randomUUID());u.setRole(role);u.setEmail(UUID.randomUUID()+"@test.invalid");u.setPasswordHash("hash");u.setFullName("Test User");u.setPhone("000");u.setStatus("ACTIVE");u.setCreatedAt(LocalDateTime.now());return users.saveAndFlush(u);}
}
