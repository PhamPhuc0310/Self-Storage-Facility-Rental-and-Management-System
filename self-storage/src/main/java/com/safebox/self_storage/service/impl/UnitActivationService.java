package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.manager.MeasurementSubmissionRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.Sprint2Access;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class UnitActivationService {
    public record ActivationCheck(boolean eligible,List<String> reasons) {}
    private final Sprint2Access access;
    private final FacilityRepository facilities;
    private final StorageUnitRepository units;
    private final StorageUnitTypeRepository types;
    private final UserFacilityAssignmentRepository assignments;
    private final UnitMeasurementSubmissionRepository submissions;
    private final PricingPolicyRepository prices;
    private final RentalContractRepository contracts;
    private final ReservationRepository reservations;
    private final ActivityLogRepository logs;
    private final JdbcTemplate jdbc;
    public UnitActivationService(Sprint2Access access,FacilityRepository facilities,StorageUnitRepository units,
            StorageUnitTypeRepository types,UserFacilityAssignmentRepository assignments,
            UnitMeasurementSubmissionRepository submissions,PricingPolicyRepository prices,
            RentalContractRepository contracts,ReservationRepository reservations,ActivityLogRepository logs,JdbcTemplate jdbc) {
        this.access=access;this.facilities=facilities;this.units=units;this.types=types;this.assignments=assignments;
        this.submissions=submissions;this.prices=prices;this.contracts=contracts;this.reservations=reservations;this.logs=logs;this.jdbc=jdbc;
    }
    private Facility scope(UUID facilityId,String requiredRole) {
        if (!requiredRole.equals(access.role()) || !assignments.isUserAssignedToFacility(access.currentId(),facilityId,access.today()))
            throw new BusinessException(HttpStatus.FORBIDDEN,"Bạn không được thao tác tại cơ sở này.");
        return facilities.findById(facilityId).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy cơ sở."));
    }
    private StorageUnit unit(UUID facilityId,UUID unitId) {
        StorageUnit u=units.lockById(unitId).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy kho."));
        if (!facilityId.equals(u.getFacilityId())) throw new BusinessException(HttpStatus.FORBIDDEN,"Kho ngoài cơ sở được phân công.");
        return u;
    }
    @Transactional(readOnly=true)
    public List<UnitMeasurementSubmission> staffSubmissions(UUID facilityId) {
        scope(facilityId,"FACILITY_STAFF");
        return submissions.findByFacilityIdOrderBySubmittedAtDesc(facilityId);
    }
    @Transactional(readOnly=true)
    public List<StorageUnit> staffUnits(UUID facilityId) {
        scope(facilityId,"FACILITY_STAFF");
        return units.findByFacilityIdOrderByUnitNumberAsc(facilityId).stream()
                .filter(u->!u.isDimensionsVerified() && !"OCCUPIED".equals(u.getStatus()) && !"RESERVED".equals(u.getStatus())).toList();
    }
    @Transactional(readOnly=true)
    public List<Facility> staffFacilities() {
        if (!"FACILITY_STAFF".equals(access.role())) throw new BusinessException(HttpStatus.FORBIDDEN,"Chỉ nhân viên cơ sở được gửi số đo.");
        return access.facilities().stream().map(facilities::findById).flatMap(Optional::stream).toList();
    }
    public UnitMeasurementSubmission submit(UUID facilityId,UUID unitId,MeasurementSubmissionRequest r) {
        scope(facilityId,"FACILITY_STAFF");
        StorageUnit u=unit(facilityId,unitId);
        if (u.isDimensionsVerified() || "OCCUPIED".equals(u.getStatus()) || "RESERVED".equals(u.getStatus()))
            throw new BusinessException(HttpStatus.CONFLICT,"Kho đã xác minh hoặc đang được sử dụng; không thể gửi số đo.");
        if (submissions.existsByUnitIdAndStatus(unitId,"SUBMITTED"))
            throw new BusinessException(HttpStatus.CONFLICT,"Kho đã có phiếu số đo đang chờ duyệt.");
        if (r==null || r.lengthM()==null || r.widthM()==null || r.heightM()==null ||
                r.lengthM().signum()<=0 || r.widthM().signum()<=0 || r.heightM().signum()<=0 ||
                r.conditionNote()==null || r.conditionNote().isBlank())
            throw new BusinessException(HttpStatus.BAD_REQUEST,"Cần đủ ba số đo dương và ghi nhận tình trạng kho.");
        if (!Set.of("PHYSICAL_INSPECTION","SYNTHETIC_DEMO").contains(r.source()))
            throw new BusinessException(HttpStatus.BAD_REQUEST,"Nguồn số đo không hợp lệ.");
        if ("SYNTHETIC_DEMO".equals(r.source())) {
            String database=jdbc.queryForObject("SELECT DB_NAME()",String.class);
            if (!"SelfStorageFacilityDB_Test".equals(database) || !u.getUnitNumber().startsWith("PH45-DEMO-"))
                throw new BusinessException(HttpStatus.FORBIDDEN,"Số đo mô phỏng chỉ dùng cho kho demo riêng trong cơ sở dữ liệu thử nghiệm.");
        }
        UnitMeasurementSubmission s=new UnitMeasurementSubmission();
        s.submissionId=UUID.randomUUID();s.facilityId=facilityId;s.unitId=unitId;
        s.lengthM=r.lengthM();s.widthM=r.widthM();s.heightM=r.heightM();s.conditionNote=r.conditionNote().trim();
        s.source=r.source();s.status="SUBMITTED";s.submittedBy=access.currentId();s.submittedAt=access.now();
        submissions.save(s);
        log("SUBMIT_UNIT_MEASUREMENT",unitId,"Nguồn="+s.source+"; số đo="+s.lengthM+"×"+s.widthM+"×"+s.heightM+"m; tình trạng="+s.conditionNote);
        return s;
    }
    @Transactional(readOnly=true)
    public List<UnitMeasurementSubmission> reviews(UUID facilityId) {
        scope(facilityId,"FACILITY_MANAGER");
        return submissions.findByFacilityIdOrderBySubmittedAtDesc(facilityId);
    }
    public UnitMeasurementSubmission decide(UUID facilityId,UUID submissionId,boolean approve,String reason) {
        scope(facilityId,"FACILITY_MANAGER");
        UnitMeasurementSubmission s=submissions.findById(submissionId).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy phiếu số đo."));
        if (!facilityId.equals(s.facilityId)) throw new BusinessException(HttpStatus.FORBIDDEN,"Phiếu ngoài cơ sở được phân công.");
        if (!"SUBMITTED".equals(s.status)) throw new BusinessException(HttpStatus.CONFLICT,"Phiếu đã được xử lý.");
        if (reason==null || reason.isBlank() || reason.length()>1000) throw new BusinessException(HttpStatus.BAD_REQUEST,"Cần ghi rõ lý do quyết định.");
        StorageUnit u=unit(facilityId,s.unitId);
        if (approve) {
            if (u.isDimensionsVerified() || "OCCUPIED".equals(u.getStatus()) || "RESERVED".equals(u.getStatus()))
                throw new BusinessException(HttpStatus.CONFLICT,"Kho đã xác minh hoặc đang được sử dụng.");
            u.setLengthM(s.lengthM);u.setWidthM(s.widthM);u.setHeightM(s.heightM);
            u.setDimensionsVerified(true);u.setMeasurementSource(s.source);
            units.save(u);
        }
        s.status=approve?"APPROVED":"REJECTED";s.reviewedBy=access.currentId();s.reviewedAt=access.now();s.decisionReason=reason.trim();
        submissions.save(s);
        log(approve?"APPROVE_UNIT_MEASUREMENT":"REJECT_UNIT_MEASUREMENT",u.getUnitId(),
                "Nguồn="+s.source+"; lý do="+s.decisionReason);
        return s;
    }
    @Transactional(readOnly=true)
    public ActivationCheck check(UUID facilityId,UUID unitId) {
        Facility f=scope(facilityId,"FACILITY_MANAGER");
        StorageUnit u=unit(facilityId,unitId);
        return evaluate(f,u);
    }
    public StorageUnit activate(UUID facilityId,UUID unitId) {
        Facility f=scope(facilityId,"FACILITY_MANAGER");
        StorageUnit u=unit(facilityId,unitId);
        ActivationCheck result=evaluate(f,u);
        if (!result.eligible()) throw new BusinessException(HttpStatus.CONFLICT,String.join(" ",result.reasons()));
        u.setStatus("AVAILABLE");
        units.save(u);
        log("ACTIVATE_UNIT",unitId,"Mở cho thuê; thể tích="+u.getVolumeM3()+" m³; nguồn="+u.getMeasurementSource());
        return u;
    }
    private ActivationCheck evaluate(Facility f,StorageUnit u) {
        List<String> reasons=new ArrayList<>();
        if (!"ACTIVE".equals(f.getStatus())) reasons.add("Cơ sở chưa hoạt động.");
        if (!"INACTIVE".equals(u.getStatus())) reasons.add("Kho phải ở trạng thái ngừng sử dụng; kho bảo trì không được mở.");
        if (!u.isDimensionsVerified() || u.getVolumeM3()==null || u.getVolumeM3().signum()<=0) reasons.add("Chưa có ba kích thước được xác minh.");
        if (!u.isReadinessVerified()) reasons.add("Chưa xác nhận sẵn sàng vận hành.");
        StorageUnitType type=types.findById(u.getTypeId()).orElse(null);
        if (type==null || !"ACTIVE".equals(type.getStatus())) reasons.add("Loại kho chưa hoạt động.");
        if (CurrentPricing.forType(prices.findByFacilityIdAndStatus(f.getFacilityId(),"ACTIVE"),u.getTypeId(),access.today())
                .filter(p->p.getMonthlyPrice()!=null && p.getMonthlyPrice().signum()>0).isEmpty()) reasons.add("Chưa có giá thuê dương đang hiệu lực.");
        if (contracts.existsByUnitIdAndStatusIn(u.getUnitId(), List.of("PENDING_CONFIRMATION", "CONFIRMED", "PENDING_HANDOVER", "ACTIVE", "OVERDUE")))
            reasons.add("Kho còn hợp đồng chưa kết thúc.");
        if (reservations.countActivePendingOverlapping(f.getFacilityId(),u.getTypeId(),access.today(),access.today().plusMonths(36),access.now())>0)
            reasons.add("Loại kho đang có lượt giữ chỗ chờ xử lý; cần kiểm tra sức chứa trước khi mở.");
        return new ActivationCheck(reasons.isEmpty(),List.copyOf(reasons));
    }
    private void log(String action,UUID unitId,String detail) {
        logs.save(new ActivityLog(access.currentId(),action,"STORAGE_UNIT",unitId,detail,access.now()));
    }
}
