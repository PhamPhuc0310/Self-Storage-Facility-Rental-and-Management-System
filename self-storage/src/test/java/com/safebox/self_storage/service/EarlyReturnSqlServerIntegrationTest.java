package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.RentalContract;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.RentalContractRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Assumptions;
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

/** Opt-in rollback-only request persistence test; no policy or refund records are created. */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SAFEBOX_SQLSERVER_TEST_URL", matches = "jdbc:sqlserver:.*")
class EarlyReturnSqlServerIntegrationTest {
    @DynamicPropertySource
    static void sqlServer(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_PASSWORD"));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired RentalContractRepository contracts;
    @Autowired EarlyReturnService earlyReturns;
    @Autowired EntityManager entityManager;

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void ownedActiveContractPersistsRequestWithoutChangingReturnLifecycle() {
        List<UUID> eligible = jdbc.query("SELECT TOP (1) c.contract_id FROM dbo.RENTAL_CONTRACTS c "
                + "WHERE c.status='ACTIVE' AND c.end_date > DATEADD(day,3,CAST(SYSDATETIME() AS date)) "
                + "AND NOT EXISTS (SELECT 1 FROM dbo.HANDOVER_RECORDS h WHERE h.contract_id=c.contract_id "
                + "AND h.handover_type='RETURN' AND h.status='SCHEDULED') "
                + "AND NOT EXISTS (SELECT 1 FROM dbo.TERMINATION_REQUESTS t WHERE t.contract_id=c.contract_id "
                + "AND t.termination_type='EARLY_RETURN' AND t.status IN ('REQUESTED','SETTLEMENT_APPROVED','REFUNDED')) "
                + "AND NOT EXISTS (SELECT 1 FROM dbo.DEPOSIT_REFUNDS r WHERE r.contract_id=c.contract_id "
                + "AND r.status IN ('REQUESTED','UNDER_REVIEW','APPROVED')) "
                + "ORDER BY c.end_date", (rs,n) -> UUID.fromString(rs.getString(1)));
        Assumptions.assumeFalse(eligible.isEmpty(), "No eligible active contract exists in this SQL Server database.");
        RentalContract contract = contracts.findById(eligible.get(0)).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                contract.customerId, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        LocalDateTime proposed = LocalDateTime.now(Sprint2Access.ZONE).plusDays(2).withSecond(0).withNano(0);
        var created = earlyReturns.request(contract.contractId,
                new EarlyReturnService.RequestInput(proposed,"SQL rollback verification",null));
        entityManager.flush();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TERMINATION_REQUESTS WHERE termination_id=? AND status='REQUESTED'",
                Integer.class, created.id));
        assertEquals(created.id, earlyReturns.customerGet(created.id).id);
        assertEquals("ACTIVE", contracts.findById(contract.contractId).orElseThrow().status);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.HANDOVER_RECORDS WHERE contract_id=? AND handover_type='RETURN' AND status='SCHEDULED'",
                Integer.class, contract.contractId));
        assertThrows(BusinessException.class, () -> earlyReturns.request(contract.contractId,
                new EarlyReturnService.RequestInput(proposed,"duplicate",null)));
    }
}
