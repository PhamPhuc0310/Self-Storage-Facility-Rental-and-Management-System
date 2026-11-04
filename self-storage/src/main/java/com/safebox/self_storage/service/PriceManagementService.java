package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.manager.PriceScheduleRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PriceManagementService {
    private final PricingPolicyRepository prices;
    private final PriceProposalRepository proposals;
    private final FacilityRepository facilities;
    private final StorageUnitTypeRepository types;
    private final ActivityLogRepository logs;
    private final Sprint2Access access;

    public PriceManagementService(PricingPolicyRepository prices, PriceProposalRepository proposals,
            FacilityRepository facilities, StorageUnitTypeRepository types, ActivityLogRepository logs,
            Sprint2Access access) {
        this.prices=prices; this.proposals=proposals; this.facilities=facilities;
        this.types=types; this.logs=logs; this.access=access;
    }

    @Transactional(readOnly=true)
    public List<PricingPolicy> prices(UUID facilityId) {
        requireView(facilityId);
        return this.prices.findByFacilityId(facilityId).stream()
                .sorted(Comparator.comparing(PricingPolicy::getEffectiveFrom).reversed()).toList();
    }
    @Transactional(readOnly=true)
    public List<PriceProposal> proposals(UUID facilityId) {
        requireView(facilityId);
        return proposals.findByFacilityIdOrderByProposedAtDesc(facilityId);
    }
    public PricingPolicy createDraft(UUID facilityId, PriceScheduleRequest r) {
        requireBusiness(); validate(facilityId, r);
        PricingPolicy p=new PricingPolicy();
        p.setFacilityId(facilityId); p.setTypeId(r.typeId()); p.setCreatedBy(access.currentId());
        apply(p,r); p.setStatus("DRAFT");
        p=prices.saveAndFlush(p); audit("PRICE_DRAFT",p.getPricingId(),facilityId.toString()); return p;
    }
    public PricingPolicy editDraft(UUID facilityId, UUID priceId, PriceScheduleRequest r) {
        requireBusiness(); validate(facilityId,r);
        PricingPolicy p=price(facilityId,priceId);
        if (!"DRAFT".equals(p.getStatus())) conflict("Chỉ có thể sửa bản nháp; giá đã công bố phải giữ lịch sử.");
        apply(p,r); p=prices.saveAndFlush(p); audit("PRICE_EDIT",p.getPricingId(),facilityId.toString()); return p;
    }
    public PricingPolicy activate(UUID facilityId, UUID priceId) {
        requireBusiness();
        facilities.lockById(facilityId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy cơ sở."));
        PricingPolicy p=price(facilityId,priceId);
        if (!"DRAFT".equals(p.getStatus())) conflict("Chỉ có thể kích hoạt bản nháp.");
        if (p.getEffectiveFrom().isBefore(access.today())) conflict("Không thể kích hoạt giá có ngày bắt đầu trong quá khứ.");
        validate(facilityId,new PriceScheduleRequest(p.getTypeId(),p.getMonthlyPrice(),p.getEffectiveFrom(),p.getEffectiveTo()));
        assertNoOverlap(p);
        p.setStatus("ACTIVE"); p=prices.saveAndFlush(p);
        audit("PRICE_ACTIVATE",p.getPricingId(),facilityId.toString()); return p;
    }
    public PricingPolicy retire(UUID facilityId, UUID priceId) {
        requireBusiness();
        PricingPolicy p=price(facilityId,priceId);
        if (!"ACTIVE".equals(p.getStatus())) conflict("Chỉ có thể ngừng bảng giá đang hoạt động.");
        // Keep the row and dates for historical reservations and signed contracts.
        p.setStatus("RETIRED"); p=prices.saveAndFlush(p);
        audit("PRICE_RETIRE",p.getPricingId(),facilityId.toString()); return p;
    }
    public PriceProposal submit(UUID facilityId, PriceScheduleRequest r, String reason) {
        requireManager(facilityId); validate(facilityId,r);
        if (reason==null || reason.trim().length()<10 || reason.length()>500)
            throw new BusinessException(HttpStatus.BAD_REQUEST,"Lý do đề xuất cần từ 10 đến 500 ký tự.");
        PriceProposal p=new PriceProposal(); p.setFacilityId(facilityId); p.setTypeId(r.typeId());
        p.setProposedMonthlyPrice(r.monthlyPrice()); p.setEffectiveFrom(r.effectiveFrom()); p.setEffectiveTo(r.effectiveTo());
        p.setReason(reason.trim()); p.setStatus("SUBMITTED"); p.setProposedBy(access.currentId()); p.setProposedAt(access.now());
        p=proposals.saveAndFlush(p); audit("PRICE_PROPOSE",p.getProposalId(),facilityId.toString()); return p;
    }
    public PriceProposal reject(UUID proposalId) {
        requireBusiness(); PriceProposal p=pending(proposalId); p.setStatus("REJECTED");
        p.setReviewedBy(access.currentId()); p.setReviewedAt(access.now()); p=proposals.saveAndFlush(p);
        audit("PRICE_PROPOSAL_REJECT",p.getProposalId(),p.getFacilityId().toString()); return p;
    }
    public PriceProposal applyProposal(UUID proposalId) {
        requireBusiness(); PriceProposal proposal=pending(proposalId);
        UUID facilityId=proposal.getFacilityId();
        facilities.lockById(facilityId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy cơ sở."));
        PriceScheduleRequest r=new PriceScheduleRequest(proposal.getTypeId(),proposal.getProposedMonthlyPrice(),proposal.getEffectiveFrom(),proposal.getEffectiveTo());
        validate(facilityId,r);
        PricingPolicy price=new PricingPolicy(); price.setFacilityId(facilityId); price.setTypeId(r.typeId());
        price.setCreatedBy(access.currentId()); apply(price,r); price.setStatus("DRAFT");
        assertNoOverlap(price); price.setStatus("ACTIVE"); price=prices.saveAndFlush(price);
        proposal.setStatus("APPLIED"); proposal.setReviewedAt(access.now()); proposal.setReviewedBy(access.currentId());
        proposal.setPricingId(price.getPricingId()); proposal=proposals.saveAndFlush(proposal);
        audit("PRICE_PROPOSAL_APPLY",proposal.getProposalId(),price.getPricingId().toString()); return proposal;
    }
    private PriceProposal pending(UUID id) {
        PriceProposal p=proposals.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy đề xuất."));
        if (!"SUBMITTED".equals(p.getStatus())) conflict("Đề xuất đã được xử lý.");
        return p;
    }
    private PricingPolicy price(UUID facilityId, UUID id) {
        PricingPolicy p=prices.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy bảng giá."));
        if (!facilityId.equals(p.getFacilityId())) throw new BusinessException(HttpStatus.FORBIDDEN,"Bảng giá không thuộc cơ sở.");
        return p;
    }
    private void apply(PricingPolicy p, PriceScheduleRequest r) {
        p.setTypeId(r.typeId()); p.setMonthlyPrice(r.monthlyPrice());
        p.setEffectiveFrom(r.effectiveFrom()); p.setEffectiveTo(r.effectiveTo());
    }
    private void validate(UUID facilityId, PriceScheduleRequest r) {
        Facility f=facilities.findById(facilityId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy cơ sở."));
        if (!"ACTIVE".equals(f.getStatus())) conflict("Cơ sở chưa hoạt động.");
        if (r==null || r.typeId()==null || r.monthlyPrice()==null || r.monthlyPrice().compareTo(BigDecimal.ZERO)<=0
                || r.monthlyPrice().scale()>2 || r.effectiveFrom()==null || r.effectiveFrom().isBefore(access.today())
                || (r.effectiveTo()!=null && r.effectiveTo().isBefore(r.effectiveFrom())))
            throw new BusinessException(HttpStatus.BAD_REQUEST,"Giá thuê hoặc kỳ hiệu lực không hợp lệ.");
        StorageUnitType type=types.findById(r.typeId()).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy loại kho."));
        if (!"ACTIVE".equals(type.getStatus())) conflict("Loại kho không hoạt động.");
    }
    private void assertNoOverlap(PricingPolicy candidate) {
        for (PricingPolicy other:prices.findByFacilityIdAndTypeId(candidate.getFacilityId(),candidate.getTypeId())) {
            if (Objects.equals(other.getPricingId(),candidate.getPricingId()) || !"ACTIVE".equals(other.getStatus())) continue;
            if ((other.getEffectiveTo()==null || !other.getEffectiveTo().isBefore(candidate.getEffectiveFrom()))
                    && (candidate.getEffectiveTo()==null || !candidate.getEffectiveTo().isBefore(other.getEffectiveFrom())))
                conflict("Kỳ hiệu lực trùng với bảng giá đang hoạt động.");
        }
    }
    private void requireBusiness() {
        if (!"BUSINESS_OPERATIONS_MANAGER".equals(access.role())) throw new BusinessException(HttpStatus.FORBIDDEN,"Chỉ quản lý kinh doanh được sửa bảng giá.");
    }
    private void requireManager(UUID facilityId) {
        if (!"FACILITY_MANAGER".equals(access.role()) || !access.facilities().contains(facilityId))
            throw new BusinessException(HttpStatus.FORBIDDEN,"Bạn không được quản lý cơ sở này.");
    }
    private void requireView(UUID facilityId) {
        if ("BUSINESS_OPERATIONS_MANAGER".equals(access.role())) return;
        requireManager(facilityId);
    }
    private void audit(String action, UUID id, String detail) {
        logs.save(new ActivityLog(access.currentId(),action,"PRICING",id,detail,access.now()));
    }
    private void conflict(String message) { throw new BusinessException(HttpStatus.CONFLICT,message); }
}
