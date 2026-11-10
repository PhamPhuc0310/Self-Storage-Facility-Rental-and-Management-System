package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Sprint2AccessTest {
    @Mock RentalContractRepository contracts;
    @Mock StorageUnitRepository units;
    @Mock UserRepository users;
    @Mock UserFacilityAssignmentRepository assignments;
    UUID actor = UUID.randomUUID(), contractId = UUID.randomUUID(), facilityId = UUID.randomUUID();
    Sprint2Access access;
    RentalContract contract;

    @BeforeEach void setup() {
        access = new Sprint2Access(contracts, units, users, assignments);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null));
        contract = new RentalContract(); contract.contractId = contractId; contract.customerId = UUID.randomUUID(); contract.unitId = UUID.randomUUID();
        lenient().when(contracts.findById(contractId)).thenReturn(Optional.of(contract));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void role(String name) {
        Role role = new Role(); role.setRoleName(name);
        User user = new User(); user.setUserId(actor); user.setRole(role);
        when(users.findById(actor)).thenReturn(Optional.of(user));
    }
    @Test void customerCannotReadSomeoneElsesContract() {
        role("CUSTOMER");
        assertEquals(403, assertThrows(BusinessException.class, () -> access.contract(contractId,false)).status().value());
    }
    @Test void staffCannotReadForeignFacility() {
        role("FACILITY_STAFF");
        StorageUnit unit = new StorageUnit(); unit.setFacilityId(facilityId);
        when(units.findById(contract.unitId)).thenReturn(Optional.of(unit));
        when(assignments.findActiveFacilityIds(eq(actor),any())).thenReturn(List.of());
        assertEquals(403, assertThrows(BusinessException.class, () -> access.contract(contractId,false)).status().value());
    }
    @Test void managerCanReadOwnFacility() {
        role("FACILITY_MANAGER");
        StorageUnit unit = new StorageUnit(); unit.setFacilityId(facilityId);
        when(units.findById(contract.unitId)).thenReturn(Optional.of(unit));
        when(assignments.findActiveFacilityIds(eq(actor),any())).thenReturn(List.of(facilityId));
        assertSame(contract, access.contract(contractId,false));
    }

    @Test void handoverEligibilityRequiresBothAcceptanceFieldsAndValidLifecycle() {
        // This test exercises the real shared guard, not a mocked acceptance check.
        contract.status="CONFIRMED";
        assertEquals(409, assertThrows(BusinessException.class, () -> access.confirmed(contract)).status().value());
        contract.customerSignedAt=java.time.LocalDateTime.now(Sprint2Access.ZONE);
        contract.customerSignature=" ";
        assertThrows(BusinessException.class, () -> access.confirmed(contract));
        contract.customerSignature="Customer acceptance";
        assertDoesNotThrow(() -> access.confirmed(contract));
        contract.status="PENDING_HANDOVER";
        assertDoesNotThrow(() -> access.confirmed(contract));
        contract.customerSignedAt=null;
        assertThrows(BusinessException.class, () -> access.confirmed(contract));
        contract.customerSignedAt=java.time.LocalDateTime.now(Sprint2Access.ZONE);
        for(String status:List.of("PENDING_CONFIRMATION","ACTIVE","CANCELLED","OVERDUE")) {
            contract.status=status;
            assertThrows(BusinessException.class, () -> access.confirmed(contract));
        }
    }
}
