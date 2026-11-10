package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.CreateReturnScheduleRequest;
import com.safebox.self_storage.entity.RentalContract;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.RentalContractRepository;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** Real SQL Server, exact Test database only; every test rolls back all writes. */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="SAFEBOX_SQLSERVER_TEST_URL",matches="jdbc:sqlserver:.*databaseName=SelfStorageFacilityDB_Test;.*")
class ReturnSchedulingSqlServerIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("SAFEBOX_SQLSERVER_TEST_URL"));
        p.add("spring.datasource.username",()->System.getenv("SAFEBOX_SQLSERVER_TEST_USER"));
        p.add("spring.datasource.password",()->System.getenv("SAFEBOX_SQLSERVER_TEST_PASSWORD"));
        p.add("spring.datasource.driver-class-name",()->"com.microsoft.sqlserver.jdbc.SQLServerDriver");
        p.add("spring.jpa.hibernate.ddl-auto",()->"none");
        p.add("spring.sql.init.mode",()->"never");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired RentalContractRepository contracts;
    @Autowired ReturnInspectionService returns;
    @Autowired EarlyReturnService earlyReturns;
    @Autowired EntityManager em;
    RentalContract contract;
    @BeforeEach void ownedTestContract() {
        assertEquals("SelfStorageFacilityDB_Test",jdbc.queryForObject("SELECT DB_NAME()",String.class));
        UUID id=jdbc.queryForObject("SELECT TOP(1) c.contract_id FROM dbo.RENTAL_CONTRACTS c JOIN dbo.USERS u ON u.user_id=c.customer_id "
                + "WHERE u.email='customer1@safebox.vn' AND c.status='ACTIVE' AND c.end_date>DATEADD(day,3,CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS date)) "
                + "AND NOT EXISTS(SELECT 1 FROM dbo.HANDOVER_RECORDS h WHERE h.contract_id=c.contract_id AND h.handover_type='RETURN' AND h.status IN ('SCHEDULED','COMPLETED')) "
                + "AND NOT EXISTS(SELECT 1 FROM dbo.TERMINATION_REQUESTS t WHERE t.contract_id=c.contract_id AND t.termination_type='EARLY_RETURN' AND t.status IN ('REQUESTED','SETTLEMENT_APPROVED','REFUNDED')) "
                + "AND NOT EXISTS(SELECT 1 FROM dbo.DEPOSIT_REFUNDS r WHERE r.contract_id=c.contract_id AND r.status IN ('REQUESTED','UNDER_REVIEW','APPROVED')) ORDER BY c.end_date DESC",
                (rs,n)->UUID.fromString(rs.getString(1)));
        contract=contracts.findById(id).orElseThrow(); authenticate(contract.customerId,"CUSTOMER");
    }
    void authenticate(UUID id,String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,
                List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    @AfterEach void clearSecurity(){SecurityContextHolder.clearContext();}
    LocalDate tomorrow(){return LocalDate.now(Sprint2Access.ZONE).plusDays(1);}
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM dbo."+table+" WHERE contract_id=?",Integer.class,contract.contractId);}
    void schedule(LocalDate day){returns.scheduleReturnAppointment(contract.customerId,contract.contractId,
            new CreateReturnScheduleRequest(contract.contractId,day.atTime(10,0),"SQL ROLLBACK ONLY",true,true));em.flush();}
    @Test void earlyAppointmentAndPendingFinancialRequestPersistAtomicallyWithoutMoneyOrTermChanges() {
        LocalDate end=contract.endDate;int payments=count("PAYMENT_TRANSACTIONS"),invoices=count("INVOICES");
        schedule(tomorrow());
        assertEquals(1,count("TERMINATION_REQUESTS"));assertEquals(0,count("EARLY_RETURN_SETTLEMENTS"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.HANDOVER_RECORDS WHERE contract_id=? AND handover_type='RETURN' AND status='SCHEDULED'",Integer.class,contract.contractId));
        assertEquals(payments,count("PAYMENT_TRANSACTIONS"));assertEquals(invoices,count("INVOICES"));
        em.clear();var persisted=contracts.findById(contract.contractId).orElseThrow();
        assertEquals(end,persisted.endDate);assertEquals("ACTIVE",persisted.status);assertNull(persisted.actualEndDate);
        assertEquals("OCCUPIED",jdbc.queryForObject("SELECT status FROM dbo.STORAGE_UNITS WHERE unit_id=?",String.class,contract.unitId));
        assertThrows(BusinessException.class,()->schedule(tomorrow()));
    }
    @ParameterizedTest @ValueSource(strings={"ON_TIME","AFTER_END","OVERDUE"})
    void otherDateScenariosDoNotCreateEarlyFinancialRequests(String scenario) {
        LocalDate end=tomorrow();if("OVERDUE".equals(scenario))end=tomorrow().minusDays(3);
        contract.startDate=end.minusDays(3);contract.endDate=end;
        contract.status="OVERDUE".equals(scenario)?"OVERDUE":"ACTIVE";contracts.saveAndFlush(contract);
        LocalDate scheduled="ON_TIME".equals(scenario)?end:tomorrow().plusDays(1);
        schedule(scheduled);assertEquals(0,count("TERMINATION_REQUESTS"));assertEquals(end,contract.endDate);
    }
    @Test void linkedRequestCannotBeRejectedToBypassClosureSettlement() {
        schedule(tomorrow());
        UUID request=jdbc.queryForObject("SELECT termination_id FROM dbo.TERMINATION_REQUESTS WHERE contract_id=? AND termination_type='EARLY_RETURN'",(rs,n)->UUID.fromString(rs.getString(1)),contract.contractId);
        UUID manager=jdbc.queryForObject("SELECT TOP(1) u.user_id FROM dbo.USERS u JOIN dbo.ROLES r ON r.role_id=u.role_id JOIN dbo.USER_FACILITY_ASSIGNMENTS a ON a.user_id=u.user_id JOIN dbo.STORAGE_UNITS s ON s.facility_id=a.facility_id WHERE r.role_name='FACILITY_MANAGER' AND a.status='ACTIVE' AND s.unit_id=?",(rs,n)->UUID.fromString(rs.getString(1)),contract.unitId);
        authenticate(manager,"FACILITY_MANAGER");
        assertThrows(BusinessException.class,()->earlyReturns.reject(request,new EarlyReturnService.RejectInput("rollback test")));
        assertEquals("REQUESTED",jdbc.queryForObject("SELECT status FROM dbo.TERMINATION_REQUESTS WHERE termination_id=?",String.class,request));
    }
}
