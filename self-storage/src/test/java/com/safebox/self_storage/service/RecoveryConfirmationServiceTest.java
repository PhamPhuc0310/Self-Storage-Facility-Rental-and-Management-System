package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryConfirmationServiceTest {
    @Mock RentalContractRepository contracts; @Mock StorageUnitRepository units; @Mock UserRepository users;
    @Mock UserFacilityAssignmentRepository scopes; @Mock HandoverRecordRepository handovers;
    @Mock RenewalRepository renewals; @Mock ActivityLogRepository logs;
    @InjectMocks RecoveryConfirmationService service;
    UUID managerId=UUID.randomUUID(), contractId=UUID.randomUUID(), unitId=UUID.randomUUID(), facilityId=UUID.randomUUID();
    RentalContract contract; StorageUnit unit; User manager; LocalDateTime now;
    @BeforeEach void setup() {
        now=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        contract=new RentalContract(); contract.setContractId(contractId); contract.setUnitId(unitId);
        contract.setStatus("ACTIVE"); contract.setEndDate(now.toLocalDate().minusDays(31));
        unit=new StorageUnit(); unit.setUnitId(unitId); unit.setFacilityId(facilityId); unit.setStatus("OCCUPIED"); unit.setReadinessVerified(true);
        Role role=new Role(); role.setRoleName("FACILITY_MANAGER"); manager=new User(); manager.setStatus("ACTIVE"); manager.setRole(role);
        when(contracts.lockById(contractId)).thenReturn(Optional.of(contract));
        when(units.lockById(unitId)).thenReturn(Optional.of(unit));
        when(users.findById(managerId)).thenReturn(Optional.of(manager));
    }
    RecoveryConfirmationService.Request request(boolean empty) {
        return new RecoveryConfirmationService.Request("BB-123", "Hồ sơ xử lý 123", now.minusMinutes(1), true, empty);
    }
    @Test void managerRecordsRecoveryAndKeepsUnitUnavailable() {
        when(scopes.isUserAssignedToFacility(eq(managerId),eq(facilityId),any())).thenReturn(true);
        var result=service.confirm(managerId,contractId,request(true));
        assertEquals("RETURNED",result.contractStatus()); assertEquals("MAINTENANCE",result.unitStatus());
        assertFalse(unit.isReadinessVerified()); verify(logs).save(any(ActivityLog.class));
        verify(renewals).findByContractIdAndStatusIn(contractId,List.of("PENDING","APPROVED"));
    }
    @Test void wrongFacilityDenied() {
        assertThrows(BusinessException.class,()->service.confirm(managerId,contractId,request(true)));
        verify(units,never()).save(any()); verify(logs,never()).save(any());
    }
    @Test void unitNotEmptyIsNeverReleased() {
        when(scopes.isUserAssignedToFacility(eq(managerId),eq(facilityId),any())).thenReturn(true);
        assertThrows(BusinessException.class,()->service.confirm(managerId,contractId,request(false)));
        assertEquals("OCCUPIED",unit.getStatus()); verify(logs,never()).save(any());
    }
    @Test void retryDoesNotRecordTwice() {
        when(scopes.isUserAssignedToFacility(eq(managerId),eq(facilityId),any())).thenReturn(true);
        when(logs.existsByActionTypeAndEntityId("CONFIRM_UNIT_RECOVERY",contractId)).thenReturn(true);
        assertTrue(service.confirm(managerId,contractId,request(true)).alreadyRecorded());
        verify(logs,never()).save(any()); verify(units,never()).save(any());
    }
    @Test void staffCannotConfirmEvenWhenAssigned() {
        manager.getRole().setRoleName("FACILITY_STAFF");
        assertThrows(BusinessException.class,()->service.confirm(managerId,contractId,request(true)));
        verify(logs,never()).save(any()); verify(units,never()).save(any());
    }
    @Test void recoveryBeforeThirtyFullDaysRejected() {
        when(scopes.isUserAssignedToFacility(eq(managerId),eq(facilityId),any())).thenReturn(true);
        contract.setEndDate(now.toLocalDate().minusDays(29));
        assertThrows(BusinessException.class,()->service.confirm(managerId,contractId,request(true)));
        assertEquals("OCCUPIED",unit.getStatus()); verify(logs,never()).save(any());
    }
}
