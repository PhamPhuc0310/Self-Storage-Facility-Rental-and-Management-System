package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositRefundServiceTest {
    @Mock Sprint2Access access;
    @Mock DepositRefundRepository refunds;
    @Mock PaymentTransactionRepository payments;
    @Mock InvoiceRepository invoices;
    UUID contractId = UUID.randomUUID(), customerId = UUID.randomUUID(), facilityId = UUID.randomUUID();
    DepositRefundService service() { return new DepositRefundService(access, refunds, payments, invoices); }
    RentalContract contract() { RentalContract c = new RentalContract(); c.contractId = contractId; c.customerId = customerId; return c; }
    @Test void requestCannotExceedNetReceivedDeposit() {
        when(access.contract(contractId,true)).thenReturn(contract());
        when(access.role()).thenReturn("CUSTOMER");
        PaymentTransaction p = new PaymentTransaction(); p.transactionId = UUID.randomUUID(); p.amount = new BigDecimal("990000");
        when(payments.successful(contractId,"DEPOSIT")).thenReturn(List.of(p));
        when(refunds.completedTotal(contractId)).thenReturn(new BigDecimal("100000"));
        assertEquals(409, assertThrows(BusinessException.class, () -> service().request(contractId,
                new RefundRequest(new BigDecimal("900000"),"Hủy trước ngày nhận"))).status().value());
        verify(refunds, never()).saveAndFlush(any());
    }
    @Test void requestPreservesOriginalPaymentAndNeedsManagerReview() {
        RentalContract c = contract(); when(access.contract(contractId,true)).thenReturn(c);
        when(access.role()).thenReturn("CUSTOMER"); when(access.facilityId(c)).thenReturn(facilityId);
        when(access.now()).thenReturn(LocalDateTime.of(2026,10,13,10,0));
        PaymentTransaction p = new PaymentTransaction(); p.transactionId = UUID.randomUUID(); p.amount = new BigDecimal("990000"); p.status = "SUCCESS";
        when(payments.successful(contractId,"DEPOSIT")).thenReturn(List.of(p));
        when(refunds.completedTotal(contractId)).thenReturn(BigDecimal.ZERO);
        when(refunds.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        DepositRefund row = service().request(contractId,new RefundRequest(new BigDecimal("500000"),"Không tiếp tục thuê"));
        assertEquals("REQUESTED",row.status); assertEquals(p.transactionId,row.paymentReference);
        assertEquals("SUCCESS",p.status); assertNull(row.approvedAmount);
    }
    @Test void managerOutsideFacilityCannotReview() {
        UUID id = UUID.randomUUID(); DepositRefund row = new DepositRefund(); row.contractId = contractId; row.facilityId = facilityId;
        when(access.role()).thenReturn("FACILITY_MANAGER"); when(refunds.findById(id)).thenReturn(Optional.of(row));
        when(access.contract(contractId,true)).thenReturn(contract());
        when(access.facilities()).thenReturn(List.of());
        assertEquals(403, assertThrows(BusinessException.class, () -> service().review(id,
                new RefundReview("APPROVED",BigDecimal.ONE,"Duyệt"))).status().value());
    }
    @Test void completionReducesInvoiceBalanceWithoutChangingSuccessfulPayment() {
        UUID id = UUID.randomUUID(); DepositRefund row = new DepositRefund(); row.id = id; row.contractId = contractId;
        row.facilityId = facilityId; row.status = "APPROVED"; row.approvedAmount = new BigDecimal("400000");
        when(access.role()).thenReturn("FACILITY_MANAGER"); when(refunds.findById(id)).thenReturn(Optional.of(row));
        when(access.contract(contractId,true)).thenReturn(contract()); when(access.facilities()).thenReturn(List.of(facilityId));
        PaymentTransaction p = new PaymentTransaction(); p.amount = new BigDecimal("990000"); p.status = "SUCCESS";
        when(payments.successful(contractId,"DEPOSIT")).thenReturn(List.of(p));
        when(refunds.completedTotal(contractId)).thenReturn(BigDecimal.ZERO);
        Invoice invoice = new Invoice(); invoice.paidAmount = new BigDecimal("990000");
        when(invoices.findFirstByContractIdAndInvoiceType(contractId,"DEPOSIT")).thenReturn(Optional.of(invoice));
        when(refunds.saveAndFlush(row)).thenReturn(row);
        when(access.now()).thenReturn(LocalDateTime.of(2026,10,13,11,0));
        DepositRefund completed = service().complete(id,new RefundCompletion("BANK-REF-123"));
        assertEquals("COMPLETED",completed.status);
        assertEquals(new BigDecimal("590000"),invoice.paidAmount);
        assertEquals("PARTIAL",invoice.status);
        assertEquals("SUCCESS",p.status);
    }
}
