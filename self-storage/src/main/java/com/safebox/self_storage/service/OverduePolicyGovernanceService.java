package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.manager.OverduePolicyProposalRequest;
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
public class OverduePolicyGovernanceService {
    private final OverduePolicyProposalRepository proposals;
    private final RentalPolicyRepository policies;
    private final FacilityRepository facilities;
    private final ActivityLogRepository logs;
    private final Sprint2Access access;
    public OverduePolicyGovernanceService(OverduePolicyProposalRepository proposals, RentalPolicyRepository policies,
            FacilityRepository facilities, ActivityLogRepository logs, Sprint2Access access) {
        this.proposals=proposals; this.policies=policies; this.facilities=facilities; this.logs=logs; this.access=access;
    }
    @Transactional(readOnly=true)
    public List<RentalPolicy> globalHistory() {
        business();
        return policies.findByFacilityId(null).stream()
                .sorted(Comparator.comparing(RentalPolicy::getEffectiveFrom).reversed()).toList();
    }
    public RentalPolicy activateGlobal(OverduePolicyProposalRequest request) {
        business(); validateParameters(request, true);
        // Every activation locks the same oldest global row, even when callers choose different source versions.
        RentalPolicy anchor=policies.findFirstByFacilityIdIsNullOrderByEffectiveFromAscPolicyIdAsc()
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT,"Chưa có chính sách toàn hệ thống làm gốc; cần đối soát cấu hình."));
        policies.lockById(anchor.getPolicyId()).orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT,"Chính sách gốc đã thay đổi."));
        RentalPolicy base=policies.findById(request.basePolicyId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy chính sách gốc."));
        if (base.getFacilityId()!=null || !"ACTIVE".equals(base.getStatus())
                || base.getEffectiveFrom().isAfter(access.today())
                || (base.getEffectiveTo()!=null && base.getEffectiveTo().isBefore(access.today())))
            conflict("Cần chọn chính sách gốc toàn hệ thống đang có hiệu lực.");
        List<RentalPolicy> versions=policies.findByFacilityId(null);
        List<RentalPolicy> overlapping=versions.stream().filter(p -> approved(p)
                && overlaps(p.getEffectiveFrom(),p.getEffectiveTo(),request.effectiveFrom(),request.effectiveTo())).toList();
        if (overlapping.size()>1 || overlapping.stream().anyMatch(p -> !p.getEffectiveFrom().isBefore(request.effectiveFrom())))
            conflict("Kỳ hiệu lực chồng lấn phiên bản toàn hệ thống đã duyệt; hãy tải lại lịch sử.");
        for (RentalPolicy old:overlapping) {
            old.setEffectiveTo(request.effectiveFrom().minusDays(1)); policies.saveAndFlush(old);
            logs.save(new ActivityLog(access.currentId(),"GLOBAL_OVERDUE_POLICY_SUPERSEDE","RENTAL_POLICY",
                    old.getPolicyId(),"end="+old.getEffectiveTo()+"; reason="+request.reason().trim(),access.now()));
        }
        RentalPolicy next=new RentalPolicy(); next.setFacilityId(null);
        next.setRenewalOpenDays(base.getRenewalOpenDays()); next.setPriorityDeadlineDays(base.getPriorityDeadlineDays());
        next.setRenewalPaymentHours(base.getRenewalPaymentHours()); next.setReservationHoldMinutes(base.getReservationHoldMinutes());
        next.setCancellationPolicy(base.getCancellationPolicy()); next.setReturnPolicy(base.getReturnPolicy());
        next.setOverdueGraceDays(request.overdueGraceDays()); next.setPenaltyType(request.penaltyType());
        next.setDailyPenaltyRate(request.dailyPenaltyRate()); next.setFixedPenaltyAmount(request.fixedPenaltyAmount());
        next.setEffectiveFrom(request.effectiveFrom()); next.setEffectiveTo(request.effectiveTo()); next.setStatus("ACTIVE");
        next.setOverdueVersion(versions.stream().map(RentalPolicy::getOverdueVersion).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0)+1);
        next.setOverdueSourcePolicyId(base.getPolicyId()); next.setOverdueCreatedBy(access.currentId());
        next.setOverdueApprovedBy(access.currentId()); next.setOverdueApprovedAt(access.now());
        next=policies.saveAndFlush(next);
        logs.save(new ActivityLog(access.currentId(),"GLOBAL_OVERDUE_POLICY_ACTIVATE","RENTAL_POLICY",next.getPolicyId(),
                "version="+next.getOverdueVersion()+"; from="+next.getEffectiveFrom()+"; to="+next.getEffectiveTo()
                        +"; source="+base.getPolicyId()+"; grace="+next.getOverdueGraceDays()+"; type="+next.getPenaltyType()
                        +"; rate="+next.getDailyPenaltyRate()+"; fixed="+next.getFixedPenaltyAmount()+"; reason="+request.reason().trim(),access.now()));
        return next;
    }
    @Transactional(readOnly=true)
    public List<OverduePolicyProposal> list(UUID facilityId) {
        view(facilityId);
        return proposals.findByFacilityIdOrderByCreatedAtDesc(facilityId);
    }
    public OverduePolicyProposal create(UUID facilityId, OverduePolicyProposalRequest request) {
        manager(facilityId); validate(facilityId,request);
        OverduePolicyProposal p=new OverduePolicyProposal(); p.setFacilityId(facilityId);
        apply(p,request); p.setStatus("DRAFT"); p.setCreatedBy(access.currentId()); p.setCreatedAt(access.now());
        p=proposals.saveAndFlush(p); audit("OVERDUE_PROPOSAL_CREATE",p.getProposalId(),facilityId.toString()); return p;
    }
    public OverduePolicyProposal edit(UUID facilityId, UUID proposalId, OverduePolicyProposalRequest request) {
        manager(facilityId); OverduePolicyProposal p=ownedDraft(facilityId,proposalId);
        validate(facilityId,request); apply(p,request); p=proposals.saveAndFlush(p);
        audit("OVERDUE_PROPOSAL_EDIT",p.getProposalId(),facilityId.toString()); return p;
    }
    public OverduePolicyProposal submit(UUID facilityId, UUID proposalId) {
        manager(facilityId); OverduePolicyProposal p=ownedDraft(facilityId,proposalId);
        validate(facilityId,requestOf(p)); p.setStatus("SUBMITTED"); p.setSubmittedAt(access.now());
        p=proposals.saveAndFlush(p); audit("OVERDUE_PROPOSAL_SUBMIT",p.getProposalId(),facilityId.toString()); return p;
    }
    public OverduePolicyProposal withdraw(UUID facilityId, UUID proposalId) {
        manager(facilityId); OverduePolicyProposal p=proposal(facilityId,proposalId);
        if (!access.currentId().equals(p.getCreatedBy()) || !Set.of("DRAFT","SUBMITTED").contains(p.getStatus()))
            conflict("Chỉ người đề xuất được rút bản nháp hoặc đề xuất đang chờ.");
        p.setStatus("WITHDRAWN"); p.setWithdrawnAt(access.now()); p=proposals.saveAndFlush(p);
        audit("OVERDUE_PROPOSAL_WITHDRAW",p.getProposalId(),facilityId.toString()); return p;
    }
    public OverduePolicyProposal reject(UUID facilityId, UUID proposalId, String reason) {
        business(); OverduePolicyProposal p=submitted(facilityId,proposalId); decisionReason(reason);
        p.setStatus("REJECTED"); decide(p,reason);
        p=proposals.saveAndFlush(p); audit("OVERDUE_PROPOSAL_REJECT",p.getProposalId(),reason.trim()); return p;
    }
    public OverduePolicyProposal approve(UUID facilityId, UUID proposalId, String reason) {
        business(); decisionReason(reason);
        facilities.lockById(facilityId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy cơ sở."));
        OverduePolicyProposal p=submitted(facilityId,proposalId);
        if (access.currentId().equals(p.getCreatedBy())) throw new BusinessException(HttpStatus.FORBIDDEN,"Không được tự duyệt đề xuất của mình.");
        validate(facilityId,requestOf(p));
        RentalPolicy base=basePolicy(facilityId,p.getBasePolicyId());
        List<RentalPolicy> versions=policies.findByFacilityId(facilityId);
        LocalDate proposedFrom=p.getEffectiveFrom(), proposedTo=p.getEffectiveTo();
        List<RentalPolicy> overlapping=versions.stream().filter(x -> approved(x) && overlaps(x.getEffectiveFrom(),x.getEffectiveTo(),proposedFrom,proposedTo)).toList();
        if (overlapping.size()>1) conflict("Có nhiều chính sách quá hạn đã duyệt chồng lấn kỳ hiệu lực.");
        if (overlapping.size()==1) {
            RentalPolicy old=overlapping.get(0);
            if (!old.getEffectiveFrom().isBefore(p.getEffectiveFrom())) conflict("Chính sách mới chồng lấn chính sách đã duyệt.");
            old.setEffectiveTo(p.getEffectiveFrom().minusDays(1));
            policies.saveAndFlush(old);
            audit("OVERDUE_POLICY_SUPERSEDE",old.getPolicyId(),"end="+old.getEffectiveTo()+"; proposal="+p.getProposalId());
        }
        int version=versions.stream().map(RentalPolicy::getOverdueVersion).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0)+1;
        RentalPolicy next=new RentalPolicy(); next.setFacilityId(facilityId);
        next.setRenewalOpenDays(base.getRenewalOpenDays()); next.setPriorityDeadlineDays(base.getPriorityDeadlineDays());
        next.setRenewalPaymentHours(base.getRenewalPaymentHours()); next.setReservationHoldMinutes(base.getReservationHoldMinutes());
        next.setCancellationPolicy(base.getCancellationPolicy()); next.setReturnPolicy(base.getReturnPolicy());
        next.setOverdueGraceDays(p.getOverdueGraceDays()); next.setPenaltyType(p.getPenaltyType());
        next.setDailyPenaltyRate(p.getDailyPenaltyRate()); next.setFixedPenaltyAmount(p.getFixedPenaltyAmount());
        next.setEffectiveFrom(p.getEffectiveFrom()); next.setEffectiveTo(p.getEffectiveTo()); next.setStatus("ACTIVE");
        next.setOverdueVersion(version); next.setOverdueSourcePolicyId(base.getPolicyId());
        next.setOverdueProposalId(p.getProposalId()); next.setOverdueCreatedBy(p.getCreatedBy());
        next.setOverdueApprovedBy(access.currentId()); next.setOverdueApprovedAt(access.now());
        next=policies.saveAndFlush(next);
        p.setStatus("APPROVED"); p.setApprovedPolicyId(next.getPolicyId()); decide(p,reason);
        p=proposals.saveAndFlush(p); audit("OVERDUE_PROPOSAL_APPROVE",p.getProposalId(),"policy="+next.getPolicyId()+"; "+reason.trim());
        return p;
    }
    private void decide(OverduePolicyProposal p,String reason) {
        p.setReviewedBy(access.currentId()); p.setReviewedAt(access.now()); p.setDecisionReason(reason.trim());
    }
    private OverduePolicyProposal ownedDraft(UUID facilityId, UUID id) {
        OverduePolicyProposal p=proposal(facilityId,id);
        if (!"DRAFT".equals(p.getStatus()) || !access.currentId().equals(p.getCreatedBy()))
            conflict("Chỉ người tạo được sửa hoặc gửi bản nháp.");
        return p;
    }
    private OverduePolicyProposal submitted(UUID facilityId, UUID id) {
        OverduePolicyProposal p=proposal(facilityId,id);
        if (!"SUBMITTED".equals(p.getStatus())) conflict("Chỉ đề xuất đã gửi mới được duyệt hoặc từ chối.");
        return p;
    }
    private OverduePolicyProposal proposal(UUID facilityId,UUID id) {
        OverduePolicyProposal p=proposals.lockById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy đề xuất."));
        if (!facilityId.equals(p.getFacilityId())) throw new BusinessException(HttpStatus.FORBIDDEN,"Đề xuất không thuộc cơ sở này.");
        return p;
    }
    private void apply(OverduePolicyProposal p, OverduePolicyProposalRequest r) {
        p.setBasePolicyId(r.basePolicyId()); p.setOverdueGraceDays(r.overdueGraceDays());
        p.setPenaltyType(r.penaltyType()); p.setDailyPenaltyRate(r.dailyPenaltyRate());
        p.setFixedPenaltyAmount(r.fixedPenaltyAmount()); p.setEffectiveFrom(r.effectiveFrom());
        p.setEffectiveTo(r.effectiveTo()); p.setReason(r.reason().trim());
    }
    private OverduePolicyProposalRequest requestOf(OverduePolicyProposal p) {
        return new OverduePolicyProposalRequest(p.getBasePolicyId(),p.getOverdueGraceDays(),p.getPenaltyType(),
                p.getDailyPenaltyRate(),p.getFixedPenaltyAmount(),p.getEffectiveFrom(),p.getEffectiveTo(),p.getReason());
    }
    private void validate(UUID facilityId,OverduePolicyProposalRequest r) {
        Facility f=facilities.findById(facilityId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy cơ sở."));
        if (!"ACTIVE".equals(f.getStatus())) conflict("Cơ sở chưa hoạt động.");
        validateParameters(r, true);
        basePolicy(facilityId,r.basePolicyId());
    }
    private void validateParameters(OverduePolicyProposalRequest r, boolean allowToday) {
        if (r==null || r.basePolicyId()==null || r.overdueGraceDays()==null || r.overdueGraceDays()<0
                || r.penaltyType()==null || !Set.of("PERCENT_DAILY","FIXED_DAILY").contains(r.penaltyType())
                || r.dailyPenaltyRate()==null || r.dailyPenaltyRate().signum()<0 || r.dailyPenaltyRate().scale()>4
                || r.dailyPenaltyRate().compareTo(new BigDecimal("10"))>=0
                || r.fixedPenaltyAmount()==null || r.fixedPenaltyAmount().signum()<0 || r.fixedPenaltyAmount().scale()>2
                || r.effectiveFrom()==null || r.effectiveFrom().isBefore(access.today())
                || (!allowToday && r.effectiveFrom().equals(access.today()))
                || (r.effectiveTo()!=null && r.effectiveTo().isBefore(r.effectiveFrom()))
                || r.reason()==null || r.reason().trim().length()<10 || r.reason().length()>500)
            throw new BusinessException(HttpStatus.BAD_REQUEST,"Dữ liệu đề xuất quá hạn không hợp lệ; cần nhập rõ công thức, mức phạt, kỳ hiệu lực và lý do.");
    }
    private RentalPolicy basePolicy(UUID facilityId,UUID id) {
        RentalPolicy base=policies.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy chính sách gốc."));
        if ((base.getFacilityId()!=null && !facilityId.equals(base.getFacilityId())) || !"ACTIVE".equals(base.getStatus())
                || base.getEffectiveFrom().isAfter(access.today())
                || (base.getEffectiveTo()!=null && base.getEffectiveTo().isBefore(access.today())))
            throw new BusinessException(HttpStatus.CONFLICT,"Chính sách gốc không áp dụng cho cơ sở hoặc đã hết hiệu lực.");
        if (base.getFacilityId()==null && policies.findByFacilityId(facilityId).stream().anyMatch(x -> "ACTIVE".equals(x.getStatus())
                && !x.getEffectiveFrom().isAfter(access.today()) && (x.getEffectiveTo()==null || !x.getEffectiveTo().isBefore(access.today()))))
            conflict("Cơ sở có chính sách riêng; không thể lấy chính sách toàn hệ thống làm gốc.");
        return base;
    }
    private boolean approved(RentalPolicy p) { return "ACTIVE".equals(p.getStatus()) && p.getOverdueApprovedAt()!=null && p.getOverdueApprovedBy()!=null; }
    private boolean overlaps(LocalDate a,LocalDate aEnd,LocalDate b,LocalDate bEnd) {
        return (aEnd==null || !aEnd.isBefore(b)) && (bEnd==null || !bEnd.isBefore(a));
    }
    private void decisionReason(String reason) {
        if (reason==null || reason.trim().length()<5 || reason.length()>500)
            throw new BusinessException(HttpStatus.BAD_REQUEST,"Cần ghi lý do quyết định từ 5 đến 500 ký tự.");
    }
    private void view(UUID facilityId) {
        if ("BUSINESS_OPERATIONS_MANAGER".equals(access.role())) return;
        manager(facilityId);
    }
    private void manager(UUID facilityId) {
        if (!"FACILITY_MANAGER".equals(access.role()) || !access.facilities().contains(facilityId))
            throw new BusinessException(HttpStatus.FORBIDDEN,"Bạn không được quản lý cơ sở này.");
    }
    private void business() {
        if (!"BUSINESS_OPERATIONS_MANAGER".equals(access.role()))
            throw new BusinessException(HttpStatus.FORBIDDEN,"Chỉ quản lý kinh doanh được duyệt chính sách.");
    }
    private void audit(String action,UUID id,String detail) {
        logs.save(new ActivityLog(access.currentId(),action,"OVERDUE_POLICY_PROPOSAL",id,detail,access.now()));
    }
    private void conflict(String message) { throw new BusinessException(HttpStatus.CONFLICT,message); }
}
