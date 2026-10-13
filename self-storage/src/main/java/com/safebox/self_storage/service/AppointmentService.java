package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.AppointmentRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {
    public record StaffOption(UUID id, String name) {}
    public record DailyTask(String taskType, UUID contractId, UUID facilityId, String contractCode, String customerName,
                            String storageUnitCode, LocalDateTime appointmentTime, UUID assignedStaffId,
                            String assignedStaffName, String facilityName, boolean readyForHandover, String status,
                            BigDecimal depositPaidAmount, BigDecimal depositRemainingAmount, BigDecimal remainingRentAmount) {}
    private final Sprint2Access access;
    private final PickupAppointmentRepository appointments;
    private final RentalContractRepository contracts;
    private final FacilityRepository facilities;
    private final StorageUnitRepository units;
    private final UserRepository users;
    private final UserFacilityAssignmentRepository assignments;
    private final InvoiceRepository invoices;
    private final PaymentService payments;
    public AppointmentService(Sprint2Access access, PickupAppointmentRepository appointments, RentalContractRepository contracts, FacilityRepository facilities, StorageUnitRepository units,
                              UserRepository users, UserFacilityAssignmentRepository assignments, InvoiceRepository invoices, PaymentService payments) {
        this.access = access; this.appointments = appointments; this.contracts = contracts; this.facilities = facilities; this.units = units;
        this.users = users; this.assignments = assignments; this.invoices = invoices; this.payments = payments;
    }
    @Transactional(readOnly = true)
    public PickupAppointment get(UUID id) {
        access.contract(id, false);
        PickupAppointment row = appointments.findFirstByContractIdOrderByCreatedAtDesc(id).orElse(null);
        if (row != null && "FACILITY_STAFF".equals(access.role()) && !access.currentId().equals(row.assignedStaffId))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn chưa được phân công lịch nhận kho này.");
        return row;
    }
    public com.safebox.self_storage.dto.AppointmentView view(PickupAppointment a) {
        if (a == null) return null;
        String staffName = a.assignedStaffId == null ? null : users.findById(a.assignedStaffId).map(User::getFullName).orElse(null);
        return new com.safebox.self_storage.dto.AppointmentView(a.id, a.contractId, a.appointmentTime,
                a.assignedStaffId, staffName, a.status);
    }
    private LocalDateTime validate(RentalContract r, AppointmentRequest request) {
        if (request == null || request.appointmentTime() == null || !request.appointmentTime().getOffset().equals(ZoneOffset.ofHours(7)))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ngày giờ phải dùng múi giờ Việt Nam (+07:00).");
        LocalDateTime when = request.appointmentTime().toLocalDateTime();
        if (!when.isAfter(access.now())) throw new BusinessException(HttpStatus.BAD_REQUEST, "Không thể đặt lịch trong quá khứ.");
        if (when.toLocalDate().isBefore(r.startDate) || when.toLocalDate().isAfter(r.endDate))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Lịch nhận kho phải nằm trong thời hạn thuê.");
        if (when.getMinute() != 0 || when.getSecond() != 0 || when.getNano() != 0 ||
                !Set.of(8, 9, 10, 11, 13, 14, 15, 16).contains(when.getHour()))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Vui lòng chọn khung giờ nhận kho hợp lệ.");
        Facility facility = facilities.findById(access.facilityId(r)).orElseThrow();
        LocalTime opening = facility.getOpeningTime() == null ? LocalTime.of(8, 0) : facility.getOpeningTime();
        LocalTime closing = facility.getClosingTime() == null ? LocalTime.of(17, 0) : facility.getClosingTime();
        if (when.toLocalTime().isBefore(opening) || !when.toLocalTime().isBefore(closing))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cơ sở không hoạt động vào khung giờ đã chọn.");
        return when;
    }
    @Transactional
    public PickupAppointment create(UUID id, AppointmentRequest request) {
        RentalContract r = access.contract(id, true); access.confirmed(r);
        if (!"CUSTOMER".equals(access.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ khách hàng được đặt lịch nhận kho.");
        requireDepositForScheduling(r);
        if (appointments.findFirstByContractIdAndStatus(id, "SCHEDULED").isPresent())
            throw new BusinessException(HttpStatus.CONFLICT, "Hợp đồng đã có lịch nhận kho đang hoạt động.");
        if (appointments.existsByContractIdAndStatus(id, "COMPLETED"))
            throw new BusinessException(HttpStatus.CONFLICT, "Kho đã được bàn giao.");
        LocalDateTime now = access.now();
        PickupAppointment a = new PickupAppointment();
        a.id = UUID.randomUUID(); a.contractId = id; a.facilityId = access.facilityId(r); a.customerId = r.customerId;
        a.appointmentTime = validate(r, request); a.status = "SCHEDULED"; a.createdAt = now; a.updatedAt = now;
        // Surface SQL constraint/write failures before reporting the appointment as created.
        PickupAppointment saved = appointments.saveAndFlush(a);
        updateRemainingDueDate(r, saved.appointmentTime);
        return saved;
    }
    private PickupAppointment editable(UUID id) {
        RentalContract r = access.contract(id, true); access.confirmed(r);
        if (!"CUSTOMER".equals(access.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ khách hàng được đổi hoặc hủy lịch.");
        PickupAppointment a = appointments.findFirstByContractIdAndStatus(id, "SCHEDULED")
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Không có lịch nhận kho đang hoạt động."));
        if (!access.now().isBefore(a.appointmentTime)) throw new BusinessException(HttpStatus.CONFLICT, "Đã đến giờ nhận kho, không thể thay đổi lịch.");
        return a;
    }
    @Transactional
    public PickupAppointment reschedule(UUID id, AppointmentRequest request) {
        PickupAppointment a = editable(id);
        RentalContract r = access.contract(id, false);
        requireDepositForScheduling(r);
        a.appointmentTime = validate(r, request); a.assignedStaffId = null; a.updatedAt = access.now();
        updateRemainingDueDate(r, a.appointmentTime);
        return appointments.save(a);
    }
    @Transactional
    public PickupAppointment cancel(UUID id) {
        PickupAppointment a = editable(id);
        a.status = "CANCELLED"; a.assignedStaffId = null; a.updatedAt = access.now(); return appointments.save(a);
    }
    private void requireDepositForScheduling(RentalContract contract) {
        if ("v2".equals(contract.termsVersion) && payments.calculate(contract).deposit().remainingAmount().signum() > 0)
            throw new BusinessException(HttpStatus.CONFLICT, "Vui lòng thanh toán đủ tiền cọc trước khi đặt hoặc đổi lịch.");
    }
    private void updateRemainingDueDate(RentalContract contract, LocalDateTime appointmentTime) {
        if (!"v2".equals(contract.termsVersion)) return;
        invoices.findFirstByContractIdAndInvoiceType(contract.contractId, "REMAINING_RENT").ifPresent(invoice -> {
            invoice.dueDate = appointmentTime.toLocalDate().atStartOfDay();
            invoices.save(invoice);
        });
    }
    @Transactional(readOnly = true)
    public List<DailyTask> daily(LocalDate date, String taskType, String status, UUID staffId) {
        return daily(date, taskType, status, staffId, null);
    }
    @Transactional(readOnly = true)
    public List<DailyTask> daily(LocalDate date, String taskType, String status, UUID staffId, UUID facilityId) {
        String role = access.role();
        if (!Set.of("FACILITY_STAFF", "FACILITY_MANAGER").contains(role)) throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem công việc cơ sở.");
        if (taskType != null && !Set.of("CHECK_IN", "RETURN").contains(taskType)) throw new BusinessException(HttpStatus.BAD_REQUEST, "Loại công việc không hợp lệ.");
        if (status != null && !Set.of("SCHEDULED", "COMPLETED", "CANCELLED").contains(status)) throw new BusinessException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ.");
        if ("FACILITY_STAFF".equals(role) && staffId != null && !staffId.equals(access.currentId()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn chỉ được xem công việc được giao cho mình.");
        if ("RETURN".equals(taskType)) return List.of(); // UC16 supplies this task type later.
        List<UUID> allowed = access.facilities();
        if (facilityId != null) {
            if (!allowed.contains(facilityId))
                throw new BusinessException(HttpStatus.FORBIDDEN, "Cơ sở không thuộc phạm vi được phân công.");
            allowed = List.of(facilityId);
        }
        if (allowed.isEmpty()) return List.of();
        if (staffId != null && "FACILITY_MANAGER".equals(role) &&
                Collections.disjoint(allowed, assignments.findActiveFacilityIds(staffId, access.today())))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Nhân viên không thuộc cơ sở của bạn.");
        UUID filterStaff = "FACILITY_STAFF".equals(role) ? access.currentId() : staffId;
        LocalDate day = date == null ? access.today() : date;
        List<PickupAppointment> rows = appointments.daily(allowed, day.atStartOfDay(), day.plusDays(1).atStartOfDay(), filterStaff, status);
        return tasks(rows);
    }
    @Transactional(readOnly = true)
    public List<DailyTask> awaitingAssignment(UUID facilityId) {
        if (!"FACILITY_MANAGER".equals(access.role()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ quản lý cơ sở được xem lịch chờ phân công.");
        List<UUID> allowed = access.facilities();
        if (facilityId != null) {
            if (!allowed.contains(facilityId))
                throw new BusinessException(HttpStatus.FORBIDDEN, "Cơ sở không thuộc phạm vi được phân công.");
            allowed = List.of(facilityId);
        }
        if (allowed.isEmpty()) return List.of();
        return tasks(appointments.findByFacilityIdInAndStatusAndAssignedStaffIdIsNullAndAppointmentTimeGreaterThanEqualOrderByAppointmentTimeAsc(
                allowed, "SCHEDULED", access.now()));
    }
    @Transactional(readOnly = true)
    public List<DailyTask> upcomingMine() {
        if (!"FACILITY_STAFF".equals(access.role()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ nhân viên được xem lịch của mình.");
        List<UUID> allowed = access.facilities();
        if (allowed.isEmpty()) return List.of();
        return tasks(appointments.findTop20ByFacilityIdInAndAssignedStaffIdAndStatusAndAppointmentTimeGreaterThanEqualOrderByAppointmentTimeAsc(
                allowed, access.currentId(), "SCHEDULED", access.now()));
    }
    private List<DailyTask> tasks(List<PickupAppointment> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, RentalContract> contractMap = new HashMap<>();
        contracts.findAllById(rows.stream().map(a -> a.contractId).distinct().toList()).forEach(c -> contractMap.put(c.contractId, c));
        Map<UUID, User> userMap = new HashMap<>();
        Set<UUID> userIds = new HashSet<>();
        rows.forEach(a -> { userIds.add(a.customerId); if (a.assignedStaffId != null) userIds.add(a.assignedStaffId); });
        users.findAllById(userIds).forEach(u -> userMap.put(u.getUserId(), u));
        Map<UUID, StorageUnit> unitMap = new HashMap<>();
        units.findAllById(contractMap.values().stream().map(c -> c.unitId).filter(Objects::nonNull).distinct().toList())
                .forEach(u -> unitMap.put(u.getUnitId(), u));
        Map<UUID, Facility> facilityMap = new HashMap<>();
        facilities.findAllById(rows.stream().map(a -> a.facilityId).distinct().toList())
                .forEach(f -> facilityMap.put(f.getFacilityId(), f));
        Map<UUID, PaymentService.Balance> balances = payments.balances(contractMap.values());
        return rows.stream().map(a -> {
            RentalContract r = contractMap.get(a.contractId);
            if (r == null || !Objects.equals(a.facilityId, Optional.ofNullable(unitMap.get(r.unitId)).map(StorageUnit::getFacilityId).orElse(null)))
                throw new BusinessException(HttpStatus.CONFLICT, "Dữ liệu lịch nhận kho không khớp hợp đồng.");
            PaymentService.Balance balance = balances.getOrDefault(r.contractId,
                    new PaymentService.Balance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));
            return new DailyTask("CHECK_IN", a.contractId, a.facilityId, r.contractNumber,
                    Optional.ofNullable(userMap.get(a.customerId)).map(User::getFullName).orElse(""),
                    Optional.ofNullable(unitMap.get(r.unitId)).map(StorageUnit::getUnitNumber).orElse(null),
                    a.appointmentTime, a.assignedStaffId,
                    Optional.ofNullable(userMap.get(a.assignedStaffId)).map(User::getFullName).orElse(null),
                    Optional.ofNullable(facilityMap.get(a.facilityId)).map(Facility::getName).orElse(""),
                    balance.readyForHandover(), a.status, balance.depositPaidAmount(),
                    balance.depositRemainingAmount(), balance.rentRemainingAmount());
        }).toList();
    }
    @Transactional
    public PickupAppointment assign(UUID id, UUID staffId) {
        if (!"FACILITY_MANAGER".equals(access.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ quản lý cơ sở được phân công nhân viên.");
        if (staffId == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Vui lòng chọn nhân viên.");
        RentalContract r = access.contract(id, true); access.confirmed(r);
        PickupAppointment a = appointments.findFirstByContractIdAndStatus(id, "SCHEDULED")
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Không có lịch nhận kho đang hoạt động."));
        UUID facilityId = access.facilityId(r);
        if (!facilityId.equals(a.facilityId) || !r.customerId.equals(a.customerId))
            throw new BusinessException(HttpStatus.CONFLICT, "Lịch nhận kho không khớp hợp đồng.");
        if (!access.now().isBefore(a.appointmentTime)) throw new BusinessException(HttpStatus.CONFLICT, "Đã đến giờ nhận kho, không thể phân công.");
        User staff = users.findById(staffId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên."));
        if (!"ACTIVE".equals(staff.getStatus()) || !"FACILITY_STAFF".equals(staff.getRole().getRoleName())
                || !assignments.findActiveFacilityIds(staffId, access.today()).contains(facilityId))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Nhân viên không thuộc cơ sở này.");
        a.assignedStaffId = staffId; a.updatedAt = access.now(); return appointments.save(a);
    }
    @Transactional(readOnly = true)
    public List<StaffOption> staffOptions() {
        return staffOptions(null);
    }
    @Transactional(readOnly = true)
    public List<StaffOption> staffOptions(UUID facilityId) {
        if (!"FACILITY_MANAGER".equals(access.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ quản lý cơ sở được xem nhân viên.");
        List<UUID> facilities = access.facilities();
        if (facilityId != null) {
            if (!facilities.contains(facilityId))
                throw new BusinessException(HttpStatus.FORBIDDEN, "Cơ sở không thuộc phạm vi được phân công.");
            facilities = List.of(facilityId);
        }
        if (facilities.isEmpty()) return List.of();
        List<UUID> eligibleStaffIds = assignments.findActiveUserIds(facilities, access.today()).stream().distinct().toList();
        return users.findAllById(eligibleStaffIds).stream()
                .filter(u -> "ACTIVE".equals(u.getStatus()) && "FACILITY_STAFF".equals(u.getRole().getRoleName()))
                .map(u -> new StaffOption(u.getUserId(), u.getFullName())).distinct().toList();
    }
    /** Called by UC14 only after the storage handover has committed successfully. */
    @Transactional
    public void completeAppointmentAfterHandover(UUID contractId) {
        if (!payments.summary(contractId).readyForHandover())
            throw new BusinessException(HttpStatus.CONFLICT, "Chưa thu đủ tiền cọc và tiền thuê ban đầu để bàn giao kho.");
        appointments.findFirstByContractIdAndStatus(contractId, "SCHEDULED").ifPresent(a -> {
            a.status = "COMPLETED"; a.updatedAt = access.now(); appointments.save(a);
        });
    }
}
