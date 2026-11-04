package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.PaymentRequest;
import com.safebox.self_storage.entity.Invoice;
import com.safebox.self_storage.entity.ActivityLog;
import com.safebox.self_storage.entity.PaymentTransaction;
import com.safebox.self_storage.entity.PickupAppointment;
import com.safebox.self_storage.entity.RentalContract;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class Phase46PaymentServiceTest {
    private final Sprint2Access access = mock(Sprint2Access.class);
    private final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PickupAppointmentRepository appointments = mock(PickupAppointmentRepository.class);
    private final DepositRefundRepository refunds = mock(DepositRefundRepository.class);
    private final ActivityLogRepository activityLogs = mock(ActivityLogRepository.class);
    private final PaymentService service = new PaymentService(access, payments, invoices, appointments, refunds, activityLogs);
    private final UUID contractId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    private RentalContract contract() {
        RentalContract r = new RentalContract();
        r.contractId = contractId; r.customerId = customerId; r.status = "CONFIRMED";
        r.startDate = LocalDate.of(2026, 10, 29); r.endDate = r.startDate.plusMonths(1);
        r.monthlyRentalFee = new BigDecimal("500000"); r.depositAmount = new BigDecimal("500000");
        return r;
    }
    private Invoice invoice() {
        Invoice i = new Invoice(); i.invoiceId = UUID.randomUUID(); i.contractId = contractId;
        i.invoiceType = "DEPOSIT"; i.totalAmount = new BigDecimal("500000");
        i.paidAmount = BigDecimal.ZERO; i.status = "UNPAID";
        return i;
    }

    @Test
    void customerCashRequestRemainsPendingAndDoesNotSettleInvoice() {
        RentalContract r = contract(); Invoice i = invoice();
        when(access.contract(contractId, true)).thenReturn(r);
        when(access.role()).thenReturn("CUSTOMER"); when(access.currentId()).thenReturn(customerId);
        when(access.today()).thenReturn(r.startDate);
        when(access.now()).thenReturn(LocalDateTime.of(2026, 10, 29, 9, 0));
        when(invoices.findFirstByContractIdAndInvoiceType(contractId, "DEPOSIT")).thenReturn(Optional.of(i));
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> {
            PaymentTransaction p = invocation.getArgument(0);
            p.transactionId = UUID.randomUUID(); return p;
        });
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentTransaction p = service.pay(contractId,
                new PaymentRequest("DEPOSIT", i.totalAmount, "CASH", "cash-request-1"));
        assertEquals("PENDING", p.status);
        assertEquals("UNPAID", i.status);
        assertEquals(BigDecimal.ZERO, i.paidAmount);
        assertNull(p.confirmedBy);
        verify(invoices, never()).save(any());
        verify(activityLogs, never()).saveAndFlush(any());
    }

    @Test
    void onlyFacilityManagerCanConfirmCashDepositAndRetryDoesNotSettleTwice() {
        RentalContract r = contract(); Invoice i = invoice();
        PaymentTransaction p = new PaymentTransaction();
        p.transactionId = UUID.randomUUID(); p.contractId = contractId;
        p.invoiceId = i.invoiceId; p.paymentMethod = "CASH"; p.paymentType = "DEPOSIT";
        p.amount = i.totalAmount; p.status = "PENDING";
        when(access.contract(contractId, true)).thenReturn(r);
        when(access.role()).thenReturn("CUSTOMER", "FACILITY_STAFF", "FACILITY_MANAGER", "FACILITY_MANAGER");
        when(payments.findById(p.transactionId)).thenReturn(Optional.of(p));
        when(invoices.findById(i.invoiceId)).thenReturn(Optional.of(i));
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(access.currentId()).thenReturn(staffId);
        when(access.now()).thenReturn(LocalDateTime.of(2026, 10, 29, 10, 0));

        BusinessException denied = assertThrows(BusinessException.class,
                () -> service.confirmCash(contractId, p.transactionId));
        assertEquals(HttpStatus.FORBIDDEN, denied.status());
        assertEquals("PENDING", p.status);
        verify(activityLogs, never()).saveAndFlush(any());

        BusinessException staffDenied = assertThrows(BusinessException.class,
                () -> service.confirmCash(contractId, p.transactionId));
        assertEquals(HttpStatus.FORBIDDEN, staffDenied.status());
        assertEquals("PENDING", p.status);
        verify(activityLogs, never()).saveAndFlush(any());

        PaymentTransaction confirmed = service.confirmCash(contractId, p.transactionId);
        assertEquals("SUCCESS", confirmed.status);
        assertEquals(staffId, confirmed.confirmedBy);
        assertNotNull(confirmed.confirmedAt);
        assertEquals("PAID", i.status);
        assertEquals(i.totalAmount, i.paidAmount);
        var log = org.mockito.ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogs, times(1)).saveAndFlush(log.capture());
        assertEquals(staffId, log.getValue().getUserId());
        assertEquals("CONFIRM_CASH_PAYMENT", log.getValue().getActionType());
        assertEquals(p.transactionId, log.getValue().getEntityId());
        assertTrue(log.getValue().getDescription().contains("role=FACILITY_MANAGER;result=SUCCESS"));
        assertTrue(log.getValue().getDescription().contains("invoiceId=" + i.invoiceId));

        service.confirmCash(contractId, p.transactionId);
        assertEquals(i.totalAmount, i.paidAmount);
        verify(invoices, times(1)).save(i);
        verify(activityLogs, times(1)).saveAndFlush(any());
    }

    @Test
    void onlyAssignedStaffCanConfirmCashRemainingRent() {
        RentalContract r = contract(); r.termsVersion = "v2";
        UUID facilityId = UUID.randomUUID();
        PickupAppointment pickup = new PickupAppointment();
        pickup.contractId = contractId; pickup.customerId = customerId; pickup.facilityId = facilityId;
        pickup.assignedStaffId = staffId; pickup.appointmentTime = LocalDateTime.of(2026, 10, 29, 10, 0);
        PaymentTransaction p = new PaymentTransaction();
        p.transactionId = UUID.randomUUID(); p.contractId = contractId;
        p.paymentMethod = "CASH"; p.paymentType = "REMAINING_RENT"; p.status = "PENDING";
        Invoice i = invoice(); i.invoiceType = "REMAINING_RENT";
        p.invoiceId = i.invoiceId;
        when(access.contract(contractId, true)).thenReturn(r);
        when(access.role()).thenReturn("FACILITY_MANAGER", "FACILITY_STAFF", "FACILITY_STAFF");
        AtomicReference<UUID> actor = new AtomicReference<>(UUID.randomUUID());
        when(access.currentId()).thenAnswer(ignored -> actor.get());
        when(access.facilityId(r)).thenReturn(facilityId);
        when(access.today()).thenReturn(r.startDate);
        when(access.now()).thenReturn(pickup.appointmentTime);
        when(appointments.findFirstByContractIdAndStatus(contractId, "SCHEDULED")).thenReturn(Optional.of(pickup));
        when(payments.findById(p.transactionId)).thenReturn(Optional.of(p));
        when(invoices.findById(i.invoiceId)).thenReturn(Optional.of(i));
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.confirmCash(contractId, p.transactionId)).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.confirmCash(contractId, p.transactionId)).status());
        // Match the invoice to the actual remaining amount calculated from v2 terms.
        p.amount = service.calculate(r).initialRent().amountDue();
        i.totalAmount = p.amount;
        actor.set(staffId);
        assertEquals("SUCCESS", service.confirmCash(contractId, p.transactionId).status);
        assertEquals("PAID", i.status);
        verify(invoices, times(1)).save(i);
        var log = org.mockito.ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogs).saveAndFlush(log.capture());
        assertEquals(staffId, log.getValue().getUserId());
        assertTrue(log.getValue().getDescription().contains("role=FACILITY_STAFF;result=SUCCESS"));
    }

    @Test
    void staleInvoiceCannotCreateAFalseCashSuccessAudit() {
        RentalContract r = contract(); Invoice i = invoice();
        i.status = "PAID"; i.paidAmount = i.totalAmount;
        PaymentTransaction p = new PaymentTransaction();
        p.transactionId = UUID.randomUUID(); p.contractId = contractId; p.invoiceId = i.invoiceId;
        p.paymentMethod = "CASH"; p.paymentType = "DEPOSIT"; p.amount = i.totalAmount; p.status = "PENDING";
        when(access.contract(contractId, true)).thenReturn(r);
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(payments.findById(p.transactionId)).thenReturn(Optional.of(p));
        when(invoices.findById(i.invoiceId)).thenReturn(Optional.of(i));

        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.confirmCash(contractId, p.transactionId)).status());
        assertEquals("PENDING", p.status);
        verify(payments, never()).save(any());
        verify(activityLogs, never()).saveAndFlush(any());
    }
}
