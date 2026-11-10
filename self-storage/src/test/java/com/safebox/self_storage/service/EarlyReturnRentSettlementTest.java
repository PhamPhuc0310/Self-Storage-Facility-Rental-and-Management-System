package com.safebox.self_storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.exception.BusinessException;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EarlyReturnRentSettlementTest {
    @Mock InvoiceRepository invoices; @Mock PaymentTransactionRepository payments;
    @Mock RenewalRepository renewals; @Mock DepositRefundRepository refunds; @Mock Sprint2Access access;
    EarlyReturnRentSettlement service;
    RentalContract contract;EarlyReturnPolicy policy;
    List<Invoice> bills;List<PaymentTransaction> transactions;
    LocalDate returned=LocalDate.of(2026,11,12);
    BigDecimal amount(String value){return new BigDecimal(value);}
    @BeforeEach void setup() {
        service=new EarlyReturnRentSettlement(invoices,payments,renewals,refunds,access,new ObjectMapper().findAndRegisterModules());
        contract=new RentalContract();contract.contractId=UUID.randomUUID();contract.customerId=UUID.randomUUID();
        contract.startDate=LocalDate.of(2026,10,1);contract.endDate=LocalDate.of(2026,12,31);contract.termsVersion="v2";
        contract.depositAmount=amount("1000");policy=new EarlyReturnPolicy();policy.rentalRefundPercent=amount("80");policy.depositRefundable=true;
        bills=new ArrayList<>();transactions=new ArrayList<>();
        lenient().when(invoices.findByContractId(contract.contractId)).thenAnswer(x->bills);
        lenient().when(payments.findByContractIdAndStatus(contract.contractId,"SUCCESS")).thenAnswer(x->transactions);
        lenient().when(refunds.completedTotal(contract.contractId)).thenReturn(BigDecimal.ZERO);
    }
    Invoice bill(String type,String total,String paid) {
        Invoice i=new Invoice();i.invoiceId=UUID.randomUUID();i.contractId=contract.contractId;i.invoiceType=type;
        i.totalAmount=amount(total);i.paidAmount=amount(paid);i.status=i.totalAmount.equals(i.paidAmount)?"PAID":"PARTIALLY_PAID";
        bills.add(i);return i;
    }
    PaymentTransaction receipt(Invoice i,String paid) {
        PaymentTransaction p=new PaymentTransaction();p.transactionId=UUID.randomUUID();p.invoiceId=i.invoiceId;p.contractId=contract.contractId;
        p.paymentType=i.invoiceType;p.amount=amount(paid);p.transactionType="PAYMENT";p.status="SUCCESS";
        p.confirmedBy=UUID.randomUUID();p.confirmedAt=returned.atTime(8,0);transactions.add(p);return p;
    }
    EarlyReturnRentSettlement.Calculation quote(){return service.calculate(contract,returned,false,policy,BigDecimal.ZERO,returned.atTime(10,0));}
    @Test void actualCalendarDaysAndEightyPercentAreRoundedOncePerPeriod() {
        var r=EarlyReturnRentSettlement.prorate(UUID.randomUUID(),LocalDate.of(2028,2,1),LocalDate.of(2028,2,29),amount("2900"),LocalDate.of(2028,2,19),amount("80"));
        assertEquals(29,r.totalDays());assertEquals(10,r.unusedDays());assertEquals(amount("1000.00"),r.unusedRent());
        assertEquals(amount("800.00"),r.rentalRefund());assertEquals(amount("200.00"),r.retainedRent());
    }
    @Test void actualDateRatherThanRequestedDateDeterminesUnusedDays() {
        receipt(bill("REMAINING_RENT","9200","9200"),"9200");
        assertEquals(49,quote().rentPeriods().get(0).unusedDays());
        assertEquals(amount("3920.00"),quote().rentalRefund());
        assertEquals(contract.endDate,LocalDate.of(2026,12,31));
    }
    @Test void noUnusedPeriodHasNoRentalRefund() {
        receipt(bill("REMAINING_RENT","9200","9200"),"9200");returned=contract.endDate;
        assertEquals(0,quote().rentalRefund().signum());returned=contract.endDate.plusDays(2);
        assertEquals(0,quote().rentalRefund().signum());
    }
    @Test void unconfirmedAndUnpaidRentAreExcluded() {
        PaymentTransaction p=receipt(bill("REMAINING_RENT","9200","9200"),"9200");p.confirmedAt=null;
        bill("RENEWAL","1000","0");assertEquals(0,quote().eligiblePrepaidRent().signum());
        assertEquals(0,quote().netRefund().signum());
    }
    @Test void partialPaymentUsesOnlyConfirmedPaidPortionAndOffsetsUnpaidBalanceOnce() {
        receipt(bill("REMAINING_RENT","9200","4600"),"4600");
        var q=quote();assertEquals(amount("4600.00"),q.eligiblePrepaidRent());
        assertEquals(amount("1960.00"),q.rentalRefund());assertEquals(amount("4600.00"),q.outstandingDebt());
        assertEquals(amount("2640.00"),q.remainingDebt());assertEquals(0,q.netRefund().signum());
    }
    @Test void moneyConfirmedAfterActualHandoverIsNotPrepaidRentalEligibility() {
        PaymentTransaction p=receipt(bill("REMAINING_RENT","9200","9200"),"9200");p.confirmedAt=returned.atTime(10,1);
        assertEquals(0,quote().eligiblePrepaidRent().signum());
    }
    @Test void depositIsSeparateAndDoesNotEnterRentBasis() {
        receipt(bill("DEPOSIT","1000","1000"),"1000");var q=quote();
        assertEquals(amount("1000.00"),q.netRefund());assertEquals(0,q.eligiblePrepaidRent().signum());
    }
    @Test void invoicedDamageAndCleaningReduceDepositOnceWithoutTouchingHistoricalRent() {
        receipt(bill("DEPOSIT","1000","1000"),"1000");bill("OTHER","300","0");
        var q=quote();assertEquals(amount("700.00"),q.netRefund());assertEquals(amount("300.00"),q.liabilityOffset());
        assertEquals(amount("0"),bills.get(1).paidAmount);
        assertThrows(BusinessException.class,()->service.addInspectionCharge(contract,UUID.randomUUID(),amount("300"),"same damage"));
    }
    @Test void liabilitiesGreaterThanRefundAreRecordedAsDebtNeverNegativeRefund() {
        receipt(bill("DEPOSIT","1000","1000"),"1000");bill("OTHER","2500","0");
        var q=quote();assertEquals(0,q.netRefund().signum());assertEquals(amount("1500.00"),q.remainingDebt());
    }
    @Test void completedRenewalHasIndependentCalendarPeriodAndOriginalEndIsPreserved() {
        receipt(bill("REMAINING_RENT","9200","9200"),"9200");
        Renewal r=new Renewal();r.setRenewalId(UUID.randomUUID());r.setOldEndDate(contract.endDate);
        r.setNewEndDate(LocalDate.of(2027,3,31));contract.endDate=r.getNewEndDate();
        when(renewals.findByContractIdAndStatus(contract.contractId,"COMPLETED")).thenReturn(List.of(r));
        Invoice renewed=bill("RENEWAL","9000","9000");renewed.renewalId=r.getRenewalId();receipt(renewed,"9000");
        var q=quote();assertEquals(amount("11120.00"),q.rentalRefund());
        assertEquals(LocalDate.of(2027,3,31),contract.endDate);
        assertEquals(92,q.rentPeriods().stream().filter(p->p.end().getYear()==2026).findFirst().orElseThrow().totalDays());
    }
    @Test void ambiguousLegacyPaidPeriodsFailClosedRatherThanInventingEligibility() {
        contract.termsVersion="v1";receipt(bill("INITIAL_RENT","1000","1000"),"1000");
        assertThrows(BusinessException.class,this::quote);
    }
    @Test void pendingRenewalMustBeResolvedInsteadOfBeingPaidByReturnOffsets() {
        when(renewals.findByContractIdAndStatusIn(eq(contract.contractId),any())).thenReturn(List.of(new Renewal()));
        assertThrows(BusinessException.class,this::quote);verify(invoices,never()).save(any());
    }
    @Test void noConfirmationCannotCreateRefund() {
        receipt(bill("DEPOSIT","1000","1000"),"1000");var s=new EarlyReturnSettlement();s.calculationSnapshot=service.snapshot(quote());
        assertThrows(BusinessException.class,()->service.complete(contract,new TerminationRequest(),s,null));
        verify(payments,never()).saveAndFlush(any());
    }
    @Test void completionRetryBindsEvidenceAndDoesNotDuplicateRefundOrOffsets() {
        Invoice rent=bill("REMAINING_RENT","9200","9200");receipt(rent,"9200");
        Invoice debt=bill("OTHER","300","0");var q=quote();
        var s=new EarlyReturnSettlement();s.id=UUID.randomUUID();s.status="APPROVED";s.priorDepositRefunds=BigDecimal.ZERO;
        s.calculationSnapshot=service.snapshot(q);var row=new TerminationRequest();
        when(invoices.findById(debt.invoiceId)).thenReturn(Optional.of(debt));
        when(access.now()).thenReturn(returned.atTime(10,0));when(access.currentId()).thenReturn(UUID.randomUUID());
        when(payments.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        var confirmation=new EarlyReturnRentSettlement.Confirmation("TEST-ONLY-REFUND-80","CASH",q.netRefund());
        service.complete(contract,row,s,confirmation);service.complete(contract,row,s,confirmation);
        assertEquals("COMPLETED",s.status);assertEquals("REFUNDED",row.status);assertEquals(amount("300"),debt.paidAmount);
        verify(payments,times(1)).saveAndFlush(any());verify(invoices,times(1)).save(debt);
        assertThrows(BusinessException.class,()->service.complete(contract,row,s,
                new EarlyReturnRentSettlement.Confirmation("CHANGED","CASH",q.netRefund())));
    }
}
