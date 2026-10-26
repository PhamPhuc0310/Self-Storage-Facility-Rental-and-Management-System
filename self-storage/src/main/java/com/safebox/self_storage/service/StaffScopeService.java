package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.support.StaffFacilityAssignmentRequest;
import com.safebox.self_storage.dto.support.StaffScopeView;
import com.safebox.self_storage.entity.ActivityLog;
import com.safebox.self_storage.entity.Facility;
import com.safebox.self_storage.entity.User;
import com.safebox.self_storage.entity.UserFacilityAssignment;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service xử lý nghiệp vụ Use Case UC23:
 * "Quản lý nhân viên và phạm vi hoạt động" (BR23.1 – BR23.4).
 */
@Service
public class StaffScopeService {

    private final Sprint2Access access;
    private final UserRepository users;
    private final FacilityRepository facilities;
    private final UserFacilityAssignmentRepository assignments;
    private final SupportTicketRepository tickets;
    private final PickupAppointmentRepository pickupAppointments;
    private final HandoverRecordRepository handoverRecords;
    private final ActivityLogRepository logs;

    public StaffScopeService(Sprint2Access access,
                             UserRepository users,
                             FacilityRepository facilities,
                             UserFacilityAssignmentRepository assignments,
                             SupportTicketRepository tickets,
                             PickupAppointmentRepository pickupAppointments,
                             HandoverRecordRepository handoverRecords,
                             ActivityLogRepository logs) {
        this.access = access;
        this.users = users;
        this.facilities = facilities;
        this.assignments = assignments;
        this.tickets = tickets;
        this.pickupAppointments = pickupAppointments;
        this.handoverRecords = handoverRecords;
        this.logs = logs;
    }

    /**
     * BR23.1: SYSTEM_ADMIN xem danh sách nhân viên cơ sở và phạm vi hoạt động hiện tại.
     */
    @Transactional(readOnly = true)
    public List<StaffScopeView> staff() {
        requireAdmin();
        return users.findAll().stream()
                .filter(u -> u.getRole() != null
                        && "FACILITY_STAFF".equals(u.getRole().getRoleName())
                        && "ACTIVE".equals(u.getStatus()))
                .map(this::toView)
                .toList();
    }

    /**
     * BR23.1, BR23.2: SYSTEM_ADMIN cấp quyền hoạt động tại một cơ sở cho nhân viên.
     */
    @Transactional
    public StaffScopeView grant(UUID staffId, StaffFacilityAssignmentRequest request) {
        requireAdmin();
        User staffUser = getStaffUser(staffId);

        if (!facilities.existsById(request.facilityId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở.");
        }

        // Nếu nhân viên đã có quyền hoạt động tại cơ sở này, trả về thông tin hiện tại (idempotent)
        if (assignments.isUserAssignedToFacility(staffId, request.facilityId(), access.today())) {
            return toView(staffUser);
        }

        UserFacilityAssignment assignment = new UserFacilityAssignment(
                UUID.randomUUID(),
                staffId,
                request.facilityId(),
                access.currentId(),
                access.today(),
                null,
                "ACTIVE"
        );
        assignments.save(assignment);

        logs.save(new ActivityLog(
                access.currentId(),
                "GRANT_STAFF_FACILITY",
                "USER",
                staffId,
                "facilityId=" + request.facilityId(),
                access.now()
        ));

        return toView(staffUser);
    }

    /**
     * BR23.3: SYSTEM_ADMIN thu hồi quyền hoạt động tại cơ sở của nhân viên.
     * Ràng buộc: Kiểm tra nhân viên không còn ticket, lịch hẹn nhận kho hay lịch trả kho đang mở.
     */
    @Transactional
    public StaffScopeView revoke(UUID staffId, UUID facilityId) {
        requireAdmin();
        User staffUser = getStaffUser(staffId);

        // 1. Kiểm tra ticket hỗ trợ đang mở (BR23.3)
        long openTickets = tickets.countOpenWork(staffId, facilityId, List.of("OPEN", "IN_PROGRESS", "RESOLVED"));
        if (openTickets > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Nhân viên còn ticket đang mở tại cơ sở này. Hãy giao lại ticket trước khi thu hồi.");
        }

        // 2. Kiểm tra lịch nhận kho / bàn giao đang SCHEDULED (BR23.3)
        long openPickups = pickupAppointments.countScheduledAppointments(staffId, facilityId);
        if (openPickups > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Nhân viên còn lịch nhận kho đang phụ trách tại cơ sở này. Hãy điều chuyển lịch trước.");
        }

        // 3. Kiểm tra biên bản nghiệm thu trả kho đang mở (BR23.3)
        long openHandovers = handoverRecords.countPendingHandoverWork(staffId, facilityId);
        if (openHandovers > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Nhân viên còn biên bản kiểm tra trả kho đang mở tại cơ sở này. Hãy hoàn tất hoặc giao lại.");
        }

        // 4. Tìm và vô hiệu hóa phân công hoạt động
        List<UserFacilityAssignment> activeList = assignments.findActiveAssignments(staffId, facilityId, access.today());
        if (activeList.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Nhân viên không có phạm vi hoạt động tại cơ sở này.");
        }

        for (UserFacilityAssignment a : activeList) {
            a.setStatus("INACTIVE");
            a.setEndDate(access.today().minusDays(1));
        }
        assignments.saveAll(activeList);

        logs.save(new ActivityLog(
                access.currentId(),
                "REVOKE_STAFF_FACILITY",
                "USER",
                staffId,
                "facilityId=" + facilityId,
                access.now()
        ));

        return toView(staffUser);
    }

    private StaffScopeView toView(User u) {
        List<UUID> facilityIds = assignments.findActiveFacilityIds(u.getUserId(), access.today());
        List<StaffScopeView.FacilityView> facilityViews = facilityIds.stream()
                .map(id -> facilities.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(f -> new StaffScopeView.FacilityView(f.getFacilityId(), f.getName(), f.getAddress()))
                .toList();

        return new StaffScopeView(u.getUserId(), u.getFullName(), u.getEmail(), facilityViews);
    }

    private User getStaffUser(UUID id) {
        User u = users.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên."));
        if (u.getRole() == null || !"FACILITY_STAFF".equals(u.getRole().getRoleName())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Tài khoản không phải nhân viên cơ sở.");
        }
        return u;
    }

    private void requireAdmin() {
        if (!"SYSTEM_ADMIN".equals(access.role())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ quản trị viên hệ thống được quản lý phạm vi nhân viên.");
        }
    }
}
