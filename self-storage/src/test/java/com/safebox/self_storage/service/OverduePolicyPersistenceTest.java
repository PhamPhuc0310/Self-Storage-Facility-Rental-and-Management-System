package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.RentalPolicy;
import com.safebox.self_storage.repository.RentalPolicyRepository;
import com.safebox.self_storage.repository.ActivityLogRepository;
import com.safebox.self_storage.dto.manager.OverduePolicyProposalRequest;
import com.safebox.self_storage.exception.BusinessException;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.concurrent.*;
import java.util.List;
import static org.mockito.Mockito.*;
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
@Import(OverduePolicyGovernanceService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:overdue_policy;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OverduePolicyPersistenceTest {
    @Autowired RentalPolicyRepository policies;
    @Autowired OverduePolicyGovernanceService governance;
    @Autowired ActivityLogRepository logs;
    @MockBean Sprint2Access access;
    final UUID facility = UUID.randomUUID(), approver = UUID.randomUUID();
    final LocalDate start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 31);

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentGlobalActivationsSerializeOnStableAnchor() throws Exception {
        RentalPolicy anchor=policy("ACTIVE",true,start,null);anchor.setFacilityId(null);anchor.setOverdueVersion(1);
        policies.saveAndFlush(anchor);
        when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");when(access.currentId()).thenReturn(approver);
        when(access.today()).thenReturn(end);when(access.now()).thenReturn(end.atStartOfDay());
        var request=new OverduePolicyProposalRequest(anchor.getPolicyId(),2,"PERCENT_DAILY",new BigDecimal("0.02"),BigDecimal.ZERO,end.plusDays(1),null,"Điều chỉnh chính sách toàn hệ thống");
        var startSignal=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<String> activate=()->{startSignal.await();try{governance.activateGlobal(request);return "CREATED";}catch(BusinessException ex){return ex.status().toString();}};
        try {
            var first=pool.submit(activate);var second=pool.submit(activate);startSignal.countDown();
            List<String> results=List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter("CREATED"::equals).count());
            assertEquals(1,results.stream().filter(x->x.startsWith("409")).count());
            assertEquals(2,policies.findByFacilityId(null).size());
            assertEquals(end,policies.findById(anchor.getPolicyId()).orElseThrow().getEffectiveTo());
            assertEquals(1,logs.findAll().stream().filter(x->"GLOBAL_OVERDUE_POLICY_ACTIVATE".equals(x.getActionType())).count());
        } finally {pool.shutdownNow();policies.deleteAll(policies.findByFacilityId(null));}
    }

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
