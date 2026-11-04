package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.BigDecimal;
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
class EarlyReturnServiceTest {
    @Mock Sprint2Access access;
    @Mock TerminationRequestRepository requests;
    @Mock EarlyReturnPolicyRepository policies;
    @Mock EarlyReturnSettlementRepository settlements;
    @Mock HandoverRecordRepository handovers;
    @Mock InspectionReportRepository inspections;
    @Mock PaymentTransactionRepository payments;
    @Mock InvoiceRepository invoices;
    @Mock DepositRefundRepository refunds;
    @InjectMocks EarlyReturnService service;
    UUID contractId = UUID.randomUUID(), customerId = UUID.randomUUID(), managerId = UUID.randomUUID(), facilityId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026,10,23,10,0);

    RentalContract active() {
        RentalContract c = new RentalContract(); c.contractId = contractId; c.customerId = customerId;
        c.depositAmount = new BigDecimal("1000000"); c.status = "ACTIVE";
        c.endDate = now.toLocalDate().plusDays(30); return c;
    }
    @BeforeEach void clock() { lenient().when(access.now()).thenReturn(now); }

    @Test void validRequestPersistsAndDuplicateIsRejected() {
        RentalContract c = active();
        when(access.role()).thenReturn("CUSTOMER"); when(access.contract(contractId,true)).thenReturn(c);
        when(access.currentId()).thenReturn(customerId);
        when(requests.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        EarlyReturnService.RequestInput input = new EarlyReturnService.RequestInput(now.plusDays(2),"moving",null);
        TerminationRequest row = service.request(contractId,input);
        assertEquals("REQUESTED",row.status); assertEquals(customerId,row.requestedBy);
        assertEquals(BigDecimal.ZERO,row.refundAmount);
        when(requests.existsByContractIdAndTypeAndStatusIn(eq(contractId),eq("EARLY_RETURN"),any())).thenReturn(true);
        assertThrows(BusinessException.class,() -> service.request(contractId,input));
        verify(requests,times(1)).saveAndFlush(any());
    }
    @Test void inactiveAndNonEarlyContractsAreRejected() {
        RentalContract c = active(); when(access.role()).thenReturn("CUSTOMER");
        when(access.contract(contractId,true)).thenReturn(c);
        c.status = "RETURNED";
        assertThrows(BusinessException.class,() -> service.request(contractId,
                new EarlyReturnService.RequestInput(now.plusDays(2),"moving",null)));
        c.status = "ACTIVE";
        assertThrows(BusinessException.class,() -> service.request(contractId,
                new EarlyReturnService.RequestInput(now.plusDays(30),"moving",null)));
        verify(requests,never()).saveAndFlush(any());
    }
    TerminationRequest managerRow() {
        TerminationRequest r = new TerminationRequest(); r.id = UUID.randomUUID();
        r.contractId = contractId; r.type = "EARLY_RETURN"; r.status = "SETTLEMENT_APPROVED";
        r.requestedBy = customerId; r.scheduledReturnAt = now.plusDays(2); return r;
    }
    void managerAccess(TerminationRequest row) {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(requests.lockById(row.id)).thenReturn(Optional.of(row));
        when(access.contract(contractId,true)).thenReturn(active());
        when(access.facilities()).thenReturn(List.of(facilityId));
        when(access.facilityId(any())).thenReturn(facilityId);
    }
    @Test void missingPolicyRemainsPending() {
        TerminationRequest row = managerRow(); row.status = "REQUESTED";
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(requests.findById(row.id)).thenReturn(Optional.of(row));
        when(access.contract(contractId,false)).thenReturn(active());
        when(access.facilities()).thenReturn(List.of(facilityId));
        when(access.facilityId(any())).thenReturn(facilityId);
        assertEquals("PENDING_POLICY",service.settlement(row.id).status());
        verifyNoInteractions(payments,refunds);
    }
    @Test void refundRequiresInspectionAndRetryCreatesOneTransaction() {
        TerminationRequest row = managerRow(); managerAccess(row);
        EarlyReturnSettlement s = new EarlyReturnSettlement(); s.id=UUID.randomUUID(); s.terminationId=row.id;
        s.status="APPROVED"; s.depositRefundAmount=new BigDecimal("900000"); s.approvedBy=managerId;
        s.approvedAt=now;
        when(settlements.findByTerminationId(row.id)).thenReturn(Optional.of(s));
        assertThrows(BusinessException.class,() -> service.refund(row.id));
        verify(payments,never()).saveAndFlush(any());
        HandoverRecord h = new HandoverRecord(); h.setStatus("COMPLETED"); h.setActualAt(now);
        when(handovers.findByContractIdAndHandoverTypeOrderByScheduledAtDesc(contractId,"RETURN")).thenReturn(List.of(h));
        InspectionReport inspection = new InspectionReport();
        when(inspections.findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(contractId,"RETURN_INSPECTION"))
                .thenReturn(Optional.of(inspection));
        PaymentTransaction deposit = new PaymentTransaction(); deposit.transactionId=UUID.randomUUID();
        deposit.invoiceId=UUID.randomUUID(); deposit.amount=new BigDecimal("1000000");
        when(payments.successful(contractId,"DEPOSIT")).thenReturn(List.of(deposit));
        when(refunds.completedTotal(contractId)).thenReturn(BigDecimal.ZERO);
        when(access.currentId()).thenReturn(managerId);
        UUID savedTransactionId = UUID.randomUUID();
        when(payments.saveAndFlush(any())).thenAnswer(i -> {
            PaymentTransaction saved = new PaymentTransaction();
            saved.transactionId = savedTransactionId;
            return saved;
        });
        when(refunds.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(settlements.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals("REFUNDED",service.refund(row.id).status);
        assertEquals(savedTransactionId, s.refundTransactionId);
        assertEquals("REFUNDED",service.refund(row.id).status);
        verify(payments,times(1)).saveAndFlush(any());
        verify(refunds,times(1)).saveAndFlush(any());
    }
    @Test void configuredPolicyProducesAuditedDepositOnlySettlement() {
        TerminationRequest row = managerRow(); row.status = "REQUESTED"; managerAccess(row);
        EarlyReturnPolicy policy = new EarlyReturnPolicy(); policy.id=UUID.randomUUID();
        policy.version="approved-v1"; policy.depositRefundable=true; policy.inspectionDeductionAllowed=true;
        when(policies.applicable(facilityId,row.scheduledReturnAt.toLocalDate())).thenReturn(List.of(policy));
        HandoverRecord h = new HandoverRecord(); h.setStatus("COMPLETED"); h.setActualAt(now);
        h.setHandoverId(UUID.randomUUID());
        when(handovers.findByContractIdAndHandoverTypeOrderByScheduledAtDesc(contractId,"RETURN")).thenReturn(List.of(h));
        InspectionReport inspection = new InspectionReport(); inspection.setInspectionId(UUID.randomUUID());
        inspection.setInspectionDate(now.plusHours(1)); inspection.setEstimatedRepairCost(new BigDecimal("200000"));
        when(inspections.findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(contractId,"RETURN_INSPECTION"))
                .thenReturn(Optional.of(inspection));
        PaymentTransaction deposit = new PaymentTransaction(); deposit.amount=new BigDecimal("1000000");
        when(payments.successful(contractId,"DEPOSIT")).thenReturn(List.of(deposit));
        when(refunds.completedTotal(contractId)).thenReturn(BigDecimal.ZERO);
        when(payments.findByContractIdAndStatus(contractId,"SUCCESS")).thenReturn(List.of(deposit));
        when(invoices.findByContractId(contractId)).thenReturn(List.of());
        when(access.currentId()).thenReturn(managerId);
        when(settlements.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        EarlyReturnSettlement result=service.approve(row.id,
                new EarlyReturnService.ApprovalInput(new BigDecimal("100000"),"verified damage"));
        assertEquals(new BigDecimal("900000"),result.depositRefundAmount);
        assertEquals(BigDecimal.ZERO,result.unusedRentRefund);
        assertEquals("approved-v1",result.policyVersion);
        assertEquals("SETTLEMENT_APPROVED",row.status);
        verify(payments,never()).saveAndFlush(any());
    }
    @Test void managerOutsideFacilityIsDenied() {
        TerminationRequest row = managerRow();
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(requests.lockById(row.id)).thenReturn(Optional.of(row));
        when(access.contract(contractId,true)).thenReturn(active());
        when(access.facilities()).thenReturn(List.of());
        when(access.facilityId(any())).thenReturn(facilityId);
        assertThrows(BusinessException.class,() -> service.refund(row.id));
        verifyNoInteractions(payments);
    }
}
