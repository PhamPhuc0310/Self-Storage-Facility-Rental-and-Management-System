package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.math.*;
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

@SpringBootTest @Transactional
@EnabledIfEnvironmentVariable(named="SAFEBOX_SQLSERVER_TEST_URL",matches="jdbc:sqlserver:.*databaseName=SelfStorageFacilityDB_Test;.*")
class EarlyReturnRefund80SqlServerIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("SAFEBOX_SQLSERVER_TEST_URL"));
        p.add("spring.datasource.username",()->System.getenv("SAFEBOX_SQLSERVER_TEST_USER"));
        p.add("spring.datasource.password",()->System.getenv("SAFEBOX_SQLSERVER_TEST_PASSWORD"));
        p.add("spring.datasource.driver-class-name",()->"com.microsoft.sqlserver.jdbc.SQLServerDriver");
        p.add("spring.jpa.hibernate.ddl-auto",()->"none");p.add("spring.sql.init.mode",()->"never");
    }
    @Autowired JdbcTemplate jdbc;@Autowired EarlyReturnService early;@Autowired ReturnInspectionService returns;
    @Autowired RentalContractRepository contracts;@Autowired EarlyReturnRentSettlement calculator;@Autowired EntityManager em;
    UUID contract=UUID.fromString("20261112-0080-4000-8000-000000000203");
    UUID customer=UUID.fromString("354e5607-b0b4-f111-a965-932c2c6addb7"),manager=UUID.fromString("3b4e5607-b0b4-f111-a965-932c2c6addb7"),staff=UUID.fromString("384e5607-b0b4-f111-a965-932c2c6addb7");
    void as(UUID id,String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @ParameterizedTest @ValueSource(strings={"0","500000","20000000"})
    void realInspectionSettlementOffsetsAndRefundRetriesRollBack(String charge) {
        assertEquals("SelfStorageFacilityDB_Test",jdbc.queryForObject("SELECT DB_NAME()",String.class));
        RentalContract before=contracts.findById(contract).orElseThrow();LocalDate end=before.endDate;
        BigDecimal deduction=new BigDecimal(charge);as(customer,"CUSTOMER");
        LocalDateTime slot=LocalDate.now(Sprint2Access.ZONE).plusDays(1).atTime(17,0);
        var scheduled=returns.scheduleReturnAppointment(customer,contract,new CreateReturnScheduleRequest(contract,slot,"SQL ROLLBACK SYNTHETIC",true,true));
        var request=early.customerList().stream().filter(r->contract.equals(r.contractId)).findFirst().orElseThrow();
        var estimate=early.customerSettlement(request.id);assertTrue(estimate.calculation().estimated());
        as(manager,"FACILITY_MANAGER");returns.assignReturnStaff(manager,scheduled.handoverId(),staff);
        assertThrows(BusinessException.class,()->early.approve(request.id,new EarlyReturnService.ApprovalInput(BigDecimal.ZERO,null)));
        jdbc.update("UPDATE dbo.HANDOVER_RECORDS SET scheduled_at=DATEADD(minute,-2,CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS datetime2)) WHERE handover_id=?",scheduled.handoverId());em.clear();
        as(staff,"FACILITY_STAFF");var inspection=new CompleteReturnInspectionRequest();inspection.setHandoverId(scheduled.handoverId());
        inspection.setCleanlinessStatus("CLEAN");inspection.setAccessReturned(true);inspection.setCustomerConfirmed(true);
        inspection.setConditionStatus(deduction.signum()>0?"DAMAGED":"GOOD");inspection.setEstimatedRepairCost(deduction);
        inspection.setDamageDescription("SYNTHETIC SQL test only");inspection.setCustomerSignature("TEST ONLY");inspection.setStaffSignature("TEST ONLY");
        inspection.setUnitNextStatus("INACTIVE");returns.completeReturnHandoverAndInspection(staff,inspection);em.flush();
        assertThrows(BusinessException.class,()->early.approve(request.id,new EarlyReturnService.ApprovalInput(deduction,"test")));
        as(manager,"FACILITY_MANAGER");var s=early.approve(request.id,new EarlyReturnService.ApprovalInput(deduction,"SYNTHETIC inspection charge"));em.flush();
        var calc=calculator.read(s);assertFalse(calc.estimated());assertEquals(LocalDate.now(Sprint2Access.ZONE),calc.returnDate());
        assertNotEquals(slot.toLocalDate(),calc.returnDate());assertEquals(end,contracts.findById(contract).orElseThrow().endDate);
        BigDecimal unusedDays=BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(calc.returnDate(),end));
        BigDecimal expected=new BigDecimal("4752000").multiply(unusedDays).divide(new BigDecimal("93"),12,RoundingMode.HALF_UP).multiply(new BigDecimal("0.8")).setScale(2,RoundingMode.HALF_UP);
        assertEquals(0,expected.compareTo(s.unusedRentRefund));
        assertThrows(BusinessException.class,()->early.approve(request.id,new EarlyReturnService.ApprovalInput(deduction,"retry")));
        assertThrows(BusinessException.class,()->early.refund(request.id));
        var confirmation=new EarlyReturnRentSettlement.Confirmation("TEST-ONLY-SQL80-"+charge,"CASH",calc.netRefund());
        early.refund(request.id,confirmation);em.flush();
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.PAYMENT_TRANSACTIONS WHERE contract_id=? AND transaction_type='REFUND'",Integer.class,contract);
        early.refund(request.id,confirmation);em.flush();
        assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.PAYMENT_TRANSACTIONS WHERE contract_id=? AND transaction_type='REFUND'",Integer.class,contract));
        assertEquals(calc.remainingDebt(),jdbc.queryForObject("SELECT COALESCE(SUM(total_amount-paid_amount),0) FROM dbo.INVOICES WHERE contract_id=? AND invoice_type<>'DEPOSIT'",BigDecimal.class,contract));
        if(calc.remainingDebt().signum()>0)assertThrows(BusinessException.class,()->returns.completeContractAndFinalizeUnit(manager,contract,null));
        else {returns.completeContractAndFinalizeUnit(manager,contract,null);returns.completeContractAndFinalizeUnit(manager,contract,null);}
    }
}
