package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.*;
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
class Sprint2ServiceTest {
    @Mock Sprint2Access access;
    @Mock PaymentTransactionRepository paymentRepository;
    @Mock InvoiceRepository invoices;
    @Mock DepositRefundRepository refunds;
    @Mock PickupAppointmentRepository appointmentRepository;
    @Mock RentalContractRepository contracts;
    @Mock FacilityRepository facilities;
    @Mock StorageUnitRepository units;
    @Mock UserRepository users;
    @Mock UserFacilityAssignmentRepository assignments;
    PaymentService payments;
    AppointmentService appointments;
    RentalContract contract;
    UUID contractId = UUID.randomUUID(), customerId = UUID.randomUUID(), facilityId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 10, 11, 8, 0);

    @BeforeEach void setup() {
        payments = new PaymentService(access, paymentRepository, invoices, appointmentRepository, refunds);
        appointments = new AppointmentService(access, appointmentRepository, contracts, facilities, units, users, assignments, invoices, payments);
        contract = new RentalContract(); contract.contractId = contractId; contract.customerId = customerId;
        contract.unitId = UUID.randomUUID(); contract.status = "CONFIRMED";
        contract.startDate = LocalDate.of(2026,10,11); contract.endDate = LocalDate.of(2027,4,11);
        contract.depositAmount = new BigDecimal("2000000"); contract.monthlyRentalFee = new BigDecimal("4500000");
    }
    void paymentSetup() {
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.currentId()).thenReturn(customerId);
        lenient().when(access.role()).thenReturn("CUSTOMER");
        lenient().when(access.today()).thenReturn(now.toLocalDate()); lenient().when(access.now()).thenReturn(now);
        Invoice invoice = new Invoice(); invoice.invoiceId = UUID.randomUUID(); invoice.paidAmount = BigDecimal.ZERO; invoice.totalAmount = new BigDecimal("4500000");
        lenient().when(invoices.findFirstByContractIdAndInvoiceType(any(),any())).thenReturn(Optional.of(invoice));
        lenient().when(invoices.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(paymentRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            PaymentTransaction payment = invocation.getArgument(0);
            payment.transactionId = UUID.randomUUID();
            return payment;
        });
        lenient().when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
    @Test void successfulPaymentAndIdempotency() {
        paymentSetup();
        PaymentRequest request = new PaymentRequest("DEPOSIT", new BigDecimal("1000000"), "BANK_TRANSFER", "same-key");
        PaymentTransaction created = payments.pay(contractId, request);
        assertEquals("SUCCESS", created.status);
        when(paymentRepository.findByCreatedByAndIdempotencyKey(customerId,"same-key")).thenReturn(Optional.of(created));
        assertSame(created, payments.pay(contractId, request));
        verify(paymentRepository, times(1)).saveAndFlush(any());
    }
    @Test void legacyCombinedPaymentBlocksAnotherChargePendingReconciliation() {
        when(access.contract(contractId, true)).thenReturn(contract);
        Invoice legacy = new Invoice(); legacy.paidAmount = new BigDecimal("1000000");
        Invoice newerUnpaid = new Invoice(); newerUnpaid.paidAmount = BigDecimal.ZERO;
        when(invoices.findByContractIdAndInvoiceType(contractId, "INITIAL_PAYMENT"))
                .thenReturn(List.of(newerUnpaid, legacy));
        BusinessException error = assertThrows(BusinessException.class, () -> payments.pay(contractId,
                new PaymentRequest("DEPOSIT", new BigDecimal("1000000"), "BANK_TRANSFER", "new-key")));
        assertEquals(409, error.status().value());
        verify(paymentRepository, never()).saveAndFlush(any());
    }
    @Test void paidLegacyInvoiceWithStaleZeroAmountStillBlocks() {
        when(access.contract(contractId, true)).thenReturn(contract);
        Invoice legacy = new Invoice(); legacy.invoiceId = UUID.randomUUID();
        legacy.status = "PAID"; legacy.paidAmount = BigDecimal.ZERO;
        when(invoices.findByContractIdAndInvoiceType(contractId, "INITIAL_PAYMENT"))
                .thenReturn(List.of(legacy));
        assertEquals(409, assertThrows(BusinessException.class, () -> payments.pay(contractId,
                new PaymentRequest("DEPOSIT", new BigDecimal("1000000"), "BANK_TRANSFER", "paid-legacy"))).status().value());
        verify(paymentRepository, never()).saveAndFlush(any());
    }
    @Test void unpaidLegacyInvoiceDoesNotBlockV2Deposit() {
        contract.termsVersion = "v2";
        paymentSetup();
        Invoice legacy = new Invoice(); legacy.invoiceId = UUID.randomUUID();
        legacy.status = "UNPAID"; legacy.paidAmount = BigDecimal.ZERO;
        when(invoices.findByContractIdAndInvoiceType(contractId, "INITIAL_PAYMENT"))
                .thenReturn(List.of(legacy));
        PaymentTransaction paid = payments.pay(contractId,
                new PaymentRequest("DEPOSIT", new BigDecimal("1000000"), "BANK_TRANSFER", "v2-deposit"));
        assertEquals("SUCCESS", paid.status);
        assertEquals("DEPOSIT", paid.paymentType);
        verify(paymentRepository).findByInvoiceIdIn(Set.of(legacy.invoiceId));
    }
    @Test void successfulLegacyTransactionBlocksChargeEvenWithStaleInvoiceAmount() {
        contract.termsVersion = "v2";
        when(access.contract(contractId, true)).thenReturn(contract);
        Invoice legacy = new Invoice(); legacy.invoiceId = UUID.randomUUID();
        legacy.status = "UNPAID"; legacy.paidAmount = BigDecimal.ZERO;
        when(invoices.findByContractIdAndInvoiceType(contractId, "INITIAL_PAYMENT"))
                .thenReturn(List.of(legacy));
        PaymentTransaction old = new PaymentTransaction(); old.status = "SUCCESS";
        old.amount = new BigDecimal("1000000");
        when(paymentRepository.findByInvoiceIdIn(Set.of(legacy.invoiceId))).thenReturn(List.of(old));
        assertEquals(409, assertThrows(BusinessException.class, () -> payments.pay(contractId,
                new PaymentRequest("DEPOSIT", new BigDecimal("1000000"), "BANK_TRANSFER", "new-key"))).status().value());
        verify(paymentRepository, never()).saveAndFlush(any());
    }
    @Test void paidLegacyInvoiceOnAnotherContractDoesNotBlockNewContract() {
        contract.termsVersion = "v2";
        paymentSetup();
        UUID otherContractId = UUID.randomUUID();
        Invoice otherLegacy = new Invoice(); otherLegacy.invoiceId = UUID.randomUUID();
        otherLegacy.contractId = otherContractId; otherLegacy.status = "PAID";
        otherLegacy.paidAmount = new BigDecimal("2000000");
        when(invoices.findByContractIdAndInvoiceType(any(UUID.class), eq("INITIAL_PAYMENT")))
                .thenAnswer(call -> otherContractId.equals(call.getArgument(0)) ? List.of(otherLegacy) : List.of());
        assertEquals("SUCCESS", payments.pay(contractId,
                new PaymentRequest("DEPOSIT", new BigDecimal("1000000"), "BANK_TRANSFER", "isolated-key")).status);
        verify(invoices).findByContractIdAndInvoiceType(contractId, "INITIAL_PAYMENT");
        verify(invoices, never()).findByContractIdAndInvoiceType(otherContractId, "INITIAL_PAYMENT");
    }
    @Test void rejectsOverpayment() {
        paymentSetup();
        assertEquals(400, assertThrows(BusinessException.class, () -> payments.pay(contractId,
                new PaymentRequest("DEPOSIT", new BigDecimal("2000001"), "BANK_TRANSFER", "large"))).status().value());
        verify(paymentRepository, never()).saveAndFlush(any());
    }
    @Test void customerDepositIsProcessedByBackendWithoutStaff() {
        paymentSetup();
        assertEquals(403, assertThrows(BusinessException.class, () -> payments.pay(contractId,
                new PaymentRequest("DEPOSIT", new BigDecimal("1000000"), "CASH", "cash-key"))).status().value());
        assertEquals(403, assertThrows(BusinessException.class, () -> payments.pay(contractId,
                new PaymentRequest("DEPOSIT", new BigDecimal("1000000"), "DEMO_ONLINE_PAYMENT", "demo-key"))).status().value());
        assertEquals(403, assertThrows(BusinessException.class, () -> payments.pay(contractId,
                new PaymentRequest("INITIAL_RENT", new BigDecimal("1000000"), "BANK_TRANSFER", "rent-key"))).status().value());
        PaymentTransaction paid = payments.pay(contractId,
                new PaymentRequest("DEPOSIT", new BigDecimal("1000000"), "BANK_TRANSFER", "bank-key"));
        assertEquals("SUCCESS", paid.status);
        assertEquals(new BigDecimal("2000000"), payments.calculate(contract).deposit().remainingAmount());
        verify(invoices).save(any());
    }
    @Test void onlySuccessfulTransactionsCountAndBothComponentsRequired() {
        PaymentTransaction success = new PaymentTransaction(); success.amount = new BigDecimal("1000000");
        when(paymentRepository.successful(contractId,"DEPOSIT")).thenReturn(List.of(success));
        assertEquals(new BigDecimal("1000000"), payments.calculate(contract).deposit().remainingAmount());
        assertFalse(payments.calculate(contract).readyForHandover());
        success.amount = new BigDecimal("2000000");
        PaymentTransaction rent = new PaymentTransaction(); rent.amount = new BigDecimal("4500000");
        when(paymentRepository.successful(contractId,"INITIAL_RENT")).thenReturn(List.of(rent));
        assertTrue(payments.calculate(contract).readyForHandover());
    }
    @Test void v2RemainingRentCannotBePaidBeforeScheduledPickupDay() {
        contract.termsVersion = "v2";
        contract.startDate = LocalDate.of(2026,11,1); contract.endDate = LocalDate.of(2027,2,1);
        contract.monthlyRentalFee = new BigDecimal("900000"); contract.depositAmount = new BigDecimal("990000");
        paymentSetup();
        PaymentService.Summary s = payments.calculate(contract);
        assertEquals(new BigDecimal("2970000"), s.contractTotal());
        assertEquals(new BigDecimal("1980000"), s.initialRent().amountDue());
        assertFalse(s.remainingPaymentAvailable());
        doReturn("FACILITY_STAFF").when(access).role();
        assertEquals(409, assertThrows(BusinessException.class, () -> payments.pay(contractId,
                new PaymentRequest("REMAINING_RENT", new BigDecimal("100000"), "CASH", "too-early"))).status().value());
        verify(paymentRepository, never()).saveAndFlush(any());
    }
    @Test void staffCannotProcessDepositOrAnotherStaffsRemainingRent() {
        contract.termsVersion = "v2";
        contract.startDate = LocalDate.of(2026,11,1); contract.endDate = LocalDate.of(2027,2,1);
        contract.monthlyRentalFee = new BigDecimal("900000"); contract.depositAmount = new BigDecimal("990000");
        UUID staffId = UUID.randomUUID();
        PickupAppointment pickup = new PickupAppointment(); pickup.appointmentTime = LocalDateTime.of(2026,11,25,9,0);
        pickup.assignedStaffId = UUID.randomUUID(); pickup.facilityId = facilityId; pickup.customerId = customerId;
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.currentId()).thenReturn(staffId); when(access.role()).thenReturn("FACILITY_STAFF");
        when(appointmentRepository.findFirstByContractIdAndStatus(contractId,"SCHEDULED")).thenReturn(Optional.of(pickup));
        assertEquals(403, assertThrows(BusinessException.class, () -> payments.pay(contractId,
                new PaymentRequest("DEPOSIT", new BigDecimal("100000"), "CASH", "staff-deposit"))).status().value());
        assertEquals(403, assertThrows(BusinessException.class, () -> payments.pay(contractId,
                new PaymentRequest("REMAINING_RENT", new BigDecimal("100000"), "CASH", "wrong-staff"))).status().value());
        verify(paymentRepository, never()).saveAndFlush(any());
    }
    @Test void staffCannotReadAnotherStaffsPaymentOrAppointment() {
        PickupAppointment pickup = new PickupAppointment();
        pickup.assignedStaffId = UUID.randomUUID(); pickup.facilityId = facilityId; pickup.customerId = customerId;
        UUID staffId = UUID.randomUUID();
        when(access.contract(contractId,false)).thenReturn(contract);
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(staffId);
        when(appointmentRepository.findFirstByContractIdAndStatus(contractId,"SCHEDULED")).thenReturn(Optional.of(pickup));
        when(appointmentRepository.findFirstByContractIdOrderByCreatedAtDesc(contractId)).thenReturn(Optional.of(pickup));
        assertEquals(403, assertThrows(BusinessException.class, () -> payments.summary(contractId)).status().value());
        assertEquals(403, assertThrows(BusinessException.class, () -> payments.history(contractId)).status().value());
        assertEquals(403, assertThrows(BusinessException.class, () -> appointments.get(contractId)).status().value());
        verify(paymentRepository, never()).findByContractIdOrderByCreatedAtDesc(any());
    }
    @Test void authorizedStaffCanRecordRemainingCashOnlyOnPickupDate() {
        contract.termsVersion = "v2";
        contract.startDate = LocalDate.of(2026,11,1); contract.endDate = LocalDate.of(2027,2,1);
        contract.monthlyRentalFee = new BigDecimal("900000"); contract.depositAmount = new BigDecimal("990000");
        PickupAppointment pickup = new PickupAppointment(); pickup.appointmentTime = LocalDateTime.of(2026,11,25,9,0);
        UUID staffId = UUID.randomUUID(); pickup.assignedStaffId = staffId; pickup.facilityId = facilityId; pickup.customerId = customerId;
        PaymentTransaction deposit = new PaymentTransaction(); deposit.amount = new BigDecimal("990000");
        Invoice invoice = new Invoice(); invoice.invoiceId = UUID.randomUUID(); invoice.totalAmount = new BigDecimal("1980000");
        invoice.paidAmount = BigDecimal.ZERO;
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.currentId()).thenReturn(staffId); when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.facilityId(contract)).thenReturn(facilityId);
        when(access.today()).thenReturn(LocalDate.of(2026,11,25));
        when(access.now()).thenReturn(LocalDateTime.of(2026,11,25,9,0));
        when(appointmentRepository.findFirstByContractIdAndStatus(contractId,"SCHEDULED")).thenReturn(Optional.of(pickup));
        when(paymentRepository.successful(contractId,"DEPOSIT")).thenReturn(List.of(deposit));
        when(invoices.findFirstByContractIdAndInvoiceType(contractId,"REMAINING_RENT")).thenReturn(Optional.of(invoice));
        when(paymentRepository.saveAndFlush(any())).thenAnswer(call -> {
            PaymentTransaction payment = call.getArgument(0);
            payment.transactionId = UUID.randomUUID();
            return payment;
        });
        when(paymentRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        PaymentTransaction recorded = payments.pay(contractId,
                new PaymentRequest("REMAINING_RENT", new BigDecimal("1980000"), "CASH", "pickup-cash"));
        assertEquals("SUCCESS", recorded.status);
        assertEquals(new BigDecimal("1980000"), invoice.paidAmount);
        assertEquals("PAID", invoice.status);
    }
    @Test void pendingRemainingTransferNeedsStaffVerificationAndDueDate() {
        contract.termsVersion = "v2";
        contract.startDate = LocalDate.of(2026,11,1); contract.endDate = LocalDate.of(2027,2,1);
        contract.monthlyRentalFee = new BigDecimal("900000"); contract.depositAmount = new BigDecimal("990000");
        PickupAppointment pickup = new PickupAppointment(); pickup.appointmentTime = LocalDateTime.of(2026,11,25,9,0);
        UUID staffId = UUID.randomUUID(); pickup.assignedStaffId = staffId; pickup.facilityId = facilityId; pickup.customerId = customerId;
        PaymentTransaction transfer = new PaymentTransaction(); transfer.transactionId = UUID.randomUUID();
        transfer.contractId = contractId; transfer.invoiceId = UUID.randomUUID();
        transfer.paymentType = "REMAINING_RENT"; transfer.paymentMethod = "BANK_TRANSFER";
        transfer.status = "PENDING"; transfer.amount = new BigDecimal("1980000");
        Invoice invoice = new Invoice(); invoice.invoiceId = transfer.invoiceId;
        invoice.totalAmount = transfer.amount; invoice.paidAmount = BigDecimal.ZERO;
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(staffId);
        when(access.facilityId(contract)).thenReturn(facilityId);
        when(access.today()).thenReturn(LocalDate.of(2026,11,24), LocalDate.of(2026,11,25));
        when(access.now()).thenReturn(LocalDateTime.of(2026,11,25,9,0));
        when(paymentRepository.findById(transfer.transactionId)).thenReturn(Optional.of(transfer));
        when(appointmentRepository.findFirstByContractIdAndStatus(contractId,"SCHEDULED")).thenReturn(Optional.of(pickup));
        assertEquals(409, assertThrows(BusinessException.class,
                () -> payments.confirmTransfer(contractId, transfer.transactionId)).status().value());
        assertEquals("PENDING", transfer.status);
        when(invoices.findById(invoice.invoiceId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.save(transfer)).thenReturn(transfer);
        assertEquals("SUCCESS", payments.confirmTransfer(contractId, transfer.transactionId).status);
        assertEquals(new BigDecimal("1980000"), invoice.paidAmount);
    }
    @Test void v2PickupRequiresFullDepositAndStartsUnassigned() {
        contract.termsVersion = "v2"; contract.depositAmount = new BigDecimal("990000");
        contract.monthlyRentalFee = new BigDecimal("900000");
        contract.endDate = LocalDate.of(2027,1,11);
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.role()).thenReturn("CUSTOMER"); when(access.now()).thenReturn(now);
        when(access.facilityId(contract)).thenReturn(facilityId);
        AppointmentRequest request = new AppointmentRequest(OffsetDateTime.of(2026,10,12,9,0,0,0,ZoneOffset.ofHours(7)));
        assertEquals(409, assertThrows(BusinessException.class, () -> appointments.create(contractId,request)).status().value());
        PaymentTransaction paid = new PaymentTransaction(); paid.amount = new BigDecimal("990000");
        when(paymentRepository.successful(contractId,"DEPOSIT")).thenReturn(List.of(paid));
        Facility facility = new Facility(); facility.setOpeningTime(LocalTime.of(8,0)); facility.setClosingTime(LocalTime.of(17,0));
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));
        when(appointmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PickupAppointment created = appointments.create(contractId, request);
        assertEquals("SCHEDULED", created.status);
        assertNull(created.assignedStaffId);
        verify(appointmentRepository).saveAndFlush(any());
    }
    @Test void batchedHandoverRequiresBothSuccessfulAmounts() {
        when(paymentRepository.successfulTotals(any())).thenReturn(List.of(
                new Object[]{contractId, "DEPOSIT", new BigDecimal("2000000")},
                new Object[]{contractId, "INITIAL_RENT", new BigDecimal("4499999")}));
        assertFalse(payments.readyForHandover(List.of(contract)).get(contractId));
        when(paymentRepository.successfulTotals(any())).thenReturn(List.of(
                new Object[]{contractId, "DEPOSIT", new BigDecimal("2000000")},
                new Object[]{contractId, "INITIAL_RENT", new BigDecimal("4500000")}));
        assertTrue(payments.readyForHandover(List.of(contract)).get(contractId));
        contract.status = "CANCELLED";
        assertFalse(payments.readyForHandover(List.of(contract)).get(contractId));
    }
    @Test void appointmentCreationValidationAndConflict() {
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.role()).thenReturn("CUSTOMER"); when(access.now()).thenReturn(now);
        when(access.facilityId(contract)).thenReturn(facilityId);
        Facility facility = new Facility(); facility.setOpeningTime(LocalTime.of(8,0)); facility.setClosingTime(LocalTime.of(17,0));
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));
        when(appointmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AppointmentRequest valid = new AppointmentRequest(OffsetDateTime.of(2026,10,12,9,0,0,0,ZoneOffset.ofHours(7)));
        assertEquals("SCHEDULED", appointments.create(contractId, valid).status);
        verify(appointmentRepository).saveAndFlush(argThat(a -> a.contractId.equals(contractId)
                && a.facilityId.equals(facilityId) && "SCHEDULED".equals(a.status)
                && a.assignedStaffId == null));
        when(appointmentRepository.findFirstByContractIdAndStatus(contractId,"SCHEDULED"))
                .thenReturn(Optional.of(new PickupAppointment()));
        assertEquals(409, assertThrows(BusinessException.class, () -> appointments.create(contractId, valid)).status().value());
    }
    @Test void pastAppointmentIsRejected() {
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.role()).thenReturn("CUSTOMER"); when(access.now()).thenReturn(now);
        AppointmentRequest past = new AppointmentRequest(OffsetDateTime.of(2026,10,11,7,0,0,0,ZoneOffset.ofHours(7)));
        assertEquals(400, assertThrows(BusinessException.class, () -> appointments.create(contractId,past)).status().value());
    }
    @Test void utcAppointmentIsRejectedWithoutWeakeningVietnamOffsetValidation() {
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.role()).thenReturn("CUSTOMER"); when(access.now()).thenReturn(now);
        AppointmentRequest utc = new AppointmentRequest(OffsetDateTime.of(2026,10,12,9,0,0,0,ZoneOffset.UTC));
        BusinessException error = assertThrows(BusinessException.class, () -> appointments.create(contractId, utc));
        assertEquals(400, error.status().value());
        assertTrue(error.getMessage().contains("+07:00"));
        verify(appointmentRepository, never()).saveAndFlush(any());
    }
    @Test void completedAppointmentBlocksNewBookingEvenAfterLaterCancellation() {
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.role()).thenReturn("CUSTOMER");
        when(appointmentRepository.existsByContractIdAndStatus(contractId,"COMPLETED")).thenReturn(true);
        AppointmentRequest valid = new AppointmentRequest(OffsetDateTime.of(2026,10,12,9,0,0,0,ZoneOffset.ofHours(7)));
        assertEquals(409, assertThrows(BusinessException.class, () -> appointments.create(contractId,valid)).status().value());
        verify(appointmentRepository, never()).save(any());
    }
    @Test void emptyDailyTasksReturnEmptyList() {
        when(access.role()).thenReturn("FACILITY_STAFF"); when(access.facilities()).thenReturn(List.of());
        assertTrue(appointments.daily(LocalDate.of(2026,10,11),null,null,null).isEmpty());
        verifyNoInteractions(appointmentRepository);
    }
    @Test void cancelAppointmentKeepsRecord() {
        PickupAppointment a = new PickupAppointment(); a.contractId = contractId;
        a.status = "SCHEDULED"; a.appointmentTime = now.plusDays(1); a.assignedStaffId = UUID.randomUUID();
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.role()).thenReturn("CUSTOMER"); when(access.now()).thenReturn(now);
        when(appointmentRepository.findFirstByContractIdAndStatus(contractId,"SCHEDULED")).thenReturn(Optional.of(a));
        when(appointmentRepository.save(a)).thenReturn(a);
        assertEquals("CANCELLED", appointments.cancel(contractId).status);
        assertNull(a.assignedStaffId);
        verify(appointmentRepository, never()).delete(any());
    }
    @Test void completedRefundKeepsScheduledAssignmentUntilCustomerExplicitlyCancels() {
        contract.termsVersion = "v2"; contract.depositAmount = new BigDecimal("990000");
        contract.monthlyRentalFee = new BigDecimal("900000");
        contract.endDate = LocalDate.of(2027,1,11);
        UUID staffId = UUID.randomUUID();
        PickupAppointment a = new PickupAppointment(); a.contractId = contractId;
        a.status = "SCHEDULED"; a.appointmentTime = now.plusDays(2); a.assignedStaffId = staffId;
        PaymentTransaction deposit = new PaymentTransaction(); deposit.amount = new BigDecimal("990000"); deposit.status = "SUCCESS";
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.contract(contractId,false)).thenReturn(contract);
        when(access.role()).thenReturn("CUSTOMER"); when(access.now()).thenReturn(now);
        when(access.today()).thenReturn(now.toLocalDate());
        when(appointmentRepository.findFirstByContractIdAndStatus(contractId,"SCHEDULED")).thenReturn(Optional.of(a));
        when(paymentRepository.successful(contractId,"DEPOSIT")).thenReturn(List.of(deposit));
        when(refunds.completedTotal(contractId)).thenReturn(new BigDecimal("400000"));
        assertEquals(new BigDecimal("590000"), payments.calculate(contract).deposit().paidAmount());
        AppointmentRequest next = new AppointmentRequest(OffsetDateTime.of(2026,10,14,9,0,0,0,ZoneOffset.ofHours(7)));
        assertEquals(409, assertThrows(BusinessException.class, () -> appointments.reschedule(contractId,next)).status().value());
        assertEquals("SCHEDULED", a.status);
        assertEquals(staffId, a.assignedStaffId);
        assertEquals(now.plusDays(2), a.appointmentTime);
        assertEquals("CONFIRMED", contract.status);
        verify(appointmentRepository, never()).save(any());
        verify(invoices, never()).save(any());
        clearInvocations(refunds);
        when(appointmentRepository.save(a)).thenReturn(a);
        assertEquals("CANCELLED", appointments.cancel(contractId).status);
        assertNull(a.assignedStaffId);
        assertEquals("CONFIRMED", contract.status);
        verifyNoInteractions(refunds);
    }
    @Test void managerCannotCancelScheduledAppointmentAndCustomerCan() {
        PickupAppointment a = new PickupAppointment(); a.contractId = contractId;
        a.status = "SCHEDULED"; a.appointmentTime = now.plusDays(1); a.assignedStaffId = UUID.randomUUID();
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.role()).thenReturn("FACILITY_MANAGER", "CUSTOMER");
        when(access.now()).thenReturn(now);
        assertEquals(403, assertThrows(BusinessException.class, () -> appointments.cancel(contractId)).status().value());
        assertEquals("SCHEDULED", a.status);
        when(appointmentRepository.findFirstByContractIdAndStatus(contractId,"SCHEDULED")).thenReturn(Optional.of(a));
        when(appointmentRepository.save(a)).thenReturn(a);
        assertEquals("CANCELLED", appointments.cancel(contractId).status);
        assertNull(a.assignedStaffId);
        assertEquals("CONFIRMED", contract.status);
        verifyNoInteractions(refunds);
        verify(appointmentRepository, never()).delete(any());
    }
    @Test void managerCannotAssignInactiveOrForeignFacilityStaff() {
        UUID staffId = UUID.randomUUID();
        PickupAppointment a = new PickupAppointment(); a.contractId = contractId;
        a.facilityId = facilityId; a.customerId = customerId; a.status = "SCHEDULED";
        a.appointmentTime = now.plusDays(1);
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.contract(contractId, true)).thenReturn(contract);
        when(access.facilityId(contract)).thenReturn(facilityId);
        when(access.now()).thenReturn(now);
        when(appointmentRepository.findFirstByContractIdAndStatus(contractId, "SCHEDULED")).thenReturn(Optional.of(a));
        Role role = new Role(); role.setRoleName("FACILITY_STAFF");
        User staff = new User(); staff.setRole(role); staff.setStatus("INACTIVE");
        when(users.findById(staffId)).thenReturn(Optional.of(staff));
        assertEquals(403, assertThrows(BusinessException.class, () -> appointments.assign(contractId, staffId)).status().value());
        staff.setStatus("ACTIVE");
        when(access.today()).thenReturn(now.toLocalDate());
        when(assignments.findActiveFacilityIds(staffId, now.toLocalDate())).thenReturn(List.of(UUID.randomUUID()));
        assertEquals(403, assertThrows(BusinessException.class, () -> appointments.assign(contractId, staffId)).status().value());
        verify(appointmentRepository, never()).save(any());
    }
    @Test void managerCanReplaceStaffOnSameScheduledAppointment() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        PickupAppointment a = new PickupAppointment(); a.id = UUID.randomUUID();
        a.contractId = contractId; a.facilityId = facilityId; a.customerId = customerId;
        a.status = "SCHEDULED"; a.appointmentTime = now.plusDays(1);
        Role role = new Role(); role.setRoleName("FACILITY_STAFF");
        User staff = new User(); staff.setRole(role); staff.setStatus("ACTIVE");
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.contract(contractId, true)).thenReturn(contract);
        when(access.facilityId(contract)).thenReturn(facilityId);
        when(access.now()).thenReturn(now);
        when(access.today()).thenReturn(now.toLocalDate());
        when(appointmentRepository.findFirstByContractIdAndStatus(contractId, "SCHEDULED")).thenReturn(Optional.of(a));
        when(users.findById(first)).thenReturn(Optional.of(staff));
        when(users.findById(second)).thenReturn(Optional.of(staff));
        when(assignments.findActiveFacilityIds(first, now.toLocalDate())).thenReturn(List.of(facilityId));
        when(assignments.findActiveFacilityIds(second, now.toLocalDate())).thenReturn(List.of(facilityId));
        when(appointmentRepository.save(a)).thenReturn(a);
        assertEquals(first, appointments.assign(contractId, first).assignedStaffId);
        assertEquals(second, appointments.assign(contractId, second).assignedStaffId);
        verify(appointmentRepository, times(2)).save(same(a));
        verify(appointmentRepository, never()).save(argThat(value -> value != a));
    }
    @Test void staffEligibleAtTwoManagedFacilitiesIsListedOncePerFacility() {
        UUID secondFacility = UUID.randomUUID(), staffId = UUID.randomUUID();
        Role role = new Role(); role.setRoleName("FACILITY_STAFF");
        User staff = new User(); staff.setUserId(staffId); staff.setRole(role);
        staff.setStatus("ACTIVE"); staff.setFullName("Phạm Gia Huy");
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facilityId, secondFacility));
        when(access.today()).thenReturn(now.toLocalDate());
        when(assignments.findActiveUserIds(List.of(facilityId), now.toLocalDate())).thenReturn(List.of(staffId));
        when(assignments.findActiveUserIds(List.of(secondFacility), now.toLocalDate())).thenReturn(List.of(staffId));
        when(assignments.findActiveUserIds(List.of(facilityId, secondFacility), now.toLocalDate())).thenReturn(List.of(staffId,staffId));
        when(users.findAllById(List.of(staffId))).thenReturn(List.of(staff));
        assertEquals(List.of(staffId), appointments.staffOptions(facilityId).stream().map(AppointmentService.StaffOption::id).toList());
        assertEquals(List.of(staffId), appointments.staffOptions(secondFacility).stream().map(AppointmentService.StaffOption::id).toList());
        assertEquals(List.of(staffId), appointments.staffOptions().stream().map(AppointmentService.StaffOption::id).toList());
        verifyNoInteractions(appointmentRepository);
    }
    @Test void eligibleStaffReceivesNeitherFacilityTaskUntilManagerAssignsIt() {
        UUID secondFacility = UUID.randomUUID(), staffId = UUID.randomUUID(), secondContractId = UUID.randomUUID();
        RentalContract secondContract = new RentalContract(); secondContract.contractId = secondContractId;
        secondContract.customerId = customerId; secondContract.status = "CONFIRMED";
        PickupAppointment first = new PickupAppointment(); first.contractId = contractId;
        first.facilityId = facilityId; first.customerId = customerId;
        first.status = "SCHEDULED"; first.appointmentTime = now.plusDays(1);
        PickupAppointment second = new PickupAppointment(); second.contractId = secondContractId;
        second.facilityId = secondFacility; second.customerId = customerId;
        second.status = "SCHEDULED"; second.appointmentTime = now.plusDays(1);
        Role role = new Role(); role.setRoleName("FACILITY_STAFF");
        User staff = new User(); staff.setRole(role); staff.setStatus("ACTIVE");
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.contract(secondContractId,true)).thenReturn(secondContract);
        when(access.facilityId(contract)).thenReturn(facilityId);
        when(access.facilityId(secondContract)).thenReturn(secondFacility);
        when(access.now()).thenReturn(now); when(access.today()).thenReturn(now.toLocalDate());
        when(appointmentRepository.findFirstByContractIdAndStatus(contractId,"SCHEDULED")).thenReturn(Optional.of(first));
        when(appointmentRepository.findFirstByContractIdAndStatus(secondContractId,"SCHEDULED")).thenReturn(Optional.of(second));
        when(users.findById(staffId)).thenReturn(Optional.of(staff));
        when(assignments.findActiveFacilityIds(staffId, now.toLocalDate())).thenReturn(List.of(facilityId,secondFacility));
        when(appointmentRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        assertNull(first.assignedStaffId);
        assertNull(second.assignedStaffId);
        verify(appointmentRepository, never()).save(any());
        assertEquals(staffId, appointments.assign(contractId, staffId).assignedStaffId);
        assertNull(second.assignedStaffId);
        assertEquals(staffId, appointments.assign(secondContractId, staffId).assignedStaffId);
        assertEquals("SCHEDULED", first.status);
        assertEquals("SCHEDULED", second.status);
        verify(appointmentRepository, times(2)).save(any());
    }
    @Test void awaitingAssignmentIsScopedToAllManagedFacilitiesOrOneSelected() {
        UUID second = UUID.randomUUID();
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facilityId, second));
        when(access.now()).thenReturn(now);
        assertTrue(appointments.awaitingAssignment(null).isEmpty());
        verify(appointmentRepository).findByFacilityIdInAndStatusAndAssignedStaffIdIsNullAndAppointmentTimeGreaterThanEqualOrderByAppointmentTimeAsc(
                List.of(facilityId, second), "SCHEDULED", now);
        assertTrue(appointments.awaitingAssignment(second).isEmpty());
        verify(appointmentRepository).findByFacilityIdInAndStatusAndAssignedStaffIdIsNullAndAppointmentTimeGreaterThanEqualOrderByAppointmentTimeAsc(
                List.of(second), "SCHEDULED", now);
        assertEquals(403, assertThrows(BusinessException.class, () ->
                appointments.awaitingAssignment(UUID.randomUUID())).status().value());
    }
    @Test void rescheduleResetsStaffAssignment() {
        PickupAppointment a = new PickupAppointment(); a.contractId = contractId;
        a.status = "SCHEDULED"; a.appointmentTime = now.plusDays(1); a.assignedStaffId = UUID.randomUUID();
        when(access.contract(contractId,true)).thenReturn(contract);
        when(access.contract(contractId,false)).thenReturn(contract);
        when(access.role()).thenReturn("CUSTOMER"); when(access.now()).thenReturn(now);
        when(access.facilityId(contract)).thenReturn(facilityId);
        when(appointmentRepository.findFirstByContractIdAndStatus(contractId,"SCHEDULED")).thenReturn(Optional.of(a));
        Facility facility = new Facility(); facility.setOpeningTime(LocalTime.of(8,0)); facility.setClosingTime(LocalTime.of(17,0));
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));
        when(appointmentRepository.save(a)).thenReturn(a);
        AppointmentRequest later = new AppointmentRequest(OffsetDateTime.of(2026,10,13,10,0,0,0,ZoneOffset.ofHours(7)));
        assertNull(appointments.reschedule(contractId,later).assignedStaffId);
        assertEquals(LocalDateTime.of(2026,10,13,10,0),a.appointmentTime);
    }
    @Test void staffCannotFilterAnotherPersonsTasks() {
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(customerId);
        assertEquals(403, assertThrows(BusinessException.class, () -> appointments.daily(
                LocalDate.of(2026,10,11),null,null,UUID.randomUUID())).status().value());
    }
    @Test void staffDailyQueryIsLimitedToOwnIdAndAssignedFacility() {
        UUID staffId = UUID.randomUUID();
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(staffId);
        when(access.facilities()).thenReturn(List.of(facilityId));
        assertTrue(appointments.daily(LocalDate.of(2026,10,11), null, null, null).isEmpty());
        verify(appointmentRepository).daily(eq(List.of(facilityId)), any(), any(), eq(staffId), isNull());
    }
    @Test void staffUpcomingQueryOnlyRequestsOwnScheduledFutureWork() {
        UUID staffId = UUID.randomUUID();
        UUID secondFacility = UUID.randomUUID();
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(staffId);
        when(access.facilities()).thenReturn(List.of(facilityId, secondFacility));
        when(access.now()).thenReturn(now);
        assertTrue(appointments.upcomingMine().isEmpty());
        verify(appointmentRepository).findTop20ByFacilityIdInAndAssignedStaffIdAndStatusAndAppointmentTimeGreaterThanEqualOrderByAppointmentTimeAsc(
                List.of(facilityId, secondFacility), staffId, "SCHEDULED", now);
    }
    @Test void managerStaffOptionsExcludeInactiveAccounts() {
        UUID activeId = UUID.randomUUID(), inactiveId = UUID.randomUUID();
        Role role = new Role(); role.setRoleName("FACILITY_STAFF");
        User active = new User(); active.setUserId(activeId); active.setRole(role); active.setStatus("ACTIVE"); active.setFullName("Nhân viên hợp lệ");
        User inactive = new User(); inactive.setUserId(inactiveId); inactive.setRole(role); inactive.setStatus("INACTIVE");
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facilityId));
        when(access.today()).thenReturn(now.toLocalDate());
        when(assignments.findActiveUserIds(List.of(facilityId), now.toLocalDate())).thenReturn(List.of(activeId, inactiveId));
        when(users.findAllById(List.of(activeId, inactiveId))).thenReturn(List.of(active, inactive));
        List<AppointmentService.StaffOption> options = appointments.staffOptions(facilityId);
        assertEquals(1, options.size());
        assertEquals(activeId, options.get(0).id());
    }
    @Test void managerSeesOwnFacilityTask() {
        PaymentService paymentReader = mock(PaymentService.class);
        AppointmentService service = new AppointmentService(access,appointmentRepository,contracts,facilities,units,users,assignments,invoices,paymentReader);
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facilityId));
        PickupAppointment a = new PickupAppointment(); a.contractId = contractId; a.facilityId = facilityId;
        a.customerId = customerId; a.appointmentTime = now.plusHours(1); a.status = "SCHEDULED";
        when(appointmentRepository.daily(eq(List.of(facilityId)),any(),any(),isNull(),isNull())).thenReturn(List.of(a));
        when(contracts.findAllById(any())).thenReturn(List.of(contract));
        StorageUnit unit = new StorageUnit(); unit.setUnitNumber("A-105");
        unit.setUnitId(contract.unitId); unit.setFacilityId(facilityId);
        when(units.findAllById(any())).thenReturn(List.of(unit));
        User customer = new User(); customer.setFullName("Khách hàng");
        customer.setUserId(customerId);
        when(users.findAllById(any())).thenReturn(List.of(customer));
        Facility facility = new Facility(); facility.setName("SafeBox");
        facility.setFacilityId(facilityId);
        when(facilities.findAllById(any())).thenReturn(List.of(facility));
        when(paymentReader.balances(any())).thenReturn(Map.of(contractId,
                new PaymentService.Balance(new BigDecimal("2000000"), BigDecimal.ZERO, BigDecimal.ZERO, true)));
        List<AppointmentService.DailyTask> tasks = service.daily(LocalDate.of(2026,10,11),null,null,null);
        assertEquals(1,tasks.size()); assertEquals("A-105",tasks.get(0).storageUnitCode());
        assertTrue(tasks.get(0).readyForHandover());
    }
    @Test void managerFacilityFilterUsesOnlySelectedAssignedFacility() {
        UUID otherAssigned = UUID.randomUUID();
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facilityId, otherAssigned));
        assertTrue(appointments.daily(LocalDate.of(2026,10,11), null, null, null, otherAssigned).isEmpty());
        verify(appointmentRepository).daily(eq(List.of(otherAssigned)), any(), any(), isNull(), isNull());
    }
    @Test void managerCannotFilterUnassignedFacilityOrItsStaff() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facilityId));
        UUID foreign = UUID.randomUUID();
        assertEquals(403, assertThrows(BusinessException.class, () ->
                appointments.daily(LocalDate.of(2026,10,11), null, null, null, foreign)).status().value());
        assertEquals(403, assertThrows(BusinessException.class, () ->
                appointments.staffOptions(foreign)).status().value());
        verifyNoInteractions(appointmentRepository);
    }
    @Test void handoverCannotCompleteBeforeBothChargesArePaid() {
        PaymentService paymentReader = mock(PaymentService.class);
        AppointmentService service = new AppointmentService(access,appointmentRepository,contracts,facilities,units,users,assignments,invoices,paymentReader);
        PaymentService.Component unpaid = new PaymentService.Component(BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ONE,"UNPAID");
        when(paymentReader.summary(contractId)).thenReturn(new PaymentService.Summary(contractId,unpaid,unpaid,false));
        assertEquals(409, assertThrows(BusinessException.class,
                () -> service.completeAppointmentAfterHandover(contractId)).status().value());
        verifyNoInteractions(appointmentRepository);
    }
}
