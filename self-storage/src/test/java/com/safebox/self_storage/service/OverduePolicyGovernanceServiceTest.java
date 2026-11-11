package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.manager.OverduePolicyProposalRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverduePolicyGovernanceServiceTest {
    @Mock OverduePolicyProposalRepository proposals;
    @Mock RentalPolicyRepository policies;
    @Mock FacilityRepository facilities;
    @Mock ActivityLogRepository logs;
    @Mock Sprint2Access access;
    OverduePolicyGovernanceService service;
    UUID facility=UUID.randomUUID(),manager=UUID.randomUUID(),business=UUID.randomUUID(),baseId=UUID.randomUUID();
    LocalDate today=LocalDate.of(2026,10,28);
    RentalPolicy base;
    @BeforeEach void setup() {
        service=new OverduePolicyGovernanceService(proposals,policies,facilities,logs,access);
        base=new RentalPolicy();base.setPolicyId(baseId);base.setFacilityId(facility);base.setStatus("ACTIVE");
        base.setEffectiveFrom(today.minusDays(30));base.setRenewalOpenDays(20);base.setPriorityDeadlineDays(5);
        base.setRenewalPaymentHours(24);base.setReservationHoldMinutes(60);base.setOverdueGraceDays(3);
        base.setDailyPenaltyRate(new BigDecimal("0.0300"));base.setFixedPenaltyAmount(BigDecimal.ZERO);
        base.setPenaltyType("PERCENT_DAILY");base.setCancellationPolicy("Giữ nguyên");base.setReturnPolicy("Giữ nguyên");
        Facility f=new Facility();f.setFacilityId(facility);f.setStatus("ACTIVE");
        lenient().when(facilities.findById(facility)).thenReturn(Optional.of(f));
        lenient().when(facilities.lockById(facility)).thenReturn(Optional.of(f));
        lenient().when(policies.findById(baseId)).thenReturn(Optional.of(base));
        lenient().when(policies.findByFacilityId(facility)).thenReturn(List.of(base));
        lenient().when(access.today()).thenReturn(today);
        lenient().when(access.now()).thenReturn(today.atTime(12,0));
        lenient().when(proposals.saveAndFlush(any())).thenAnswer(x->x.getArgument(0));
        lenient().when(policies.saveAndFlush(any())).thenAnswer(x->{RentalPolicy p=x.getArgument(0);if(p.getPolicyId()==null)p.setPolicyId(UUID.randomUUID());return p;});
    }
    OverduePolicyProposalRequest request(){return request(today.plusDays(5));}
    OverduePolicyProposalRequest request(LocalDate effectiveFrom) {
        return new OverduePolicyProposalRequest(baseId,4,"PERCENT_DAILY",new BigDecimal("0.0400"),
                BigDecimal.ZERO,effectiveFrom,null,"Điều chỉnh theo đánh giá vận hành");
    }
    OverduePolicyProposal pending() {
        OverduePolicyProposal p=new OverduePolicyProposal();p.setFacilityId(facility);p.setBasePolicyId(baseId);
        p.setCreatedBy(manager);p.setStatus("SUBMITTED");p.setOverdueGraceDays(4);p.setPenaltyType("PERCENT_DAILY");
        p.setDailyPenaltyRate(new BigDecimal("0.0400"));p.setFixedPenaltyAmount(BigDecimal.ZERO);
        p.setEffectiveFrom(today.plusDays(5));p.setReason("Điều chỉnh theo đánh giá vận hành");return p;
    }
    @Test void managerCreatesScopedDraftWithoutChangingEffectivePolicy() {
        when(access.role()).thenReturn("FACILITY_MANAGER");when(access.facilities()).thenReturn(List.of(facility));when(access.currentId()).thenReturn(manager);
        OverduePolicyProposal p=service.create(facility,request());
        assertEquals("DRAFT",p.getStatus());assertEquals(new BigDecimal("0.0400"),p.getDailyPenaltyRate());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(BusinessException.class,()->service.create(UUID.randomUUID(),request())).status());
        verify(policies,never()).saveAndFlush(any());
    }
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void facilityCreationRejectsPastAndAcceptsTodayOrFuture(int offset) {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facility));
        LocalDate effectiveFrom = today.plusDays(offset);

        if (offset < 0) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                    () -> service.create(facility, request(effectiveFrom))).status());
            verify(proposals, never()).saveAndFlush(any());
        } else {
            when(access.currentId()).thenReturn(manager);
            OverduePolicyProposal draft = service.create(facility, request(effectiveFrom));
            assertEquals("DRAFT", draft.getStatus());
            assertEquals(effectiveFrom, draft.getEffectiveFrom());
            assertEquals(manager, draft.getCreatedBy());
        }
        verify(policies, never()).saveAndFlush(any());
    }
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void facilityEditingRejectsPastAndAcceptsTodayOrFuture(int offset) {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facility));
        when(access.currentId()).thenReturn(manager);
        UUID id = UUID.randomUUID();
        OverduePolicyProposal draft = pending();
        draft.setStatus("DRAFT");
        when(proposals.lockById(id)).thenReturn(Optional.of(draft));
        LocalDate effectiveFrom = today.plusDays(offset);

        if (offset < 0) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                    () -> service.edit(facility, id, request(effectiveFrom))).status());
            verify(proposals, never()).saveAndFlush(any());
        } else {
            service.edit(facility, id, request(effectiveFrom));
            assertEquals("DRAFT", draft.getStatus());
            assertEquals(effectiveFrom, draft.getEffectiveFrom());
        }
        verify(policies, never()).saveAndFlush(any());
    }
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void facilitySubmissionRevalidatesPastTodayAndFuture(int offset) {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facility));
        when(access.currentId()).thenReturn(manager);
        UUID id = UUID.randomUUID();
        OverduePolicyProposal draft = pending();
        draft.setStatus("DRAFT");
        draft.setEffectiveFrom(today.plusDays(offset));
        when(proposals.lockById(id)).thenReturn(Optional.of(draft));

        if (offset < 0) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                    () -> service.submit(facility, id)).status());
            assertEquals("DRAFT", draft.getStatus());
            verify(proposals, never()).saveAndFlush(any());
        } else {
            service.submit(facility, id);
            assertEquals("SUBMITTED", draft.getStatus());
            assertEquals(today.atTime(12, 0), draft.getSubmittedAt());
        }
        verify(policies, never()).saveAndFlush(any());
    }
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void facilityApprovalRevalidatesPastTodayAndFuture(int offset) {
        when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");
        when(access.currentId()).thenReturn(business);
        UUID id = UUID.randomUUID();
        OverduePolicyProposal submitted = pending();
        LocalDate effectiveFrom = today.plusDays(offset);
        submitted.setEffectiveFrom(effectiveFrom);
        when(proposals.lockById(id)).thenReturn(Optional.of(submitted));

        if (offset < 0) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                    () -> service.approve(facility, id, "Đã xem xét kỹ")).status());
            assertEquals("SUBMITTED", submitted.getStatus());
            verify(policies, never()).saveAndFlush(any());
        } else {
            service.approve(facility, id, "Đã xem xét kỹ");
            assertEquals("APPROVED", submitted.getStatus());
            assertEquals(business, submitted.getReviewedBy());
            assertNotNull(submitted.getApprovedPolicyId());
            verify(policies).saveAndFlush(argThat(policy ->
                    effectiveFrom.equals(policy.getEffectiveFrom())
                    && business.equals(policy.getOverdueApprovedBy())
                    && manager.equals(policy.getOverdueCreatedBy())));
        }
    }
    @Test void proposalCrossesVietnamMidnightFromFutureToToday() {
        LocalDate effectiveFrom = LocalDate.of(2026, 11, 11);
        Clock beforeMidnight = Clock.fixed(Instant.parse("2026-11-10T16:59:59Z"), Sprint2Access.ZONE);
        Clock atMidnight = Clock.fixed(Instant.parse("2026-11-10T17:00:00Z"), Sprint2Access.ZONE);
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facility));
        when(access.currentId()).thenReturn(manager);
        assertEquals("Asia/Ho_Chi_Minh", Sprint2Access.ZONE.getId());
        assertEquals(effectiveFrom.minusDays(1), LocalDate.now(beforeMidnight));
        assertEquals(effectiveFrom, LocalDate.now(atMidnight));
        assertEquals(effectiveFrom.minusDays(1), LocalDate.ofInstant(atMidnight.instant(), ZoneOffset.UTC));
        when(access.today()).thenReturn(LocalDate.now(beforeMidnight));
        when(access.now()).thenReturn(LocalDateTime.now(beforeMidnight));
        OverduePolicyProposal draft = service.create(facility, request(effectiveFrom));
        UUID id = UUID.randomUUID();
        when(proposals.lockById(id)).thenReturn(Optional.of(draft));

        when(access.today()).thenReturn(LocalDate.now(atMidnight));
        when(access.now()).thenReturn(LocalDateTime.now(atMidnight));
        service.submit(facility, id);
        assertEquals(effectiveFrom.atStartOfDay(), draft.getSubmittedAt());
        when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");
        when(access.currentId()).thenReturn(business);
        service.approve(facility, id, "Duyệt tại ranh giới ngày Việt Nam");
        assertEquals("APPROVED", draft.getStatus());
        verify(policies).saveAndFlush(argThat(policy ->
                effectiveFrom.atStartOfDay().equals(policy.getOverdueApprovedAt())));
    }
    @Test void businessActivatesGlobalDirectlyAndPreservesUnrelatedTermsAndLocalFlow() {
        base.setFacilityId(null);
        when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");when(access.currentId()).thenReturn(business);
        when(policies.findFirstByFacilityIdIsNullOrderByEffectiveFromAscPolicyIdAsc()).thenReturn(Optional.of(base));
        when(policies.lockById(baseId)).thenReturn(Optional.of(base));
        when(policies.findByFacilityId(null)).thenReturn(List.of(base));
        RentalPolicy next=service.activateGlobal(request());
        assertNull(next.getFacilityId());assertEquals(business,next.getOverdueCreatedBy());
        assertEquals(business,next.getOverdueApprovedBy());assertNull(next.getOverdueProposalId());
        assertEquals(1,next.getOverdueVersion());assertEquals(baseId,next.getOverdueSourcePolicyId());
        assertEquals(base.getReservationHoldMinutes(),next.getReservationHoldMinutes());
        assertEquals(base.getReturnPolicy(),next.getReturnPolicy());assertNull(base.getOverdueApprovedAt());
        verify(policies).lockById(baseId);verifyNoInteractions(proposals,facilities);
        verify(logs).save(argThat(log->"GLOBAL_OVERDUE_POLICY_ACTIVATE".equals(log.getActionType())&&"RENTAL_POLICY".equals(log.getEntityType())));
    }
    @Test void globalReplacementClosesOldBoundaryAndConflictingRetryIsRejected() {
        base.setFacilityId(null);base.setOverdueApprovedBy(business);base.setOverdueApprovedAt(today.atStartOfDay());base.setOverdueVersion(2);
        when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");when(access.currentId()).thenReturn(business);
        when(policies.findFirstByFacilityIdIsNullOrderByEffectiveFromAscPolicyIdAsc()).thenReturn(Optional.of(base));
        when(policies.lockById(baseId)).thenReturn(Optional.of(base));when(policies.findByFacilityId(null)).thenReturn(List.of(base));
        var next=service.activateGlobal(request());assertEquals(3,next.getOverdueVersion());
        assertEquals(request().effectiveFrom().minusDays(1),base.getEffectiveTo());
        when(policies.findByFacilityId(null)).thenReturn(List.of(base,next));
        assertEquals(HttpStatus.CONFLICT,assertThrows(BusinessException.class,()->service.activateGlobal(request())).status());
        verify(policies,times(2)).saveAndFlush(any());
    }
    @Test void otherRolesCannotReadOrActivateGlobalAndBackdatedPolicyIsRejected() {
        for(String role:List.of("SYSTEM_ADMIN","FACILITY_MANAGER","FACILITY_STAFF","CUSTOMER")) {
            when(access.role()).thenReturn(role);
            assertEquals(HttpStatus.FORBIDDEN,assertThrows(BusinessException.class,()->service.activateGlobal(request())).status());
            assertEquals(HttpStatus.FORBIDDEN,assertThrows(BusinessException.class,()->service.globalHistory()).status());
        }
        when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");
        var r=request();var past=new OverduePolicyProposalRequest(baseId,r.overdueGraceDays(),r.penaltyType(),r.dailyPenaltyRate(),r.fixedPenaltyAmount(),today.minusDays(1),null,r.reason());
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(BusinessException.class,()->service.activateGlobal(past)).status());
        verify(policies,never()).saveAndFlush(any());
    }
    @Test void managerEditsSubmitsAndWithdrawsOwnProposalOnly() {
        when(access.role()).thenReturn("FACILITY_MANAGER");when(access.facilities()).thenReturn(List.of(facility));when(access.currentId()).thenReturn(manager);
        UUID id=UUID.randomUUID();OverduePolicyProposal p=pending();p.setStatus("DRAFT");when(proposals.lockById(id)).thenReturn(Optional.of(p));
        service.edit(facility,id,request());assertEquals("DRAFT",p.getStatus());
        service.submit(facility,id);assertEquals("SUBMITTED",p.getStatus());assertNotNull(p.getSubmittedAt());
        assertEquals(HttpStatus.CONFLICT,assertThrows(BusinessException.class,()->service.edit(facility,id,request())).status());
        service.withdraw(facility,id);assertEquals("WITHDRAWN",p.getStatus());
        verify(policies,never()).saveAndFlush(any());
    }
    @Test void rejectionRecordsDecisionAndDoesNotCreatePolicy() {
        when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");when(access.currentId()).thenReturn(business);
        UUID id=UUID.randomUUID();OverduePolicyProposal p=pending();when(proposals.lockById(id)).thenReturn(Optional.of(p));
        service.reject(facility,id,"Không đủ cơ sở dữ liệu");
        assertEquals("REJECTED",p.getStatus());assertEquals(business,p.getReviewedBy());
        assertEquals("Không đủ cơ sở dữ liệu",p.getDecisionReason());
        verify(policies,never()).saveAndFlush(any());
    }
    @Test void approvalCreatesFutureVersionAndClosesPreviousOnlyAtBoundary() {
        when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");when(access.currentId()).thenReturn(business);
        base.setOverdueApprovedAt(today.minusDays(10).atStartOfDay());base.setOverdueApprovedBy(business);
        UUID id=UUID.randomUUID();OverduePolicyProposal p=pending();when(proposals.lockById(id)).thenReturn(Optional.of(p));
        OverduePolicyProposal reviewed=service.approve(facility,id,"Đã kiểm tra và chấp thuận");
        assertEquals("APPROVED",reviewed.getStatus());assertNotNull(reviewed.getApprovedPolicyId());
        assertEquals(today.plusDays(4),base.getEffectiveTo());
        assertEquals(business,reviewed.getReviewedBy());
        verify(policies,atLeastOnce()).saveAndFlush(argThat(x->x.getOverdueVersion()!=null&&x.getOverdueVersion()==1
                && x.getEffectiveFrom().equals(today.plusDays(5))&&x.getRenewalOpenDays()==20&&x.getOverdueCreatedBy().equals(manager)));
    }
    @Test void expiredAndConflictingApprovalAreRejectedWithoutChangingPenaltyRecords() {
        when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");when(access.currentId()).thenReturn(business);
        UUID id=UUID.randomUUID();OverduePolicyProposal p=pending();when(proposals.lockById(id)).thenReturn(Optional.of(p));
        p.setEffectiveFrom(today.minusDays(1));assertEquals(HttpStatus.BAD_REQUEST,assertThrows(BusinessException.class,()->service.approve(facility,id,"Đã xem xét kỹ")).status());
        p.setEffectiveFrom(today.plusDays(5));
        RentalPolicy conflict=new RentalPolicy();conflict.setStatus("ACTIVE");conflict.setEffectiveFrom(today.plusDays(5));
        conflict.setOverdueApprovedAt(today.atStartOfDay());conflict.setOverdueApprovedBy(business);
        when(policies.findByFacilityId(facility)).thenReturn(List.of(base,conflict));
        assertEquals(HttpStatus.CONFLICT,assertThrows(BusinessException.class,()->service.approve(facility,id,"Đã xem xét kỹ")).status());
        verify(policies,never()).saveAndFlush(any());
    }
    @Test void managerAndAdminCannotApprove() {
        when(access.role()).thenReturn("FACILITY_MANAGER","SYSTEM_ADMIN");
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(BusinessException.class,()->service.approve(facility,UUID.randomUUID(),"Đã xem xét kỹ")).status());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(BusinessException.class,()->service.approve(facility,UUID.randomUUID(),"Đã xem xét kỹ")).status());
        verifyNoInteractions(proposals);
    }
    @Test void reviewerCannotApproveOwnProposalAfterRoleChangeOrCrossFacilityRoute() {
        when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");when(access.currentId()).thenReturn(manager);
        UUID id=UUID.randomUUID();OverduePolicyProposal p=pending();when(proposals.lockById(id)).thenReturn(Optional.of(p));
        UUID otherFacility=UUID.randomUUID(); Facility other=new Facility();other.setFacilityId(otherFacility);
        when(facilities.lockById(otherFacility)).thenReturn(Optional.of(other));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(BusinessException.class,
                ()->service.approve(otherFacility,id,"Đã xem xét kỹ")).status());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(BusinessException.class,
                ()->service.approve(facility,id,"Đã xem xét kỹ")).status());
        verify(policies,never()).saveAndFlush(any());
    }
}
