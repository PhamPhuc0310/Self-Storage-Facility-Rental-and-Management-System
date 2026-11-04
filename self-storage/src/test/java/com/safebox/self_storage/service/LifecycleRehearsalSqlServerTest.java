package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.CompleteReturnInspectionRequest;
import com.safebox.self_storage.entity.ActivityLog;
import com.safebox.self_storage.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic assertions only; every write rolls back inside the existing rehearsal DB. */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="SAFEBOX_SQLSERVER_TEST_URL",matches="(?i)jdbc:sqlserver:.*databaseName=SafeBox_FinalRehearsal_20261030;.*")
class LifecycleRehearsalSqlServerTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("SAFEBOX_SQLSERVER_TEST_URL"));
        p.add("spring.datasource.username",()->System.getenv("SAFEBOX_SQLSERVER_TEST_USER"));
        p.add("spring.datasource.password",()->System.getenv("SAFEBOX_SQLSERVER_TEST_PASSWORD"));
    }
    @Autowired JdbcTemplate jdbc; @Autowired EntityManager em;
    @Autowired ReturnInspectionService returns; @Autowired RecoveryConfirmationService recovery;
    static final UUID CUSTOMER=UUID.fromString("374E5607-B0B4-F111-A965-932C2C6ADDB7");
    static final UUID STAFF=UUID.fromString("384E5607-B0B4-F111-A965-932C2C6ADDB7");
    static final UUID MANAGER=UUID.fromString("3B4E5607-B0B4-F111-A965-932C2C6ADDB7");
    static final UUID CONTRACT=UUID.fromString("00000000-0000-4000-8000-00000000D403");
    static final UUID UNIT=UUID.fromString("00000000-0000-4000-8000-00000000D203");
    @BeforeEach void guard() {
        assertEquals("SafeBox_FinalRehearsal_20261030",jdbc.queryForObject("SELECT DB_NAME()",String.class));
    }
    void prepareExpired(int days) {
        assertEquals(1,jdbc.update("UPDATE dbo.RENTAL_CONTRACTS SET start_date=DATEADD(day,-45,CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS date)), end_date=DATEADD(day,?,CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS date)) WHERE contract_id=? AND status='ACTIVE'",-days,CONTRACT.toString()));
    }
    @Test void completedSyntheticReturnPreservesDebtAndRequiresReadiness() {
        prepareExpired(4);
        UUID invoice=UUID.randomUUID(), handover=UUID.randomUUID();
        jdbc.update("INSERT dbo.INVOICES(invoice_id,invoice_number,contract_id,invoice_type,description,subtotal,penalty_amount,discount_amount,total_amount,paid_amount,due_date,status) VALUES(?,?,?,'MONTHLY_RENT','ROLLBACK TEST',100000,0,0,100000,0,SYSDATETIME(),'UNPAID')",invoice.toString(),"ROLLBACK-"+invoice,CONTRACT.toString());
        jdbc.update("INSERT dbo.HANDOVER_RECORDS(handover_id,contract_id,unit_id,staff_id,handover_type,scheduled_at,status,note) VALUES(?,?,?,?,'RETURN',DATEADD(minute,-5,SYSDATETIME()),'SCHEDULED','ROLLBACK TEST')",handover.toString(),CONTRACT.toString(),UNIT.toString(),STAFF.toString());
        CompleteReturnInspectionRequest req=new CompleteReturnInspectionRequest(); req.setHandoverId(handover);
        req.setAccessReturned(true); req.setCustomerConfirmed(true); req.setConditionStatus("GOOD");
        req.setCustomerSignature("Synthetic test"); req.setStaffSignature("Synthetic test");
        returns.completeReturnHandoverAndInspection(STAFF,req); em.flush();
        assertEquals("RETURNED",jdbc.queryForObject("SELECT status FROM dbo.RENTAL_CONTRACTS WHERE contract_id=?",String.class,CONTRACT.toString()));
        assertEquals("INACTIVE",jdbc.queryForObject("SELECT status FROM dbo.STORAGE_UNITS WHERE unit_id=?",String.class,UNIT.toString()));
        assertEquals(0,jdbc.queryForObject("SELECT CAST(readiness_verified AS int) FROM dbo.STORAGE_UNITS WHERE unit_id=?",Integer.class,UNIT.toString()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.INVOICES i JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id=i.contract_id WHERE i.invoice_id=? AND c.customer_id=? AND i.status='UNPAID' AND i.total_amount=100000 AND i.paid_amount=0",Integer.class,invoice.toString(),CUSTOMER.toString()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.INVOICES i JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id=i.contract_id WHERE i.invoice_id=? AND c.customer_id<>?",Integer.class,invoice.toString(),CUSTOMER.toString()));
    }
    @Test void syntheticRecoveryRecordsActorAndKeepsUnitUnavailable() {
        prepareExpired(31);
        LocalDateTime completed=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusMinutes(1);
        var req=new RecoveryConfirmationService.Request("ROLLBACK-TEST-RECORD","ROLLBACK-TEST-BASIS",completed,true,true);
        var result=recovery.confirm(MANAGER,CONTRACT,req); em.flush();
        assertEquals("RETURNED",result.contractStatus()); assertEquals("MAINTENANCE",result.unitStatus());
        assertTrue(recovery.confirm(MANAGER,CONTRACT,req).alreadyRecorded());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.ACTIVITY_LOGS WHERE action_type='CONFIRM_UNIT_RECOVERY' AND entity_id=? AND user_id=? AND description LIKE '%ROLLBACK-TEST-RECORD%'",Integer.class,CONTRACT.toString(),MANAGER.toString()));
        assertEquals(0,jdbc.queryForObject("SELECT CAST(readiness_verified AS int) FROM dbo.STORAGE_UNITS WHERE unit_id=?",Integer.class,UNIT.toString()));
    }
    @Test void thirtyDaysDoesNotReleaseUnitAndStaffCannotConfirmRecovery() {
        prepareExpired(30);
        assertEquals("OCCUPIED",jdbc.queryForObject("SELECT status FROM dbo.STORAGE_UNITS WHERE unit_id=?",String.class,UNIT.toString()));
        var req=new RecoveryConfirmationService.Request("ROLLBACK-TEST-RECORD","ROLLBACK-TEST-BASIS",
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusMinutes(1),true,true);
        assertThrows(BusinessException.class,()->recovery.confirm(STAFF,CONTRACT,req));
        assertEquals("OCCUPIED",jdbc.queryForObject("SELECT status FROM dbo.STORAGE_UNITS WHERE unit_id=?",String.class,UNIT.toString()));
    }
}
