package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.ActivityLog;
import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.ActivityLogRepository;
import com.safebox.self_storage.repository.FacilityRepository;
import com.safebox.self_storage.repository.UserFacilityAssignmentRepository;
import com.safebox.self_storage.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator provisioning for the existing UC24 facility-based business scope. */
@Service
public class BusinessReportScopeService {
    private final Sprint2Access access;
    private final UserRepository users;
    private final FacilityRepository facilities;
    private final UserFacilityAssignmentRepository assignments;
    private final ActivityLogRepository logs;
    private final EntityManager entityManager;

    public BusinessReportScopeService(Sprint2Access access, UserRepository users,
            FacilityRepository facilities, UserFacilityAssignmentRepository assignments,
            ActivityLogRepository logs, EntityManager entityManager) {
        this.access = access;
        this.users = users;
        this.facilities = facilities;
        this.assignments = assignments;
        this.logs = logs;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<UUID> list(UUID managerId) {
        requireAdmin();
        requireBusinessManager(managerId);
        return assignments.findActiveFacilityIds(managerId, access.today());
    }

    @Transactional
    public List<UUID> grant(UUID managerId, UUID facilityId) {
        requireAdmin();
        requireBusinessManager(managerId);
        if (!facilities.existsById(facilityId))
            throw new BusinessException(HttpStatus.NOT_FOUND, "Facility was not found.");
        if (!assignments.isUserAssignedToFacility(managerId, facilityId, access.today())) {
            entityManager.createNativeQuery("INSERT INTO USER_FACILITY_ASSIGNMENTS "
                    + "(assignment_id,user_id,facility_id,assigned_by,start_date,status) "
                    + "VALUES (NEWID(),:userId,:facilityId,:adminId,:today,'ACTIVE')")
                    .setParameter("userId", managerId)
                    .setParameter("facilityId", facilityId)
                    .setParameter("adminId", access.currentId())
                    .setParameter("today", access.today())
                    .executeUpdate();
            logs.save(new ActivityLog(access.currentId(), "GRANT_REPORT_SCOPE", "USER", managerId,
                    "facilityId=" + facilityId, access.now()));
        }
        return assignments.findActiveFacilityIds(managerId, access.today());
    }

    @Transactional
    public List<UUID> revoke(UUID managerId, UUID facilityId) {
        requireAdmin();
        requireBusinessManager(managerId);
        int count = entityManager.createNativeQuery("UPDATE USER_FACILITY_ASSIGNMENTS "
                + "SET status='INACTIVE', end_date=:today WHERE user_id=:userId AND facility_id=:facilityId "
                + "AND status='ACTIVE' AND start_date<=:today AND (end_date IS NULL OR end_date>=:today)")
                .setParameter("today", access.today())
                .setParameter("userId", managerId)
                .setParameter("facilityId", facilityId)
                .executeUpdate();
        if (count == 0)
            throw new BusinessException(HttpStatus.NOT_FOUND, "Active report scope was not found.");
        logs.save(new ActivityLog(access.currentId(), "REVOKE_REPORT_SCOPE", "USER", managerId,
                "facilityId=" + facilityId, access.now()));
        return assignments.findActiveFacilityIds(managerId, access.today());
    }

    private void requireAdmin() {
        if (!"SYSTEM_ADMIN".equals(access.role()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only administrators can manage report scope.");
    }

    private void requireBusinessManager(UUID managerId) {
        User user = users.findById(managerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User was not found."));
        if (!"ACTIVE".equals(user.getStatus()) || user.getRole() == null
                || !"BUSINESS_OPERATIONS_MANAGER".equals(user.getRole().getRoleName()))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "User is not an active Business Operations Manager.");
    }
}
