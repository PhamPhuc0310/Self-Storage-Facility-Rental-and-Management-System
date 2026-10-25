package com.safebox.self_storage.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.safebox.self_storage.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportingServiceTest {
    JdbcTemplate db;
    Sprint2Access access;
    ReportingService service;
    UUID facility = UUID.randomUUID(), other = UUID.randomUUID(), unit = UUID.randomUUID(), contract = UUID.randomUUID(), invoice = UUID.randomUUID();
    ReportingService.Filters period = new ReportingService.Filters(LocalDate.of(2026,10,1), LocalDate.of(2026,10,31),null);

    @BeforeEach void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:reports_"+UUID.randomUUID()+";MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(source);
        db.execute("CREATE SCHEMA dbo");
        db.execute("CREATE TABLE dbo.STORAGE_UNITS(unit_id UUID PRIMARY KEY, facility_id UUID, status VARCHAR(30))");
        db.execute("CREATE TABLE dbo.RENTAL_CONTRACTS(contract_id UUID PRIMARY KEY, unit_id UUID, status VARCHAR(30), end_date DATE)");
        db.execute("CREATE TABLE dbo.INVOICES(invoice_id UUID PRIMARY KEY, contract_id UUID, invoice_type VARCHAR(30), total_amount DECIMAL(18,2), paid_amount DECIMAL(18,2), status VARCHAR(30), issued_at TIMESTAMP, due_date TIMESTAMP)");
        db.execute("CREATE TABLE dbo.PAYMENT_TRANSACTIONS(transaction_id UUID PRIMARY KEY, invoice_id UUID, transaction_type VARCHAR(30), payment_type VARCHAR(30), amount DECIMAL(18,2), status VARCHAR(30), transaction_date TIMESTAMP)");
        db.execute("CREATE TABLE dbo.DEPOSIT_REFUNDS(refund_id UUID PRIMARY KEY, facility_id UUID, status VARCHAR(30), completed_at TIMESTAMP, approved_amount DECIMAL(18,2), refund_transaction_reference VARCHAR(100))");
        db.execute("ALTER TABLE dbo.PAYMENT_TRANSACTIONS ADD reference_code VARCHAR(100)");
        db.execute("CREATE TABLE dbo.HANDOVER_RECORDS(handover_id UUID PRIMARY KEY, unit_id UUID, status VARCHAR(30), handover_type VARCHAR(30), actual_at TIMESTAMP)");
        db.update("INSERT INTO dbo.STORAGE_UNITS VALUES (?,?,?)",unit,facility,"OCCUPIED");
        db.update("INSERT INTO dbo.RENTAL_CONTRACTS VALUES (?,?,?,?)",contract,unit,"ACTIVE",java.sql.Date.valueOf("2026-11-15"));
        db.update("INSERT INTO dbo.INVOICES VALUES (?,?,?,?,?,?,?,?)",invoice,contract,"INITIAL_RENT",1000,700,"PARTIALLY_PAID",
                java.sql.Timestamp.valueOf("2026-10-01 00:00:00"),java.sql.Timestamp.valueOf("2026-10-15 00:00:00"));
        access=mock(Sprint2Access.class);
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facility));
        when(access.today()).thenReturn(LocalDate.of(2026,10,23));
        service=new ReportingService(access,new NamedParameterJdbcTemplate(source));
    }
    void payment(String type,String kind,int amount,String status,String at) {
        db.update("INSERT INTO dbo.PAYMENT_TRANSACTIONS(transaction_id,invoice_id,transaction_type,payment_type,amount,status,transaction_date) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),invoice,kind,type,amount,status,java.sql.Timestamp.valueOf(at));
    }
    @Test void confirmedPaymentsAreSeparatedAndBounded() {
        payment("INITIAL_RENT","INITIAL_PAYMENT",500,"SUCCESS","2026-10-01 00:00:00");
        payment("DEPOSIT","INITIAL_PAYMENT",200,"SUCCESS","2026-10-31 23:59:59");
        payment("DEPOSIT","REFUND",50,"SUCCESS","2026-10-20 10:00:00");
        payment("INITIAL_RENT","INITIAL_PAYMENT",999,"PENDING","2026-10-05 10:00:00");
        payment("INITIAL_RENT","INITIAL_PAYMENT",999,"FAILED","2026-10-05 10:00:00");
        payment("INITIAL_RENT","INITIAL_PAYMENT",999,"SUCCESS","2026-11-01 00:00:00");
        var result=service.financial(period);
        assertEquals(new BigDecimal("500.00"),result.rentalCollections());
        assertEquals(new BigDecimal("200.00"),result.depositCollections());
        assertEquals(new BigDecimal("50.00"),result.refundOutflow());
        assertEquals(new BigDecimal("650.00"),result.netCashMovement());
        assertEquals(new BigDecimal("300.00"),result.outstandingReceivables());
        assertEquals(3,service.trend(period,"DAY").data().size());
        assertEquals(1,service.trend(period,"MONTH").data().size());
    }
    @Test void legacyRefundWithoutTransactionIsCountedOnce() {
        db.update("INSERT INTO dbo.DEPOSIT_REFUNDS VALUES (?,?,?,?,?,?)",UUID.randomUUID(),facility,"COMPLETED",
                java.sql.Timestamp.valueOf("2026-10-20 10:00:00"),100,"LEGACY-1");
        assertEquals(new BigDecimal("100.00"),service.financial(period).refundOutflow());
        assertEquals(new BigDecimal("100.00"),service.trend(period,"MONTH").data().get(0).refundOutflow());
    }
    @Test void duplicateSuccessfulReferenceIsCountedOnce() {
        db.update("INSERT INTO dbo.PAYMENT_TRANSACTIONS VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),invoice,
                "INITIAL_PAYMENT","INITIAL_RENT",500,"SUCCESS",java.sql.Timestamp.valueOf("2026-10-08 10:00:00"),"RETRY-1");
        db.update("INSERT INTO dbo.PAYMENT_TRANSACTIONS VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),invoice,
                "INITIAL_PAYMENT","INITIAL_RENT",500,"SUCCESS",java.sql.Timestamp.valueOf("2026-10-08 10:01:00"),"RETRY-1");
        assertEquals(new BigDecimal("500.00"),service.financial(period).rentalCollections());
    }
    @Test void facilityScopeAndOccupancyAreEnforced() {
        db.update("INSERT INTO dbo.STORAGE_UNITS VALUES (?,?,?)",UUID.randomUUID(),facility,"AVAILABLE");
        db.update("INSERT INTO dbo.STORAGE_UNITS VALUES (?,?,?)",UUID.randomUUID(),facility,"MAINTENANCE");
        var row=service.performance(period).get(0);
        assertEquals(3,row.totalUnits()); assertEquals(1,row.occupiedUnits());
        assertEquals(1,row.maintenanceUnits());
        assertEquals(new BigDecimal("50.00"),row.occupancyRate());
        assertThrows(BusinessException.class,() -> service.financial(new ReportingService.Filters(period.startDate(),period.endDate(),other)));
    }
    @Test void emptyPeriodAndUnassignedOperationsManagerReturnZeros() {
        when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");
        when(access.facilities()).thenReturn(List.of());
        assertEquals(BigDecimal.ZERO,service.financial(period).rentalCollections());
        assertTrue(service.performance(period).isEmpty());
        assertEquals(0,service.operational(period).activeContracts());
    }
}
