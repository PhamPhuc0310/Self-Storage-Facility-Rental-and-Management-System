package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.Invoice;
import com.safebox.self_storage.entity.RentalContract;
import com.safebox.self_storage.entity.Reservation;
import com.safebox.self_storage.entity.StorageUnit;
import com.safebox.self_storage.entity.PaymentTransaction;
import com.safebox.self_storage.dto.PaymentRequest;
import com.safebox.self_storage.dto.AppointmentRequest;
import com.safebox.self_storage.dto.contract.ConfirmContractRequest;
import com.safebox.self_storage.repository.InvoiceRepository;
import com.safebox.self_storage.repository.RentalContractRepository;
import com.safebox.self_storage.repository.ReservationRepository;
import com.safebox.self_storage.repository.StorageUnitRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Opt-in persistence regression against a disposable SQL Server database with the SafeBox schema.
 * Configure SAFEBOX_SQLSERVER_TEST_URL, SAFEBOX_SQLSERVER_TEST_USER,
 * SAFEBOX_SQLSERVER_TEST_PASSWORD, SAFEBOX_SQLSERVER_TEST_RESERVATION_ID and
 * SAFEBOX_SQLSERVER_TEST_MANAGER_ID. The reservation must be PENDING with an
 * AVAILABLE matching unit. The test transaction rolls back every write.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SAFEBOX_SQLSERVER_TEST_URL", matches = "jdbc:sqlserver:.*")
class ReservationApprovalSqlServerTest {
    @DynamicPropertySource
    static void sqlServer(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_PASSWORD"));
    }

    @Autowired ReservationApprovalService approval;
    @Autowired ReservationRepository reservations;
    @Autowired RentalContractRepository contracts;
    @Autowired InvoiceRepository invoices;
    @Autowired StorageUnitRepository units;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired ContractService contractService;
    @Autowired PaymentService paymentService;
    @Autowired AppointmentService appointmentService;
    @MockBean Sprint2Access access;

    @Test void contractRowExistsBeforeBothInvoiceRowsAndDuplicateApprovalIsIdempotent() {
        UUID reservationId = UUID.fromString(System.getenv("SAFEBOX_SQLSERVER_TEST_RESERVATION_ID"));
        UUID managerId = UUID.fromString(System.getenv("SAFEBOX_SQLSERVER_TEST_MANAGER_ID"));
        Reservation reservation = reservations.findById(reservationId).orElseThrow();
        assertEquals("PENDING", reservation.getStatus());
        assertTrue(contracts.findByReservationId(reservationId).isEmpty());
        assertFalse(units.findByFacilityIdAndTypeIdAndStatusOrderByUnitNumberAsc(
                reservation.getFacilityId(), reservation.getTypeId(), "AVAILABLE").isEmpty());
        assertTrue(reservation.getStartDate().isAfter(LocalDate.now(Sprint2Access.ZONE).minusDays(1)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.USERS WHERE user_id = ?",
                Integer.class, managerId));

        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(reservation.getFacilityId()));
        when(access.today()).thenReturn(LocalDate.now(Sprint2Access.ZONE));
        when(access.now()).thenReturn(LocalDateTime.now(Sprint2Access.ZONE));
        when(access.currentId()).thenReturn(managerId);

        RentalContract created = approval.approve(reservationId);
        entityManager.flush();
        entityManager.clear();

