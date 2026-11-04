package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.manager.OverduePolicyProposalRequest;
import com.safebox.self_storage.entity.OverduePolicyProposal;
import com.safebox.self_storage.entity.RentalPolicy;
import com.safebox.self_storage.repository.RentalPolicyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
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

@SpringBootTest @Transactional
@EnabledIfEnvironmentVariable(named="SAFEBOX_SQLSERVER_TEST_URL",matches="jdbc:sqlserver:.*SelfStorageFacilityDB_Test.*")
class OverduePolicyGovernanceSqlServerIntegrationTest {
    @DynamicPropertySource static void db(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("SAFEBOX_SQLSERVER_TEST_URL"));
        p.add("spring.datasource.username",()->System.getenv("SAFEBOX_SQLSERVER_TEST_USER"));
        p.add("spring.datasource.password",()->System.getenv("SAFEBOX_SQLSERVER_TEST_PASSWORD"));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired OverduePolicyGovernanceService service;
    @Autowired RentalPolicyRepository policies;
    UUID facility=UUID.fromString("414E5607-B0B4-F111-A965-932C2C6ADDB7");
    UUID base=UUID.fromString("00000000-0000-4000-8000-000000001018");
    UUID manager,business;
    @BeforeEach void identity() {
        assertEquals("SelfStorageFacilityDB_Test",jdbc.queryForObject("SELECT DB_NAME()",String.class));
        manager=jdbc.queryForObject("SELECT user_id FROM dbo.USERS WHERE email='manager.q7@safebox.vn'",UUID.class);
        business=jdbc.queryForObject("SELECT user_id FROM dbo.USERS WHERE email='business@safebox.vn'",UUID.class);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void as(UUID id,String role) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,
            List.of(new SimpleGrantedAuthority("ROLE_"+role)))); }
    OverduePolicyProposalRequest request(LocalDate start) {
        return new OverduePolicyProposalRequest(base,4,"PERCENT_DAILY",new BigDecimal("0.0400"),BigDecimal.ZERO,
                start,null,"Đề xuất điều chỉnh có kiểm tra");
    }
    @Test void futureApprovalCreatesVersionWithoutEarlyApplicationOrHistoricalPenaltyMutation() {
        Integer penaltyCount=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.CONTRACT_PENALTIES",Integer.class);
        Integer invoiceCount=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.INVOICES",Integer.class);
        Integer penaltyChecksum=jdbc.queryForObject("SELECT CHECKSUM_AGG(BINARY_CHECKSUM(penalty_id,policy_id,penalty_amount,paid_amount)) FROM dbo.CONTRACT_PENALTIES",Integer.class);
        Integer invoiceChecksum=jdbc.queryForObject("SELECT CHECKSUM_AGG(BINARY_CHECKSUM(invoice_id,total_amount,penalty_amount,status)) FROM dbo.INVOICES",Integer.class);
        LocalDate today=LocalDate.now(Sprint2Access.ZONE);
        // The isolated test database can retain a previously approved future version.
        // Propose after it so this test exercises supersession without overlapping two approved windows.
        int nextVersion=policies.findByFacilityId(facility).stream()
                .map(RentalPolicy::getOverdueVersion).filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0)+1;
        LocalDate latestStart=policies.findByFacilityId(facility).stream()
                .map(RentalPolicy::getEffectiveFrom).max(LocalDate::compareTo).orElse(today);
        LocalDate start=latestStart.plusDays(5).isAfter(today.plusDays(5))
                ? latestStart.plusDays(5) : today.plusDays(5);
        as(manager,"FACILITY_MANAGER");
        OverduePolicyProposal draft=service.create(facility,request(start));
        assertEquals("DRAFT",draft.getStatus());
        assertEquals(base,policies.findApplicableOverduePolicies(facility,today).stream().filter(p->facility.equals(p.getFacilityId())).findFirst().orElseThrow().getPolicyId());
        service.submit(facility,draft.getProposalId());
        as(business,"BUSINESS_OPERATIONS_MANAGER");
        OverduePolicyProposal approved=service.approve(facility,draft.getProposalId(),"Đã kiểm tra, chấp thuận cho kỳ mới");
        assertEquals("APPROVED",approved.getStatus());assertEquals(business,approved.getReviewedBy());
        assertEquals("APPROVED",jdbc.queryForObject("SELECT status FROM dbo.OVERDUE_POLICY_PROPOSALS WHERE proposal_id=?",String.class,draft.getProposalId()));
        RentalPolicy current=policies.findApplicableOverduePolicies(facility,today).stream().filter(p->facility.equals(p.getFacilityId())).findFirst().orElseThrow();
        assertEquals(base,current.getPolicyId());
        RentalPolicy future=policies.findApplicableOverduePolicies(facility,start).stream().filter(p->facility.equals(p.getFacilityId())).findFirst().orElseThrow();
        assertEquals(approved.getApprovedPolicyId(),future.getPolicyId());assertEquals(nextVersion,future.getOverdueVersion());
        assertEquals(penaltyCount,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.CONTRACT_PENALTIES",Integer.class));
        assertEquals(invoiceCount,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.INVOICES",Integer.class));
        assertEquals(penaltyChecksum,jdbc.queryForObject("SELECT CHECKSUM_AGG(BINARY_CHECKSUM(penalty_id,policy_id,penalty_amount,paid_amount)) FROM dbo.CONTRACT_PENALTIES",Integer.class));
        assertEquals(invoiceChecksum,jdbc.queryForObject("SELECT CHECKSUM_AGG(BINARY_CHECKSUM(invoice_id,total_amount,penalty_amount,status)) FROM dbo.INVOICES",Integer.class));
    }
    @Test void rejectionPersistsDecisionButCreatesNoPolicy() {
        Integer before=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RENTAL_POLICIES",Integer.class);
        as(manager,"FACILITY_MANAGER");OverduePolicyProposal p=service.create(facility,request(LocalDate.now(Sprint2Access.ZONE).plusDays(10)));
        service.submit(facility,p.getProposalId());as(business,"BUSINESS_OPERATIONS_MANAGER");
        service.reject(facility,p.getProposalId(),"Thiếu cơ sở đánh giá");
        assertEquals("REJECTED",jdbc.queryForObject("SELECT status FROM dbo.OVERDUE_POLICY_PROPOSALS WHERE proposal_id=?",String.class,p.getProposalId()));
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RENTAL_POLICIES",Integer.class));
    }
}
