package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.dto.overdue.PayPenaltyRequest;
import com.safebox.self_storage.dto.overdue.AdjustPenaltyRequest;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.impl.OverdueServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverdueRemediationTest {
    @Mock RentalContractRepository contracts;
    @Mock ContractPenaltyRepository penalties;
    @Mock PenaltyAdjustmentRepository adjustments;
    @Mock RentalPolicyRepository policies;
    @Mock InvoiceRepository invoices;
    @Mock PaymentTransactionRepository payments;
    @Mock HandoverRecordRepository handovers;
    @Mock RenewalRepository renewals;
    @Mock StorageUnitRepository units;
    @Mock StorageUnitTypeRepository types;
    @Mock FacilityRepository facilities;
    @Mock UserRepository users;
    @Mock UserFacilityAssignmentRepository assignments;
    @InjectMocks OverdueServiceImpl service;

    final UUID managerId = UUID.randomUUID(), customerId = UUID.randomUUID();
    final UUID facilityId = UUID.randomUUID(), contractId = UUID.randomUUID(), unitId = UUID.randomUUID();
    final LocalDate feeDate = LocalDate.of(2026, 10, 27);
    RentalContract contract;

    @BeforeEach void setup() {
        contract = new RentalContract();
        contract.contractId = contractId; contract.customerId = customerId; contract.unitId = unitId;
        contract.contractNumber = "SB-CT-TEST"; contract.status = "ACTIVE";
        contract.endDate = feeDate.minusDays(2); contract.monthlyRentalFee = new BigDecimal("3000000");
    }

    void managerAndContract(boolean assigned) {
        Role role = new Role(); role.setRoleName("FACILITY_MANAGER");
        User manager = new User(); manager.setUserId(managerId); manager.setRole(role); manager.setStatus("ACTIVE");
        StorageUnit unit = new StorageUnit(); unit.setUnitId(unitId); unit.setFacilityId(facilityId);
        when(contracts.lockById(contractId)).thenReturn(Optional.of(contract));
        when(users.findById(managerId)).thenReturn(Optional.of(manager));
        when(units.findById(unitId)).thenReturn(Optional.of(unit));
        when(assignments.isUserAssignedToFacility(eq(managerId), eq(facilityId), any(LocalDate.class)))
                .thenReturn(assigned);
    }

    @Test void crossFacilityManagerCannotCreateInvoiceOrPenalty() {
        managerAndContract(false);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.calculateOverdueFeeForContract(managerId, contractId, feeDate)).status());
        verifyNoInteractions(invoices, penalties, policies);
    }

    @Test void batchWithoutFacilityUsesOnlyAssignedFacilities() {
        Role role = new Role(); role.setRoleName("FACILITY_MANAGER");
        User manager = new User(); manager.setRole(role); manager.setStatus("ACTIVE");
        when(users.findById(managerId)).thenReturn(Optional.of(manager));
        when(assignments.findActiveFacilityIds(eq(managerId), any(LocalDate.class))).thenReturn(List.of(facilityId));
        when(contracts.findByFacilityIds(List.of(facilityId))).thenReturn(List.of());
        assertEquals(0, service.batchCalculateOverdueFeesForFacility(managerId, null, feeDate));
        verify(contracts, never()).findAll();
    }

    @Test void managerCannotBatchOtherFacility() {
        Role role = new Role(); role.setRoleName("FACILITY_MANAGER");
        User manager = new User(); manager.setRole(role); manager.setStatus("ACTIVE");
        when(users.findById(managerId)).thenReturn(Optional.of(manager));
        when(assignments.findActiveFacilityIds(eq(managerId), any(LocalDate.class))).thenReturn(List.of(facilityId));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.batchCalculateOverdueFeesForFacility(managerId, UUID.randomUUID(), feeDate)).status());
        verify(contracts, never()).findAll();
    }

    @Test void missingApprovedPolicyCannotCreateInvoice() {
        managerAndContract(true);
        when(handovers.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "COMPLETED"))
                .thenReturn(Optional.empty());
        when(penalties.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(policies.findApplicableOverduePolicies(facilityId, feeDate)).thenReturn(List.of());
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.calculateOverdueFeeForContract(managerId, contractId, feeDate)).status());
        verify(invoices, never()).saveAndFlush(any());
    }

    @Test void overdueListUsesContractEndAndReturnInsteadOfPolicyOrUnpaidRenewal() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        contract.endDate = today.minusDays(2);
        Role role = new Role(); role.setRoleName("CUSTOMER");
        User customer = new User(); customer.setRole(role); customer.setStatus("ACTIVE");
        when(users.findById(customerId)).thenReturn(Optional.of(customer));
        when(contracts.findByCustomerIdOrderByStartDateDesc(customerId)).thenReturn(List.of(contract));
        var page = service.getCustomerOverdueContracts(customerId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertTrue(page.getContent().get(0).isOverdue());
        assertNull(page.getContent().get(0).graceDays());
        verify(renewals).findByContractIdAndStatusIn(contractId, List.of("PENDING", "APPROVED"));
        verify(invoices, never()).saveAndFlush(any());
        contract.endDate = today.plusDays(1);
        assertEquals(0, service.getCustomerOverdueContracts(customerId, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
        contract.endDate = today.minusDays(2);
        when(handovers.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "COMPLETED"))
                .thenReturn(Optional.of(new HandoverRecord()));
        assertEquals(0, service.getCustomerOverdueContracts(customerId, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
    }

    @Test void recoveryEscalatesAtThirtyFullCalendarDaysAndStopsAfterReturn() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        Role role = new Role(); role.setRoleName("CUSTOMER");
        User customer = new User(); customer.setRole(role); customer.setStatus("ACTIVE");
        when(users.findById(customerId)).thenReturn(Optional.of(customer));
        when(contracts.findByCustomerIdOrderByStartDateDesc(customerId)).thenReturn(List.of(contract));
        var page = org.springframework.data.domain.PageRequest.of(0, 10);
        contract.endDate = today.minusDays(29);
        assertFalse(service.getCustomerOverdueContracts(customerId, page).getContent().get(0).requiresRecovery());
        contract.endDate = today.minusDays(30);
        assertTrue(service.getCustomerOverdueContracts(customerId, page).getContent().get(0).requiresRecovery());
        contract.endDate = today.minusDays(31);
        assertTrue(service.getCustomerOverdueContracts(customerId, page).getContent().get(0).requiresRecovery());
        when(handovers.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "COMPLETED"))
                .thenReturn(Optional.of(new HandoverRecord()));
        assertTrue(service.getCustomerOverdueContracts(customerId, page).isEmpty());
    }

    @Test void otherCustomerCannotSubmitPenaltyPayment() {
        UUID otherCustomer = UUID.randomUUID();
        Role role = new Role(); role.setRoleName("CUSTOMER");
        User user = new User(); user.setRole(role); user.setStatus("ACTIVE");
        ContractPenalty penalty = new ContractPenalty(); penalty.setContractId(contractId);
        UUID penaltyId = UUID.randomUUID(); penalty.setPenaltyId(penaltyId);
        when(penalties.findById(penaltyId)).thenReturn(Optional.of(penalty));
        when(contracts.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(users.findById(otherCustomer)).thenReturn(Optional.of(user));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.payPenaltyDebt(otherCustomer, penaltyId,
                        new PayPenaltyRequest("BANK_TRANSFER", new BigDecimal("1000"), "key", null))).status());
        verifyNoInteractions(payments, invoices);
    }

    @Test void penaltyRetryMustMatchItsInvoiceAmountMethodAndReference() {
        Role role=new Role(); role.setRoleName("CUSTOMER");
        User customer=new User(); customer.setRole(role); customer.setStatus("ACTIVE");
        UUID penaltyId=UUID.randomUUID(), invoiceId=UUID.randomUUID();
        ContractPenalty penalty=new ContractPenalty(); penalty.setPenaltyId(penaltyId);
        penalty.setContractId(contractId); penalty.setInvoiceId(invoiceId);
        penalty.setPenaltyAmount(new BigDecimal("10000"));
        penalty.setOverdueDays(1); penalty.setBillableDays(1); penalty.setDailyRate(BigDecimal.TEN);
        penalty.setPenaltyRate(BigDecimal.ONE);
        when(penalties.findById(penaltyId)).thenReturn(Optional.of(penalty));
        when(contracts.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(users.findById(customerId)).thenReturn(Optional.of(customer));
        PaymentTransaction tx=new PaymentTransaction(); tx.contractId=contractId; tx.invoiceId=invoiceId;
        tx.paymentType="PENALTY"; tx.paymentMethod="BANK_TRANSFER";
        tx.amount=new BigDecimal("10000"); tx.referenceCode="TEST-ONLY";
        when(payments.findByCreatedByAndIdempotencyKey(customerId,"retry")).thenReturn(Optional.of(tx));
        var original=new PayPenaltyRequest("BANK_TRANSFER",new BigDecimal("10000.00"),"retry","TEST-ONLY");
        assertDoesNotThrow(() -> service.payPenaltyDebt(customerId,penaltyId,original));
        for(var changed:List.of(
                new PayPenaltyRequest("BANK_TRANSFER",new BigDecimal("9000"),"retry","TEST-ONLY"),
                new PayPenaltyRequest("MOMO",new BigDecimal("10000"),"retry","TEST-ONLY"),
                new PayPenaltyRequest("BANK_TRANSFER",new BigDecimal("10000"),"retry","CHANGED")))
            assertEquals(HttpStatus.CONFLICT,assertThrows(BusinessException.class,
                    () -> service.payPenaltyDebt(customerId,penaltyId,changed)).status());
        tx.invoiceId=UUID.randomUUID();
        assertEquals(HttpStatus.CONFLICT,assertThrows(BusinessException.class,
                () -> service.payPenaltyDebt(customerId,penaltyId,original)).status());
        verify(payments,never()).save(any()); verify(invoices,never()).save(any());
    }

    @Test void crossFacilityManagerCannotModifyOrCollectExistingPenalty() {
        Role role = new Role(); role.setRoleName("FACILITY_MANAGER");
        User manager = new User(); manager.setRole(role); manager.setStatus("ACTIVE");
        StorageUnit unit = new StorageUnit(); unit.setUnitId(unitId); unit.setFacilityId(facilityId);
        UUID penaltyId = UUID.randomUUID(); ContractPenalty penalty = new ContractPenalty();
        penalty.setPenaltyId(penaltyId); penalty.setContractId(contractId);
        when(penalties.findById(penaltyId)).thenReturn(Optional.of(penalty));
        when(contracts.findByContractId(contractId)).thenReturn(Optional.of(contract));
        when(users.findById(managerId)).thenReturn(Optional.of(manager));
        when(units.findById(unitId)).thenReturn(Optional.of(unit));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.adjustPenaltyDebt(managerId, penaltyId,
                        new AdjustPenaltyRequest(BigDecimal.ZERO, "WAIVER", "reason", null))).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.approvePenaltyPayment(managerId, penaltyId, true, "note")).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.payPenaltyDebt(managerId, penaltyId,
                        new PayPenaltyRequest("CASH", new BigDecimal("1000"), "key", null))).status());
        verifyNoInteractions(payments, invoices, adjustments);
    }

    @Test void overlappingApprovedPoliciesAreRejected() {
        managerAndContract(true);
        when(handovers.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "COMPLETED"))
                .thenReturn(Optional.empty());
        RentalPolicy a = validPolicy(), b = validPolicy();
        when(policies.findApplicableOverduePolicies(facilityId, feeDate)).thenReturn(List.of(a, b));
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.calculateOverdueFeeForContract(managerId, contractId, feeDate)).status());
        verify(invoices, never()).saveAndFlush(any());
    }

    @Test void authorizedManagerUsesPolicyAndRetryDoesNotCreateDuplicate() {
        managerAndContract(true);
        when(handovers.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "COMPLETED"))
                .thenReturn(Optional.empty());
        RentalPolicy policy = validPolicy();
        when(policies.findApplicableOverduePolicies(facilityId, feeDate)).thenReturn(List.of(policy));
        AtomicReference<ContractPenalty> saved = new AtomicReference<>();
        when(penalties.findByIdempotencyKey(anyString())).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(adjustments.findByPenaltyIdOrderByAdjustedAtDesc(any())).thenReturn(List.of());
        when(invoices.saveAndFlush(any())).thenAnswer(call -> {
            Invoice invoice = call.getArgument(0); invoice.invoiceId = UUID.randomUUID(); return invoice;
        });
        when(penalties.saveAndFlush(any())).thenAnswer(call -> {
            ContractPenalty penalty = call.getArgument(0); penalty.setPenaltyId(UUID.randomUUID());
            saved.set(penalty); return penalty;
        });
        var first = service.calculateOverdueFeeForContract(managerId, contractId, feeDate);
        assertNotNull(first);
        assertEquals(0, first.penaltyAmount().compareTo(new BigDecimal("10000")));
        var retry = service.calculateOverdueFeeForContract(managerId, contractId, feeDate);
        assertEquals(first.penaltyId(), retry.penaltyId());
        verify(penalties).saveAndFlush(argThat(p -> policy.getPolicyId().equals(p.getPolicyId())));
        verify(invoices).saveAndFlush(any());
        verify(contracts, times(2)).lockById(contractId);
    }

    @Test void laterPeriodBillsOnlyNewDays() {
        managerAndContract(true);
        when(handovers.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "COMPLETED"))
                .thenReturn(Optional.empty());
        when(policies.findApplicableOverduePolicies(facilityId, feeDate)).thenReturn(List.of(validPolicy()));
        ContractPenalty previous = new ContractPenalty();
        previous.setPeriodDate(feeDate.minusDays(1));
        when(penalties.findByContractIdOrderByPeriodDateDesc(contractId)).thenReturn(List.of(previous));
        when(adjustments.findByPenaltyIdOrderByAdjustedAtDesc(any())).thenReturn(List.of());
        when(invoices.saveAndFlush(any())).thenAnswer(call -> {
            Invoice invoice = call.getArgument(0); invoice.invoiceId = UUID.randomUUID(); return invoice;
        });
        when(penalties.saveAndFlush(any())).thenAnswer(call -> {
            ContractPenalty penalty = call.getArgument(0); penalty.setPenaltyId(UUID.randomUUID()); return penalty;
        });

        var result = service.calculateOverdueFeeForContract(managerId, contractId, feeDate);

        assertEquals(1, result.billableDays());
        assertEquals(0, result.penaltyAmount().compareTo(new BigDecimal("5000")));
        verify(invoices).saveAndFlush(argThat(invoice ->
                invoice.totalAmount.compareTo(new BigDecimal("5000")) == 0));
    }

    RentalPolicy validPolicy() {
        RentalPolicy p = new RentalPolicy(); p.setPolicyId(UUID.randomUUID());
        p.setFacilityId(facilityId); p.setStatus("ACTIVE");
        p.setEffectiveFrom(feeDate.minusDays(1)); p.setEffectiveTo(feeDate);
        p.setOverdueApprovedAt(feeDate.atStartOfDay()); p.setOverdueApprovedBy(managerId);
        p.setOverdueGraceDays(0); p.setPenaltyType("PERCENT_DAILY");
        p.setDailyPenaltyRate(new BigDecimal("0.05")); return p;
    }
    void calculationSaves() {
        when(invoices.saveAndFlush(any())).thenAnswer(call->{Invoice i=call.getArgument(0);i.invoiceId=UUID.randomUUID();return i;});
        when(penalties.saveAndFlush(any())).thenAnswer(call->{ContractPenalty p=call.getArgument(0);p.setPenaltyId(UUID.randomUUID());return p;});
    }
    @Test void gracePeriodCreatesZeroPaidPeriodInsteadOf409AndDoesNotHideFuturePeriod() {
        managerAndContract(true);var p=validPolicy();p.setOverdueGraceDays(3);
        when(policies.findApplicableOverduePolicies(facilityId,feeDate)).thenReturn(List.of(p));calculationSaves();
        var result=service.calculateOverdueFeeForContract(managerId,contractId,feeDate);
        assertEquals(0,result.billableDays());assertEquals(0,result.penaltyAmount().signum());assertEquals("PAID",result.status());
        var future=new ContractPenalty();future.setPeriodDate(feeDate.plusDays(1));
        when(penalties.findByContractIdOrderByPeriodDateDesc(contractId)).thenReturn(List.of(future));
        assertEquals(HttpStatus.CONFLICT,assertThrows(BusinessException.class,()->service.calculateOverdueFeeForContract(managerId,contractId,feeDate)).status());
    }
    @Test void newGlobalDoesNotChargeDaysBeforeEffectiveFromAndUsesFixedFormula() {
        managerAndContract(true);contract.endDate=feeDate.minusDays(10);var global=validPolicy();
        global.setFacilityId(null);global.setEffectiveFrom(feeDate);global.setPenaltyType("FIXED_DAILY");global.setFixedPenaltyAmount(new BigDecimal("17000"));
        when(policies.findApplicableOverduePolicies(facilityId,feeDate)).thenReturn(List.of(global));calculationSaves();
        var result=service.calculateOverdueFeeForContract(managerId,contractId,feeDate);
        assertEquals(1,result.billableDays());assertEquals(0,new BigDecimal("17000").compareTo(result.penaltyAmount()));
        assertTrue(result.calculationExplanation().contains("17000 đ/ngày"));
    }
    @Test void displayedBillableDaysAlsoRespectCurrentPolicyEffectiveDate() {
        LocalDate today=LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));contract.endDate=today.minusDays(10);
        Role role=new Role();role.setRoleName("CUSTOMER");User customer=new User();customer.setRole(role);customer.setStatus("ACTIVE");
        StorageUnit unit=new StorageUnit();unit.setFacilityId(facilityId);Facility facility=new Facility();facility.setFacilityId(facilityId);
        when(users.findById(customerId)).thenReturn(Optional.of(customer));when(units.findById(unitId)).thenReturn(Optional.of(unit));
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));
        when(contracts.findByCustomerIdOrderByStartDateDesc(customerId)).thenReturn(List.of(contract));
        RentalPolicy global=validPolicy();global.setFacilityId(null);global.setEffectiveFrom(today);global.setEffectiveTo(null);
        when(policies.findApplicableOverduePolicies(facilityId,today)).thenReturn(List.of(global));
        var row=service.getCustomerOverdueContracts(customerId,org.springframework.data.domain.PageRequest.of(0,10)).getContent().get(0);
        assertEquals(10,row.overdueDays());assertEquals(1,row.billableDays());
    }
    @Test void uncalculatedOldVersionMustBeBilledFirstAndLocalWinsOverGlobal() {
        managerAndContract(true);contract.endDate=feeDate.minusDays(10);
        var global=validPolicy();global.setFacilityId(null);global.setEffectiveFrom(feeDate.minusDays(1));
        var old=validPolicy();old.setFacilityId(null);old.setEffectiveFrom(feeDate.minusDays(20));old.setEffectiveTo(feeDate.minusDays(2));
        when(policies.findApplicableOverduePolicies(facilityId,feeDate)).thenReturn(List.of(global));
        doReturn(List.of(old,global)).when(policies).findByFacilityId(null);
        doReturn(List.of()).when(policies).findByFacilityId(facilityId);
        assertEquals(HttpStatus.CONFLICT,assertThrows(BusinessException.class,()->service.calculateOverdueFeeForContract(managerId,contractId,feeDate)).status());
        verify(invoices,never()).saveAndFlush(any());
        var local=validPolicy();local.setEffectiveFrom(feeDate.minusDays(20));local.setDailyPenaltyRate(new BigDecimal("0.02"));
        when(policies.findByFacilityId(facilityId)).thenReturn(List.of(local));
        when(policies.findApplicableOverduePolicies(facilityId,feeDate)).thenReturn(List.of(global,local));calculationSaves();
        var result=service.calculateOverdueFeeForContract(managerId,contractId,feeDate);
        assertEquals(0,new BigDecimal("20000").compareTo(result.penaltyAmount()));
    }
}