        assertNotNull(created.contractId);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.RENTAL_CONTRACTS WHERE reservation_id = ? AND contract_id = ?",
                Integer.class, reservationId, created.contractId));
        List<Invoice> createdInvoices = invoices.findByContractId(created.contractId);
        assertEquals(2, createdInvoices.size());
        assertEquals(Set.of("DEPOSIT", "v2".equals(reservation.getPricingVersion())
                ? "REMAINING_RENT" : "INITIAL_RENT"),
                createdInvoices.stream().map(Invoice::getInvoiceType).collect(Collectors.toSet()));
        assertEquals("CONFIRMED", reservations.findById(reservationId).orElseThrow().getStatus());
        StorageUnit allocated = units.findById(created.unitId).orElseThrow();
        assertEquals(reservation.getFacilityId(), allocated.getFacilityId());
        assertEquals(reservation.getTypeId(), allocated.getTypeId());
        assertEquals("RESERVED", allocated.getStatus());

        RentalContract repeated = approval.approve(reservationId);
        entityManager.flush();
        assertEquals(created.contractId, repeated.contractId);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.RENTAL_CONTRACTS WHERE reservation_id = ?",
                Integer.class, reservationId));
        assertEquals(2, invoices.findByContractId(created.contractId).size());
    }

    @Test void newV2ApprovalSignatureAndFirstDepositPaymentHaveNoLegacyConflict() {
        UUID reservationId = UUID.fromString(System.getenv("SAFEBOX_SQLSERVER_TEST_RESERVATION_ID"));
        UUID managerId = UUID.fromString(System.getenv("SAFEBOX_SQLSERVER_TEST_MANAGER_ID"));
        Reservation reservation = reservations.findById(reservationId).orElseThrow();
        assertEquals("v2", reservation.getPricingVersion());
        assertEquals("PENDING", reservation.getStatus());
        assertTrue(contracts.findByReservationId(reservationId).isEmpty());
        LocalDateTime now = LocalDateTime.now(Sprint2Access.ZONE);
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(reservation.getFacilityId()));
        when(access.today()).thenReturn(now.toLocalDate());
        when(access.now()).thenReturn(now);
        when(access.currentId()).thenReturn(managerId);

        RentalContract created = approval.approve(reservationId);
        entityManager.flush(); entityManager.clear();
        UUID id = created.contractId;
        assertNotNull(id);
        assertEquals(Set.of("DEPOSIT", "REMAINING_RENT"), invoices.findByContractId(id).stream()
                .map(Invoice::getInvoiceType).collect(Collectors.toSet()));
        assertTrue(invoices.findByContractIdAndInvoiceType(id, "INITIAL_PAYMENT").isEmpty());
        assertEquals(2, invoices.findByContractId(id).size());
        assertTrue(invoices.findByContractId(id).stream().allMatch(i -> id.equals(i.contractId)
                && "UNPAID".equals(i.status) && BigDecimal.ZERO.compareTo(i.paidAmount) == 0));

        UUID customerId = reservation.getCustomerId();
        contractService.confirmContract(customerId, id, new ConfirmContractRequest(true, "SQL test signature"));
        entityManager.flush(); entityManager.clear();
        assertNotNull(contracts.findById(id).orElseThrow().getCustomerSignedAt());
        when(access.role()).thenReturn("CUSTOMER");
        when(access.currentId()).thenReturn(customerId);
        when(access.contract(id, true)).thenAnswer(call -> contracts.lockById(id).orElseThrow());
        when(access.contract(id, false)).thenAnswer(call -> contracts.findById(id).orElseThrow());

        LocalDate pickupDate = reservation.getStartDate().isAfter(now.toLocalDate())
                ? reservation.getStartDate() : now.toLocalDate().plusDays(1);
        AppointmentRequest pickup = new AppointmentRequest(OffsetDateTime.of(
                pickupDate.atTime(10, 0), ZoneOffset.ofHours(7)));
        assertTrue(paymentService.summary(id).deposit().remainingAmount().signum() > 0);

        BigDecimal deposit = contracts.findById(id).orElseThrow().depositAmount;
        PaymentRequest request = new PaymentRequest("DEPOSIT", deposit, "BANK_TRANSFER", "sql-first-deposit");
        PaymentTransaction first = paymentService.pay(id, request);
        entityManager.flush(); entityManager.clear();
        assertEquals("SUCCESS", first.status);
        assertEquals(id, first.contractId);
        assertEquals("DEPOSIT", first.paymentType);
        assertEquals(id, invoices.findById(first.invoiceId).orElseThrow().contractId);
        assertEquals("PAID", invoices.findById(first.invoiceId).orElseThrow().status);
        assertEquals(0, deposit.compareTo(invoices.findById(first.invoiceId).orElseThrow().paidAmount));
        assertEquals(0, paymentService.summary(id).deposit().remainingAmount().signum());
        assertEquals(first.transactionId, paymentService.pay(id, request).transactionId);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.PAYMENT_TRANSACTIONS WHERE contract_id = ? AND payment_type = 'DEPOSIT' AND status = 'SUCCESS'",
                Integer.class, id));
        assertEquals(2, invoices.findByContractId(id).size());
        assertTrue(invoices.findByContractIdAndInvoiceType(id, "INITIAL_PAYMENT").isEmpty());
        assertEquals(id, appointmentService.create(id, pickup).contractId);
    }
}
