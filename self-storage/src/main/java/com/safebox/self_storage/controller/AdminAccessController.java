package com.safebox.self_storage.controller;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.Sprint2Access;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/access") @PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminAccessController {
    public record UserSummary(UUID userId, String fullName, String email, String role, String status, List<UUID> facilityIds, List<String> regionCodes) {}
    public record RoleChange(@NotBlank String role) {}
    public record StatusChange(@NotBlank String status) {}
    public record RegionChange(@NotBlank String regionCode) {}
    private static final Set<String> ROLES = Set.of("CUSTOMER", "FACILITY_STAFF", "FACILITY_MANAGER", "BUSINESS_OPERATIONS_MANAGER", "SYSTEM_ADMIN");
    private final UserRepository users; private final RoleRepository roles; private final FacilityRepository facilities;
    private final UserFacilityAssignmentRepository assignments; private final StaffRegionAssignmentRepository regions;
    private final ActivityLogRepository logs; private final Sprint2Access access; private final EntityManager em;
    public AdminAccessController(UserRepository users, RoleRepository roles, FacilityRepository facilities,
            UserFacilityAssignmentRepository assignments, StaffRegionAssignmentRepository regions,
            ActivityLogRepository logs, Sprint2Access access, EntityManager em) {
        this.users=users; this.roles=roles; this.facilities=facilities; this.assignments=assignments;
        this.regions=regions; this.logs=logs; this.access=access; this.em=em;
    }
    @GetMapping("/users")
    public List<UserSummary> users() { return users.findAll().stream().map(this::summary).toList(); }
    @GetMapping("/facilities")
    public List<Facility> facilities() { return facilities.findAll(); }
    @GetMapping("/audit")
    public List<ActivityLog> audit() { return logs.findTop100ByOrderByCreatedAtDesc(); }
    @PutMapping("/users/{id}/role") @Transactional
    public UserSummary role(@PathVariable UUID id, @Valid @RequestBody RoleChange change) {
        if (!ROLES.contains(change.role())) bad("Vai trò không hợp lệ.");
        User target = target(id);
        protectFinalAdmin(target, !"SYSTEM_ADMIN".equals(change.role()));
        if (!target.getRole().getRoleName().equals(change.role())) {
            String old = target.getRole().getRoleName();
            target.setRole(roles.findByRoleName(change.role()).orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Vai trò chưa được cấu hình.")));
            users.save(target);
            revokeScopes(id);
            log("CHANGE_ROLE", id, old + " -> " + change.role());
        }
        return summary(target);
    }
    @PutMapping("/users/{id}/status") @Transactional
    public UserSummary status(@PathVariable UUID id, @Valid @RequestBody StatusChange change) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(change.status())) bad("Trạng thái tài khoản không hợp lệ.");
        User target = target(id);
        protectFinalAdmin(target, "INACTIVE".equals(change.status()));
        target.setStatus(change.status()); users.save(target);
        if ("INACTIVE".equals(change.status())) revokeScopes(id);
        log("CHANGE_USER_STATUS", id, change.status()); return summary(target);
    }
    @PostMapping("/managers/{id}/facilities/{facilityId}") @Transactional
    public UserSummary grantManager(@PathVariable UUID id, @PathVariable UUID facilityId) {
        User target = requireRole(id, "FACILITY_MANAGER");
        if (!facilities.existsById(facilityId)) throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở.");
        if (!assignments.isUserAssignedToFacility(id, facilityId, access.today())) {
            em.createNativeQuery("INSERT INTO USER_FACILITY_ASSIGNMENTS (assignment_id,user_id,facility_id,assigned_by,start_date,status) VALUES (NEWID(),:user,:facility,:admin,:today,'ACTIVE')")
                .setParameter("user",id).setParameter("facility",facilityId).setParameter("admin",access.currentId()).setParameter("today",access.today()).executeUpdate();
            log("GRANT_MANAGER_FACILITY", id, facilityId.toString());
        }
        return summary(target);
    }
    @DeleteMapping("/managers/{id}/facilities/{facilityId}") @Transactional
    public UserSummary revokeManager(@PathVariable UUID id, @PathVariable UUID facilityId) {
        User target = requireRole(id, "FACILITY_MANAGER");
        int changed = em.createNativeQuery("UPDATE USER_FACILITY_ASSIGNMENTS SET status='INACTIVE',end_date=:today WHERE user_id=:user AND facility_id=:facility AND status='ACTIVE'")
            .setParameter("today",access.today()).setParameter("user",id).setParameter("facility",facilityId).executeUpdate();
        if (changed == 0) throw new BusinessException(HttpStatus.NOT_FOUND, "Chưa cấp quyền tại cơ sở này.");
        log("REVOKE_MANAGER_FACILITY", id, facilityId.toString()); return summary(target);
    }
    @PostMapping("/staff/{id}/regions") @Transactional
    public UserSummary grantRegion(@PathVariable UUID id, @Valid @RequestBody RegionChange change) {
        User target = requireRole(id, "FACILITY_STAFF");
        if (!change.regionCode().matches("[A-Z0-9_-]{2,30}") || facilities.findByRegionCodeIgnoreCase(change.regionCode()).isEmpty()) bad("Mã vùng chưa có cơ sở.");
        if (!regions.existsByUserIdAndRegionCodeAndStatus(id, change.regionCode(), "ACTIVE")) {
            StaffRegionAssignment assignment = new StaffRegionAssignment();
            assignment.setAssignmentId(UUID.randomUUID()); assignment.setUserId(id); assignment.setRegionCode(change.regionCode());
            assignment.setStatus("ACTIVE"); assignment.setAssignedBy(access.currentId()); assignment.setAssignedAt(LocalDateTime.now());
            regions.save(assignment); log("GRANT_STAFF_REGION", id, change.regionCode());
        }
        return summary(target);
    }
    @DeleteMapping("/staff/{id}/regions/{regionCode}") @Transactional
    public UserSummary revokeRegion(@PathVariable UUID id, @PathVariable String regionCode) {
        User target = requireRole(id, "FACILITY_STAFF");
        List<StaffRegionAssignment> active = regions.findByUserIdAndStatus(id, "ACTIVE").stream().filter(r -> r.getRegionCode().equals(regionCode)).toList();
        if (active.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "Chưa cấp quyền vùng này.");
        active.forEach(r -> r.setStatus("INACTIVE")); regions.saveAll(active);
        log("REVOKE_STAFF_REGION", id, regionCode); return summary(target);
    }
    private User target(UUID id) { return users.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản.")); }
    private User requireRole(UUID id, String role) { User u=target(id); if (!role.equals(u.getRole().getRoleName()) || !"ACTIVE".equals(u.getStatus())) bad("Tài khoản không thuộc vai trò đang hoạt động."); return u; }
    private void protectFinalAdmin(User target, boolean removing) {
        if (removing && "SYSTEM_ADMIN".equals(target.getRole().getRoleName()) && "ACTIVE".equals(target.getStatus())
                && users.lockActiveAdministrators().size() <= 1)
            throw new BusinessException(HttpStatus.CONFLICT, "Không thể vô hiệu hóa quản trị viên cuối cùng.");
    }
    private void revokeScopes(UUID id) {
        em.createNativeQuery("UPDATE USER_FACILITY_ASSIGNMENTS SET status='INACTIVE',end_date=:today WHERE user_id=:user AND status='ACTIVE'")
            .setParameter("today",access.today()).setParameter("user",id).executeUpdate();
        regions.findByUserIdAndStatus(id,"ACTIVE").forEach(r -> r.setStatus("INACTIVE"));
    }
    private UserSummary summary(User u) { return new UserSummary(u.getUserId(),u.getFullName(),u.getEmail(),u.getRole().getRoleName(),u.getStatus(),
        assignments.findActiveFacilityIds(u.getUserId(),access.today()), regions.findByUserIdAndStatus(u.getUserId(),"ACTIVE").stream().map(StaffRegionAssignment::getRegionCode).toList()); }
    private void log(String action, UUID id, String detail) { logs.save(new ActivityLog(access.currentId(),action,"USER",id,detail,access.now())); }
    private void bad(String message) { throw new BusinessException(HttpStatus.BAD_REQUEST,message); }
}
