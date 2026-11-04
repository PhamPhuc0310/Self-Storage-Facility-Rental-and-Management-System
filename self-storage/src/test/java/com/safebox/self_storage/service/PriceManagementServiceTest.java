package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.manager.PriceScheduleRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceManagementServiceTest {
    @Mock PricingPolicyRepository prices;
    @Mock PriceProposalRepository proposals;
    @Mock FacilityRepository facilities;
    @Mock StorageUnitTypeRepository types;
    @Mock ActivityLogRepository logs;
    @Mock Sprint2Access access;
    PriceManagementService service;
    UUID facility=UUID.randomUUID(), actor=UUID.randomUUID();
    LocalDate today=LocalDate.of(2026,10,28);
    @BeforeEach void setup() {
        service=new PriceManagementService(prices,proposals,facilities,types,logs,access);
    }
    PriceScheduleRequest request(LocalDate from, LocalDate to) { return new PriceScheduleRequest(1,new BigDecimal("1250000.00"),from,to); }
    void valid() {
        Facility f=new Facility(); f.setFacilityId(facility); f.setStatus("ACTIVE");
        StorageUnitType t=new StorageUnitType(); t.setTypeId(1); t.setStatus("ACTIVE");
        lenient().when(facilities.findById(facility)).thenReturn(Optional.of(f));
        lenient().when(facilities.lockById(facility)).thenReturn(Optional.of(f));
        lenient().when(types.findById(1)).thenReturn(Optional.of(t));
        lenient().when(access.today()).thenReturn(today);
        lenient().when(access.currentId()).thenReturn(actor);
        lenient().when(access.now()).thenReturn(today.atStartOfDay());
        lenient().when(prices.saveAndFlush(any())).thenAnswer(a->{ PricingPolicy p=a.getArgument(0); if(p.getPricingId()==null)p.setPricingId(UUID.randomUUID()); return p; });
        lenient().when(proposals.saveAndFlush(any())).thenAnswer(a->a.getArgument(0));
    }
    @Test void draftIsNotBookableAndActivationChecksInclusiveOverlap() {
        valid(); when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");
        PricingPolicy draft=service.createDraft(facility,request(today.plusDays(1),today.plusDays(10)));
        assertEquals("DRAFT",draft.getStatus()); assertNull(draft.getDepositAmount());
        assertNull(draft.getDailyOverdueRate());
        PricingPolicy existing=new PricingPolicy(); existing.setPricingId(UUID.randomUUID()); existing.setStatus("ACTIVE");
        existing.setEffectiveFrom(today.plusDays(10)); existing.setEffectiveTo(today.plusDays(20));
        when(prices.findById(draft.getPricingId())).thenReturn(Optional.of(draft));
        when(prices.findByFacilityIdAndTypeId(facility,1)).thenReturn(List.of(existing));
        assertEquals(HttpStatus.CONFLICT,assertThrows(BusinessException.class,()->service.activate(facility,draft.getPricingId())).status());
        verify(prices,never()).saveAndFlush(argThat(p->"ACTIVE".equals(p.getStatus())));
    }
    @Test void activationAndRetirementPreserveScheduleRow() {
        valid(); when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");
        PricingPolicy draft=service.createDraft(facility,request(today.plusDays(1),today.plusDays(3)));
        when(prices.findById(draft.getPricingId())).thenReturn(Optional.of(draft));
        when(prices.findByFacilityIdAndTypeId(facility,1)).thenReturn(List.of(draft));
        assertEquals("ACTIVE",service.activate(facility,draft.getPricingId()).getStatus());
        assertEquals("RETIRED",service.retire(facility,draft.getPricingId()).getStatus());
        assertEquals(today.plusDays(1),draft.getEffectiveFrom());
        verify(prices,never()).delete(any());
    }
    @Test void managerCanProposeButCannotPrice() {
        valid(); when(access.role()).thenReturn("FACILITY_MANAGER"); when(access.facilities()).thenReturn(List.of(facility));
        PriceProposal p=service.submit(facility,request(today.plusDays(1),null),"Điều chỉnh theo nhu cầu thực tế");
        assertEquals("SUBMITTED",p.getStatus());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(BusinessException.class,()->service.createDraft(facility,request(today.plusDays(1),null))).status());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(BusinessException.class,()->service.submit(UUID.randomUUID(),request(today.plusDays(1),null),"Điều chỉnh theo nhu cầu thực tế")).status());
    }
    @Test void proposalApprovalUsesOverlapValidationAndCapturesReviewer() {
        valid(); when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");
        PriceProposal p=new PriceProposal(); p.setFacilityId(facility); p.setTypeId(1); p.setStatus("SUBMITTED");
        p.setEffectiveFrom(today.plusDays(1)); p.setProposedMonthlyPrice(new BigDecimal("1300000"));
        when(proposals.findById(any())).thenReturn(Optional.of(p));
        UUID proposalId=UUID.randomUUID();
        PriceProposal result=service.applyProposal(proposalId);
        assertEquals("APPLIED",result.getStatus()); assertNotNull(result.getPricingId());
        assertEquals(actor,result.getReviewedBy());
        verify(prices).saveAndFlush(argThat(x->"ACTIVE".equals(x.getStatus())));
    }
    @Test void rejectsPastDatesAndUnapprovedProposalMutation() {
        valid(); when(access.role()).thenReturn("BUSINESS_OPERATIONS_MANAGER");
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(BusinessException.class,()->service.createDraft(facility,request(today.minusDays(1),null))).status());
        PriceProposal p=new PriceProposal(); p.setStatus("REJECTED");
        when(proposals.findById(any())).thenReturn(Optional.of(p));
        assertEquals(HttpStatus.CONFLICT,assertThrows(BusinessException.class,()->service.applyProposal(UUID.randomUUID())).status());
    }
}
