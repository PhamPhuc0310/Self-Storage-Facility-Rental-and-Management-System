package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.support.StaffFacilityAssignmentRequest;
import com.safebox.self_storage.repository.UserFacilityAssignmentRepository;
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

/** Rollback-only regression for SQL Server CK_UFA_date_range on same-day staff revocation. */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SAFEBOX_SQLSERVER_TEST_URL", matches = "jdbc:sqlserver:.*")
class StaffScopeSqlServerIntegrationTest {
    @DynamicPropertySource
    static void sqlServer(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("SAFEBOX_SQLSERVER_TEST_PASSWORD"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffScopeService scopes;
    @Autowired UserFacilityAssignmentRepository assignments;
    @Autowired Sprint2Access access;

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void sameDayGrantAndRevokePreserveDateConstraintAndRemoveEffectiveScope() {
        UUID admin = jdbc.queryForObject("SELECT user_id FROM dbo.USERS WHERE email='admin@safebox.vn'", UUID.class);
        UUID staff = jdbc.queryForObject("SELECT user_id FROM dbo.USERS WHERE email='staff.q8@safebox.vn'", UUID.class);
        UUID facility = UUID.fromString("404E5607-B0B4-F111-A965-932C2C6ADDB7");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                admin, null, List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))));
        if (assignments.isUserAssignedToFacility(staff, facility, access.today()))
            scopes.revoke(staff, facility);

        scopes.grant(staff, new StaffFacilityAssignmentRequest(facility));
        assertTrue(assignments.isUserAssignedToFacility(staff, facility, access.today()));
        scopes.revoke(staff, facility);
        assertFalse(assignments.isUserAssignedToFacility(staff, facility, access.today()));
        Integer invalidDates = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.USER_FACILITY_ASSIGNMENTS "
                + "WHERE user_id=? AND facility_id=? AND end_date < start_date", Integer.class, staff, facility);
        assertEquals(0, invalidDates);
    }
}
