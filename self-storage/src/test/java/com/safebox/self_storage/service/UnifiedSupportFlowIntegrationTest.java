package com.safebox.self_storage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:unified_support;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=01234567890123456789012345678901"
})
class UnifiedSupportFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired FacilityRepository facilities;
    @Autowired StorageUnitRepository units;
    @Autowired RentalContractRepository contracts;
    @Autowired SupportTicketRepository tickets;
    @Autowired SupportTicketReplyRepository replies;
    @Autowired JdbcTemplate db;

    @Test void customerManagerStaffCustomerShareOnePersistedTicket() throws Exception {
        Role customerRole = role(1, "CUSTOMER"), managerRole = role(2, "FACILITY_MANAGER"), staffRole = role(3, "FACILITY_STAFF");
        UUID customer = user(customerRole, "customer"), manager = user(managerRole, "manager");
        UUID staff = user(staffRole, "staff"), outsider = user(managerRole, "outsider");
        Facility facility = facility("A"), other = facility("B");
        assign(manager, facility.getFacilityId()); assign(staff, facility.getFacilityId());
        assign(outsider, other.getFacilityId());
        StorageUnit unit = new StorageUnit(); unit.setFacilityId(facility.getFacilityId());
        unit.setTypeId(1); unit.setUnitNumber("A-1"); unit.setStatus("OCCUPIED"); unit = units.saveAndFlush(unit);
        RentalContract contract = new RentalContract(); contract.contractNumber = "CT-" + UUID.randomUUID();
        contract.reservationId = UUID.randomUUID(); contract.customerId = customer; contract.unitId = unit.getUnitId();
        contract.startDate = LocalDate.now().minusMonths(1); contract.endDate = LocalDate.now().plusMonths(1);
        contract.monthlyRentalFee = new BigDecimal("1000000"); contract.depositAmount = BigDecimal.ZERO;
        contract.billingCycle = "MONTHLY"; contract.termsVersion = "v2";
        contract.status = "ACTIVE"; contract.createdAt = LocalDateTime.now();
        contract = contracts.saveAndFlush(contract);

        String payload = json.writeValueAsString(new java.util.LinkedHashMap<>(java.util.Map.of(
                "contractId", contract.contractId, "issueType", "ACCESS_ISSUE", "title", "Lock problem",
                "description", "Cannot open unit", "priority", "HIGH")));
        String response = mvc.perform(post("/api/support-tickets").with(authentication(auth(customer, "CUSTOMER")))
                .contentType("application/json").content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        UUID ticketId = UUID.fromString(json.readTree(response).get("ticketId").asText());
        assertEquals(1, tickets.count());

        mvc.perform(get("/api/support/queue").with(authentication(auth(manager, "FACILITY_MANAGER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].requestId").value(ticketId.toString()));
        mvc.perform(get("/api/support/queue").with(authentication(auth(outsider, "FACILITY_MANAGER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/support/{id}/assignment", ticketId)
                .with(authentication(auth(outsider, "FACILITY_MANAGER"))).contentType("application/json")
                .content("{\"staffId\":\"" + staff + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/support/{id}/assignment", ticketId)
                .with(authentication(auth(manager, "FACILITY_MANAGER"))).contentType("application/json")
                .content("{\"staffId\":\"" + staff + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedStaffId").value(staff.toString()));
        mvc.perform(get("/api/support/queue").with(authentication(auth(staff, "FACILITY_STAFF"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].requestId").value(ticketId.toString()));
        mvc.perform(put("/api/support/{id}/status", ticketId)
                .with(authentication(auth(staff, "FACILITY_STAFF"))).contentType("application/json")
                .content("{\"status\":\"IN_PROGRESS\",\"note\":\"Started\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/support/{id}/status", ticketId)
                .with(authentication(auth(staff, "FACILITY_STAFF"))).contentType("application/json")
                .content("{\"status\":\"RESOLVED\",\"note\":\"Lock repaired\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/support-tickets/{id}", ticketId)
                .with(authentication(auth(customer, "CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.replies.length()").value(2));
        assertEquals("RESOLVED", tickets.findById(ticketId).orElseThrow().getStatus());
        assertEquals(2, replies.findByTicketIdOrderByCreatedAtAsc(ticketId).size());

        mvc.perform(post("/api/support-tickets/{id}/replies", ticketId)
                .with(authentication(auth(customer, "CUSTOMER"))).contentType("application/json")
                .content("{\"content\":\"Thank you\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/support/{id}", ticketId)
                .with(authentication(auth(manager, "FACILITY_MANAGER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(3));

        String second = "{\"contractId\":\"" + contract.contractId
                + "\",\"requestType\":\"ACCESS\",\"title\":\"Second issue\","
                + "\"description\":\"Lock code failed\",\"priority\":\"HIGH\"}";
        mvc.perform(post("/api/support").with(authentication(auth(customer, "CUSTOMER")))
                .contentType("application/json").content(second))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/support-tickets/my").with(authentication(auth(customer, "CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertEquals(2, tickets.count());
    }

    private Role role(int id, String name) {
        Role role = new Role(); role.setRoleId(id); role.setRoleName(name); return roles.saveAndFlush(role);
    }
    private UUID user(Role role, String name) {
        UUID id = UUID.randomUUID(); User user = new User(); user.setUserId(id); user.setRole(role);
        user.setEmail(name + id + "@test.local"); user.setPasswordHash("unused");
        user.setFullName(name); user.setPhone("000"); user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now()); users.saveAndFlush(user); return id;
    }
    private Facility facility(String name) {
        Facility f = new Facility(); f.setName(name); f.setAddress(name + " street"); f.setStatus("ACTIVE");
        return facilities.saveAndFlush(f);
    }
    private void assign(UUID user, UUID facility) {
        db.update("INSERT INTO USER_FACILITY_ASSIGNMENTS (assignment_id,user_id,facility_id,start_date,status) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), user, facility, LocalDate.now().minusDays(1), "ACTIVE");
    }
    private UsernamePasswordAuthenticationToken auth(UUID id, String role) {
        return new UsernamePasswordAuthenticationToken(id, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
