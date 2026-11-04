package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.RentalPolicy;
import com.safebox.self_storage.repository.RentalPolicyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:overdue_policy;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OverduePolicyPersistenceTest {
    @Autowired RentalPolicyRepository policies;
    final UUID facility = UUID.randomUUID(), approver = UUID.randomUUID();
    final LocalDate start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 31);

    @Test void effectiveRangeIsInclusiveAndUnapprovedOrInactivePoliciesAreExcluded() {
        RentalPolicy approved = policy("ACTIVE", true, start, end);
        RentalPolicy unapproved = policy("ACTIVE", false, start, end);
        RentalPolicy inactive = policy("INACTIVE", true, start, end);
        policies.saveAndFlush(approved); policies.saveAndFlush(unapproved); policies.saveAndFlush(inactive);
        assertEquals(1, policies.findApplicableOverduePolicies(facility, start).size());
        assertEquals(1, policies.findApplicableOverduePolicies(facility, end).size());
        assertTrue(policies.findApplicableOverduePolicies(facility, start.minusDays(1)).isEmpty());
        assertTrue(policies.findApplicableOverduePolicies(facility, end.plusDays(1)).isEmpty());
        assertTrue(policies.findApplicableOverduePolicies(UUID.randomUUID(), start).isEmpty());
    }

    private RentalPolicy policy(String status, boolean approved, LocalDate from, LocalDate to) {
        RentalPolicy p = new RentalPolicy(); p.setFacilityId(facility);
        p.setRenewalOpenDays(14); p.setPriorityDeadlineDays(3);
        p.setRenewalPaymentHours(24); p.setReservationHoldMinutes(30);
        p.setOverdueGraceDays(0); p.setDailyPenaltyRate(new BigDecimal("0.05"));
        p.setFixedPenaltyAmount(BigDecimal.ZERO); p.setPenaltyType("PERCENT_DAILY");
        p.setEffectiveFrom(from); p.setEffectiveTo(to); p.setStatus(status);
        if (approved) { p.setOverdueApprovedAt(from.atStartOfDay()); p.setOverdueApprovedBy(approver); }
        return p;
    }
}
