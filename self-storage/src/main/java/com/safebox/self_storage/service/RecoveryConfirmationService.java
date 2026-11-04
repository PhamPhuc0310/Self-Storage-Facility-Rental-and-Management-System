package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records a documented recovery; the unit remains unavailable pending separate readiness review. */
@Service
public class RecoveryConfirmationService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final RentalContractRepository contracts;
    private final StorageUnitRepository units;
    private final UserRepository users;
    private final UserFacilityAssignmentRepository scopes;
    private final HandoverRecordRepository handovers;
    private final RenewalRepository renewals;
    private final ActivityLogRepository logs;
    public RecoveryConfirmationService(RentalContractRepository contracts, StorageUnitRepository units, UserRepository users,
            UserFacilityAssignmentRepository scopes, HandoverRecordRepository handovers, RenewalRepository renewals, ActivityLogRepository logs) {
        this.contracts=contracts; this.units=units; this.users=users; this.scopes=scopes;
        this.handovers=handovers; this.renewals=renewals; this.logs=logs;
    }
    public record Request(String recoveryReference, String authorityReference, LocalDateTime completedAt,
                          boolean possessionConfirmed, boolean emptyConfirmed) {}
    public record Result(UUID contractId, String contractStatus, String unitStatus, boolean alreadyRecorded) {}
    @Transactional
    public Result confirm(UUID managerId, UUID contractId, Request request) {
        RentalContract c=contracts.lockById(contractId).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy hợp đồng."));
        StorageUnit u=units.lockById(c.getUnitId()).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy kho."));
        User manager=users.findById(managerId).orElseThrow(()->new BusinessException(HttpStatus.UNAUTHORIZED,"Không tìm thấy tài khoản."));
        LocalDateTime now=LocalDateTime.now(ZONE);
        if (!"ACTIVE".equals(manager.getStatus()) || manager.getRole()==null || !"FACILITY_MANAGER".equals(manager.getRole().getRoleName())
                || !scopes.isUserAssignedToFacility(managerId,u.getFacilityId(),now.toLocalDate()))
            throw new BusinessException(HttpStatus.FORBIDDEN,"Bạn không quản lý cơ sở của hợp đồng này.");
        if (logs.existsByActionTypeAndEntityId("CONFIRM_UNIT_RECOVERY",contractId))
            return new Result(contractId,c.getStatus(),u.getStatus(),true);
        if (request==null || !request.possessionConfirmed() || !request.emptyConfirmed()
                || request.recoveryReference()==null || request.recoveryReference().isBlank() || request.recoveryReference().length()>200
                || request.authorityReference()==null || request.authorityReference().isBlank() || request.authorityReference().length()>200
                || request.completedAt()==null || request.completedAt().isAfter(now)
                || c.getEndDate()==null || request.completedAt().toLocalDate().isBefore(c.getEndDate()))
            throw new BusinessException(HttpStatus.BAD_REQUEST,"Cần xác nhận kho đã được nhận lại, đã trống, kèm căn cứ, biên bản và thời điểm thực tế hợp lệ.");
        if (!"ACTIVE".equals(c.getStatus()) && !"OVERDUE".equals(c.getStatus())
                || ChronoUnit.DAYS.between(c.getEndDate(),request.completedAt().toLocalDate())<30 || !"OCCUPIED".equals(u.getStatus())
                || handovers.findFirstByContractIdAndHandoverTypeAndStatus(contractId,"RETURN","COMPLETED").isPresent()
                || handovers.findFirstByContractIdAndHandoverTypeAndStatus(contractId,"RETURN","SCHEDULED").isPresent()
                || handovers.findFirstByContractIdAndHandoverTypeAndStatus(contractId,"RETURN","IN_PROGRESS").isPresent()
                || !renewals.findByContractIdAndStatusIn(contractId,List.of("PENDING","APPROVED")).isEmpty())
            throw new BusinessException(HttpStatus.CONFLICT,"Hợp đồng chưa đủ điều kiện hoặc đang có lịch trả, yêu cầu gia hạn cần xử lý.");
        c.setStatus("RETURNED"); c.setActualEndDate(request.completedAt().toLocalDate());
        u.setStatus("MAINTENANCE"); u.setReadinessVerified(false);
        contracts.save(c); units.save(u);
        logs.save(new ActivityLog(managerId,"CONFIRM_UNIT_RECOVERY","RENTAL_CONTRACT",contractId,
                "Biên bản: "+request.recoveryReference().trim()+"; căn cứ: "+request.authorityReference().trim()
                +"; hoàn tất: "+request.completedAt()+"; kho đã trống theo xác nhận",now));
        return new Result(contractId,c.getStatus(),u.getStatus(),false);
    }
}
