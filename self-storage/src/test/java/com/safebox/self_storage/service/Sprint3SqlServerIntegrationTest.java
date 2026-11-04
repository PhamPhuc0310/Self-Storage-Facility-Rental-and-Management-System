package com.safebox.self_storage.service;

import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.EarlyReturnPolicyRepository;
import com.safebox.self_storage.repository.EarlyReturnSettlementRepository;
import com.safebox.self_storage.repository.TerminationRequestRepository;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Read-only integration checks against an already migrated SafeBox SQL Server database. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional(readOnly = true)
@EnabledIfEnvironmentVariable(named = "SAFEBOX_SQLSERVER_TEST_URL", matches = "jdbc:sqlserver:.*")
class Sprint3SqlServerIntegrationTest {
    @DynamicPropertySource
    static void sqlServer(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_PASSWORD"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ReportingService reporting;
    @Autowired EarlyReturnPolicyRepository policies;
    @Autowired EarlyReturnSettlementRepository settlements;
    @Autowired TerminationRequestRepository terminations;
    @MockBean Sprint2Access access;

    @Test void uc19MappingsAndMigratedTablesAreReadable() {
        assertNotNull(policies.count());
        assertNotNull(settlements.count());
        assertNotNull(terminations.count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.TERMINATION_REQUESTS') AND name='UX_TERMINATION_EARLY_ACTIVE'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.PAYMENT_TRANSACTIONS') AND name='UX_PAYMENT_UC19_REFUND_REFERENCE'", Integer.class));
    }

    @Test void reportsMatchPersistedCollectionsAndFacilityScope() {
        UUID facility = UUID.fromString(jdbc.queryForObject(
                "SELECT TOP (1) CAST(u.facility_id AS varchar(36)) FROM dbo.PAYMENT_TRANSACTIONS p "
                        + "JOIN dbo.INVOICES i ON i.invoice_id=p.invoice_id "
                        + "JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id=i.contract_id "
                        + "JOIN dbo.STORAGE_UNITS u ON u.unit_id=c.unit_id ORDER BY p.transaction_date DESC", String.class));
        LocalDate end = jdbc.queryForObject("SELECT CAST(MAX(transaction_date) AS date) FROM dbo.PAYMENT_TRANSACTIONS", Date.class).toLocalDate();
        LocalDate start = end.minusDays(30);
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facility));
        when(access.today()).thenReturn(end);
        var filter = new ReportingService.Filters(start, end, facility);
        var summary = reporting.financial(filter);
        var trend = reporting.trend(filter, "DAY");
        var performance = reporting.performance(filter).get(0);
        var operations = reporting.operational(filter);

        BigDecimal trendRent = trend.data().stream().map(ReportingService.TrendPoint::rentalCollections)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal trendDeposit = trend.data().stream().map(ReportingService.TrendPoint::depositCollections)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal trendRefund = trend.data().stream().map(ReportingService.TrendPoint::refundOutflow)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, summary.rentalCollections().compareTo(trendRent));
        assertEquals(0, summary.depositCollections().compareTo(trendDeposit));
        assertEquals(0, summary.refundOutflow().compareTo(trendRefund));
        assertEquals(0, summary.netCashMovement().compareTo(summary.rentalCollections()
                .add(summary.depositCollections()).add(summary.otherCollections())
                .add(summary.unclassifiedCollections()).subtract(summary.refundOutflow())));

        Integer duplicateReferences = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT reference_code,transaction_type,payment_type "
                + "FROM dbo.PAYMENT_TRANSACTIONS WHERE status='SUCCESS' AND reference_code IS NOT NULL "
                + "GROUP BY reference_code,transaction_type,payment_type HAVING COUNT(*)>1) x", Integer.class);
        if (duplicateReferences == 0) {
            String base = " FROM dbo.PAYMENT_TRANSACTIONS p JOIN dbo.INVOICES i ON i.invoice_id=p.invoice_id "
                    + "JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id=i.contract_id "
                    + "JOIN dbo.STORAGE_UNITS u ON u.unit_id=c.unit_id "
                    + "WHERE u.facility_id=? AND p.transaction_date>=? AND p.transaction_date<? "
                    + "AND p.status='SUCCESS' AND p.transaction_type<>'REFUND' ";
            Timestamp from = Timestamp.valueOf(start.atStartOfDay());
            Timestamp until = Timestamp.valueOf(end.plusDays(1).atStartOfDay());
            BigDecimal deposits = jdbc.queryForObject("SELECT COALESCE(SUM(p.amount),0)" + base
                    + "AND p.payment_type='DEPOSIT'", BigDecimal.class, facility, from, until);
            BigDecimal rent = jdbc.queryForObject("SELECT COALESCE(SUM(p.amount),0)" + base
                    + "AND p.payment_type IN ('INITIAL_RENT','REMAINING_RENT','MONTHLY_RENT','RENEWAL')",
                    BigDecimal.class, facility, from, until);
            assertEquals(0, deposits.compareTo(summary.depositCollections()));
            assertEquals(0, rent.compareTo(summary.rentalCollections()));
        }

        Integer occupied = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.STORAGE_UNITS WHERE facility_id=? AND status='OCCUPIED'", Integer.class, facility);
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RENTAL_CONTRACTS c JOIN dbo.STORAGE_UNITS u ON u.unit_id=c.unit_id WHERE u.facility_id=? AND c.status='ACTIVE'", Integer.class, facility);
        assertEquals(occupied, performance.occupiedUnits());
        assertEquals(active.longValue(), performance.activeContracts());
        assertEquals(active.longValue(), operations.activeContracts());
        assertThrows(BusinessException.class, () -> reporting.financial(new ReportingService.Filters(start, end, UUID.randomUUID())));
    }

    @Test @WithMockUser(roles = "FACILITY_MANAGER")
    void fourHttpEndpointsReadSqlServerAndRejectOtherFacility() throws Exception {
        UUID facility = UUID.fromString(jdbc.queryForObject(
                "SELECT TOP (1) CAST(facility_id AS varchar(36)) FROM dbo.STORAGE_UNITS", String.class));
        LocalDate end = jdbc.queryForObject("SELECT CAST(MAX(transaction_date) AS date) FROM dbo.PAYMENT_TRANSACTIONS", Date.class).toLocalDate();
        LocalDate start = end.minusDays(30);
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facility));
        when(access.today()).thenReturn(end);
        for (String path : List.of("financial-summary","revenue-trend","facility-performance","operational-summary")) {
            mvc.perform(get("/api/reports/" + path).param("startDate", start.toString())
                    .param("endDate", end.toString()).param("facilityId", facility.toString()))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/reports/financial-summary").param("startDate",start.toString())
                .param("endDate",end.toString()).param("facilityId",UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }
}
