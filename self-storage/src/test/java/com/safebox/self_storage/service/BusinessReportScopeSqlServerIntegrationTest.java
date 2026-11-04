package com.safebox.self_storage.service;

import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.UserFacilityAssignmentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

/** Rollback-only UC24 grant -> report -> revoke check on the isolated SQL Server. */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SAFEBOX_SQLSERVER_TEST_URL", matches = "jdbc:sqlserver:.*")
class BusinessReportScopeSqlServerIntegrationTest {
    @DynamicPropertySource
    static void sqlServer(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_PASSWORD"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired BusinessReportScopeService scopes;
    @Autowired ReportingService reporting;
    @Autowired UserFacilityAssignmentRepository assignments;
    @Autowired Sprint2Access access;

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    private void actor(UUID id, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                id, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test void administratorGrantsBoundedBusinessScopeAndRevocationRemovesReportAccess() {
        UUID admin = jdbc.queryForObject("SELECT user_id FROM dbo.USERS WHERE email='admin@safebox.vn'", UUID.class);
        UUID business = jdbc.queryForObject("SELECT user_id FROM dbo.USERS WHERE email='business@safebox.vn'", UUID.class);
        UUID manager = jdbc.queryForObject("SELECT user_id FROM dbo.USERS WHERE email='manager.q7@safebox.vn'", UUID.class);
        UUID facility = UUID.fromString("414E5607-B0B4-F111-A965-932C2C6ADDB7");
        UUID other = UUID.fromString("404E5607-B0B4-F111-A965-932C2C6ADDB7");
        var period = new ReportingService.Filters(LocalDate.of(2026,10,1), LocalDate.of(2026,10,27), facility);
        actor(admin, "SYSTEM_ADMIN");
        if (assignments.isUserAssignedToFacility(business, facility, access.today()))
            scopes.revoke(business, facility);
        actor(business, "BUSINESS_OPERATIONS_MANAGER");
        assertFalse(assignments.isUserAssignedToFacility(business, facility, access.today()),
                "Fixture must begin without a business-scope grant");
        assertThrows(BusinessException.class, () -> reporting.financial(period));
        actor(manager, "FACILITY_MANAGER");
        assertThrows(BusinessException.class, () -> scopes.grant(business, facility));
        var managerSummary = reporting.financial(period);
        actor(admin, "SYSTEM_ADMIN");
        assertEquals(List.of(facility), scopes.grant(business, facility));
        assertEquals(List.of(facility), scopes.grant(business, facility));
        actor(business, "BUSINESS_OPERATIONS_MANAGER");
        var businessSummary = reporting.financial(period);
        assertEquals(managerSummary, businessSummary);
        assertTrue(businessSummary.netCashMovement().signum() > 0);
        assertThrows(BusinessException.class, () -> reporting.financial(new ReportingService.Filters(
                period.startDate(), period.endDate(), other)));
        actor(admin, "SYSTEM_ADMIN");
        assertTrue(scopes.revoke(business, facility).isEmpty());
        actor(business, "BUSINESS_OPERATIONS_MANAGER");
        assertThrows(BusinessException.class, () -> reporting.financial(period));
        assertThrows(BusinessException.class, () -> reporting.financial(new ReportingService.Filters(
                period.startDate(), period.endDate(), null)));
    }
}
