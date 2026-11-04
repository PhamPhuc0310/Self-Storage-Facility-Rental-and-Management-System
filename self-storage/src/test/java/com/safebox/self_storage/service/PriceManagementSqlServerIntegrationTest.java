package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.manager.PriceScheduleRequest;
import com.safebox.self_storage.entity.PricingPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="SAFEBOX_SQLSERVER_TEST_URL",matches="jdbc:sqlserver:.*SelfStorageFacilityDB_Test.*")
class PriceManagementSqlServerIntegrationTest {
    @DynamicPropertySource static void db(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("SAFEBOX_SQLSERVER_TEST_URL"));
        p.add("spring.datasource.username",()->System.getenv("SAFEBOX_SQLSERVER_TEST_USER"));
        p.add("spring.datasource.password",()->System.getenv("SAFEBOX_SQLSERVER_TEST_PASSWORD"));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PriceManagementService service;
    UUID facility=UUID.fromString("ede766ca-d3d3-460d-b027-1220305f6e98");
    @BeforeEach void identityAndRole() {
        assertEquals("SelfStorageFacilityDB_Test",jdbc.queryForObject("SELECT DB_NAME()",String.class));
        UUID business=jdbc.queryForObject("SELECT user_id FROM dbo.USERS WHERE email='business@safebox.vn'",UUID.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(business,null,
                List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_OPERATIONS_MANAGER"))));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void draftActivationAndRetirementArePersistedWithoutChangingSignedContracts() {
        Integer before=jdbc.queryForObject("SELECT CHECKSUM_AGG(BINARY_CHECKSUM(contract_id, monthly_rental_fee, deposit_amount)) FROM dbo.RENTAL_CONTRACTS",Integer.class);
        LocalDate from=LocalDate.now(Sprint2Access.ZONE).plusYears(1);
        PricingPolicy draft=service.createDraft(facility,new PriceScheduleRequest(1,new BigDecimal("1500000"),from,from.plusDays(30)));
        assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM dbo.PRICING_POLICIES WHERE pricing_id=?",String.class,draft.getPricingId()));
        service.activate(facility,draft.getPricingId());
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM dbo.PRICING_POLICIES WHERE pricing_id=?",String.class,draft.getPricingId()));
        service.retire(facility,draft.getPricingId());
        assertEquals("RETIRED",jdbc.queryForObject("SELECT status FROM dbo.PRICING_POLICIES WHERE pricing_id=?",String.class,draft.getPricingId()));
        assertEquals(before,jdbc.queryForObject("SELECT CHECKSUM_AGG(BINARY_CHECKSUM(contract_id, monthly_rental_fee, deposit_amount)) FROM dbo.RENTAL_CONTRACTS",Integer.class));
    }
}
