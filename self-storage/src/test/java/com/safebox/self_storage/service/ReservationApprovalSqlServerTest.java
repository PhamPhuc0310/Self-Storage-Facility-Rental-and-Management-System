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
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;

/**
 * Opt-in persistence regression against a disposable SQL Server database with the SafeBox schema.
 * Configure SAFEBOX_SQLSERVER_TEST_URL, SAFEBOX_SQLSERVER_TEST_USER, and
 * SAFEBOX_SQLSERVER_TEST_PASSWORD. Each test creates its own PENDING reservation
 * and AVAILABLE unit from the explicitly synthetic verified source. Every write
 * rolls back with the test transaction; the database identity is checked first.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SAFEBOX_SQLSERVER_TEST_URL", matches = "jdbc:sqlserver:.*")
class ReservationApprovalSqlServerTest {
    private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-4000-8000-00000000b301");
    private static final UUID UNIT_ID = UUID.fromString("00000000-0000-4000-8000-00000000b302");
    private static final UUID CUSTOMER_ID = UUID.fromString("374e5607-b0b4-f111-a965-932c2c6addb7");
    private static final UUID MANAGER_ID = UUID.fromString("3b4e5607-b0b4-f111-a965-932c2c6addb7");
    private static final UUID STAFF_ID = UUID.fromString("49a562b3-879b-420c-a6de-319f3fcca978");
    private static final UUID SOURCE_UNIT_ID = UUID.fromString("fa88d008-0594-4ef0-911e-4348a976499c");
    private static final UUID PRICING_ID = UUID.fromString("63292355-68be-f111-a965-932c2c6addb7");
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

    @BeforeEach void createIsolatedFixture() {
        assertEquals("SelfStorageFacilityDB_Test", jdbc.queryForObject("SELECT DB_NAME()", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.STORAGE_UNITS WHERE unit_id = ? "
                + "AND measurement_source = 'SYNTHETIC_DEMO' AND length_m = 2 AND width_m = 3 "
                + "AND height_m = 4 AND readiness_verified = 1 AND dimensions_verified = 1",
                Integer.class, SOURCE_UNIT_ID));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.PRICING_POLICIES WHERE pricing_id = ? "
                + "AND status = 'ACTIVE' AND monthly_price = 900000", Integer.class, PRICING_ID));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RESERVATIONS WHERE reservation_id = ?",
                Integer.class, RESERVATION_ID));
        jdbc.update("INSERT INTO dbo.STORAGE_UNITS "
                + "(unit_id, facility_id, type_id, unit_number, floor, zone, status, length_m, width_m, "
                + "height_m, readiness_verified, dimensions_verified, measurement_source) "
                + "SELECT ?, facility_id, type_id, 'SQL-APPROVAL-B302', floor, zone, 'AVAILABLE', "
                + "length_m, width_m, height_m, readiness_verified, dimensions_verified, measurement_source "
                + "FROM dbo.STORAGE_UNITS WHERE unit_id = ?", UNIT_ID, SOURCE_UNIT_ID);
        LocalDate start = LocalDate.now(Sprint2Access.ZONE).plusDays(1);
        jdbc.update("INSERT INTO dbo.RESERVATIONS "
                + "(reservation_id, customer_id, facility_id, type_id, pricing_id, start_date, end_date, "
                + "hold_expires_at, estimated_rental_amount, estimated_deposit_amount, status, "
                + "quoted_monthly_price, pricing_version) "
                + "SELECT ?, ?, facility_id, type_id, ?, ?, ?, DATEADD(day, 1, SYSDATETIME()), "
                + "900000, 300000, 'PENDING', 900000, 'v2' FROM dbo.STORAGE_UNITS WHERE unit_id = ?",
                RESERVATION_ID, CUSTOMER_ID, PRICING_ID, start, start.plusMonths(1), UNIT_ID);
    }

    @Test void contractRowExistsBeforeBothInvoiceRowsAndDuplicateApprovalIsIdempotent() {
        UUID reservationId = RESERVATION_ID;
        UUID managerId = MANAGER_ID;
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
        UUID reservationId = RESERVATION_ID;
        UUID managerId = MANAGER_ID;
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
        when(access.facilityId(any(RentalContract.class))).thenReturn(reservation.getFacilityId());

        LocalDate pickupDate = reservation.getStartDate().isAfter(now.toLocalDate())
                ? reservation.getStartDate() : now.toLocalDate().plusDays(1);
        AppointmentRequest pickup = new AppointmentRequest(OffsetDateTime.of(
                pickupDate.atTime(10, 0), ZoneOffset.ofHours(7)));
        assertTrue(paymentService.summary(id).deposit().remainingAmount().signum() > 0);

        BigDecimal deposit = contracts.findById(id).orElseThrow().depositAmount;
        PaymentRequest request = new PaymentRequest("DEPOSIT", deposit, "BANK_TRANSFER", "sql-first-deposit");
        PaymentTransaction first = paymentService.pay(id, request);
        assertEquals("PENDING", first.status);
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(STAFF_ID);
        first = paymentService.confirmTransfer(id, first.transactionId);
        when(access.role()).thenReturn("CUSTOMER");
        when(access.currentId()).thenReturn(customerId);
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
