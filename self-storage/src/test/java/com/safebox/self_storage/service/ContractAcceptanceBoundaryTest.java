package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractAcceptanceBoundaryTest {
    final RentalContractRepository contracts=mock(RentalContractRepository.class);
    final UserRepository users=mock(UserRepository.class);
    final StorageUnitRepository units=mock(StorageUnitRepository.class);
    final UserFacilityAssignmentRepository assignments=mock(UserFacilityAssignmentRepository.class);
    final PaymentTransactionRepository transactions=mock(PaymentTransactionRepository.class);
    final InvoiceRepository invoices=mock(InvoiceRepository.class);
    final PickupAppointmentRepository pickups=mock(PickupAppointmentRepository.class);
    final HandoverRecordRepository handovers=mock(HandoverRecordRepository.class);
    final Sprint2Access access=new Sprint2Access(contracts,units,users,assignments);
    final PaymentService payments=new PaymentService(access,transactions,invoices,pickups,
            mock(DepositRefundRepository.class),mock(ActivityLogRepository.class));
    final UUID owner=UUID.randomUUID(),facility=UUID.randomUUID();
    final RentalContract contract=new RentalContract();

    @BeforeEach void setup() {
        contract.contractId=UUID.randomUUID(); contract.customerId=owner; contract.unitId=UUID.randomUUID();
        contract.status="CONFIRMED"; contract.termsVersion="v1";
        contract.monthlyRentalFee=BigDecimal.TEN; contract.depositAmount=BigDecimal.TEN;
        when(contracts.lockById(contract.contractId)).thenReturn(Optional.of(contract));
        StorageUnit unit=new StorageUnit(); unit.setFacilityId(facility);
        when(units.findById(contract.unitId)).thenReturn(Optional.of(unit));
    }
    void actor(UUID id,String roleName) {
        Role role=new Role(); role.setRoleName(roleName);
        User user=new User(); user.setUserId(id); user.setRole(role);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(assignments.findActiveFacilityIds(eq(id),any())).thenReturn(List.of(facility));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void unsignedCustomerCannotPayOrScheduleAndForeignCustomerStillGetsForbidden() {
        actor(owner,"CUSTOMER");
        var request=new PaymentRequest("DEPOSIT",BigDecimal.TEN,"CASH","test-only");
        assertEquals(409,assertThrows(BusinessException.class,()->payments.pay(contract.contractId,request)).status().value());
        var appointments=new AppointmentService(access,pickups,handovers,contracts,mock(FacilityRepository.class),
                units,users,assignments,invoices,payments);
        assertEquals(409,assertThrows(BusinessException.class,()->appointments.create(contract.contractId,null)).status().value());
        actor(UUID.randomUUID(),"CUSTOMER");
        assertEquals(403,assertThrows(BusinessException.class,()->payments.pay(contract.contractId,request)).status().value());
        verifyNoInteractions(transactions,invoices,pickups);
    }

    @Test void unsignedManagerCannotConfirmMoneyAssignPickupOrHandover() {
        actor(UUID.randomUUID(),"FACILITY_MANAGER");
        assertEquals(409,assertThrows(BusinessException.class,()->payments.confirmCash(contract.contractId,UUID.randomUUID())).status().value());
        var appointments=new AppointmentService(access,pickups,handovers,contracts,mock(FacilityRepository.class),
                units,users,assignments,invoices,payments);
        assertEquals(409,assertThrows(BusinessException.class,()->appointments.assign(contract.contractId,UUID.randomUUID(),null)).status().value());
        var handover=new HandoverService(access,pickups,handovers,mock(InspectionReportRepository.class),contracts,units,payments);
        assertEquals(409,assertThrows(BusinessException.class,()->handover.complete(contract.contractId,null)).status().value());
        verifyNoInteractions(transactions,invoices,pickups);
    }

    @Test void unsignedStaffCannotConfirmTransfer() {
        actor(UUID.randomUUID(),"FACILITY_STAFF");
        assertEquals(409,assertThrows(BusinessException.class,
                () -> payments.confirmTransfer(contract.contractId,UUID.randomUUID())).status().value());
        verifyNoInteractions(transactions,invoices);
    }

    @Test void unsignedPaidContractNeverAppearsReadyInEitherSummaryPath() {
        var paid=new PaymentTransaction(); paid.amount=BigDecimal.TEN;
        when(transactions.successful(contract.contractId,"DEPOSIT")).thenReturn(List.of(paid));
        when(transactions.successful(contract.contractId,"INITIAL_RENT")).thenReturn(List.of(paid));
        when(transactions.successfulTotals(anyCollection())).thenReturn(Arrays.asList(
                new Object[]{contract.contractId,"DEPOSIT",BigDecimal.TEN},
                new Object[]{contract.contractId,"INITIAL_RENT",BigDecimal.TEN}));
        assertFalse(payments.calculate(contract).readyForHandover());
        assertFalse(payments.balances(List.of(contract)).get(contract.contractId).readyForHandover());
        contract.customerSignedAt=LocalDateTime.now(Sprint2Access.ZONE); contract.customerSignature="Acceptance";
        assertTrue(payments.calculate(contract).readyForHandover());
        assertTrue(payments.balances(List.of(contract)).get(contract.contractId).readyForHandover());
    }
}
