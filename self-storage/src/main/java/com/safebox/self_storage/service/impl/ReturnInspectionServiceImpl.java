package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.CompleteReturnInspectionRequest;
import com.safebox.self_storage.dto.CreateReturnScheduleRequest;
import com.safebox.self_storage.dto.response.FacilityReturnTaskItemResponse;
import com.safebox.self_storage.dto.response.ReturnInspectionDetailResponse;
import com.safebox.self_storage.dto.response.ReturnScheduleResponse;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.ReturnInspectionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ReturnInspectionServiceImpl implements ReturnInspectionService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final RentalContractRepository rentalContractRepository;
    private final StorageUnitRepository storageUnitRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final HandoverRecordRepository handoverRecordRepository;
    private final InspectionReportRepository inspectionReportRepository;
    private final UserFacilityAssignmentRepository userFacilityAssignmentRepository;
    private final InvoiceRepository invoiceRepository;

    public ReturnInspectionServiceImpl(
            RentalContractRepository rentalContractRepository,
            StorageUnitRepository storageUnitRepository,
            FacilityRepository facilityRepository,
            UserRepository userRepository,
            HandoverRecordRepository handoverRecordRepository,
            InspectionReportRepository inspectionReportRepository,
            UserFacilityAssignmentRepository userFacilityAssignmentRepository,
            InvoiceRepository invoiceRepository) {
        this.rentalContractRepository = rentalContractRepository;
        this.storageUnitRepository = storageUnitRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.handoverRecordRepository = handoverRecordRepository;
        this.inspectionReportRepository = inspectionReportRepository;
        this.userFacilityAssignmentRepository = userFacilityAssignmentRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    @Transactional
    public ReturnScheduleResponse scheduleReturnAppointment(
            UUID customerId, UUID contractId, CreateReturnScheduleRequest request) {
        if (customerId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (contractId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã hợp đồng không được để trống");
        }
        if (request == null || request.getScheduledAt() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Thời gian hẹn trả kho không được để trống");
        }

        // BR16.1: CUSTOMER chỉ đặt lịch trả cho hợp đồng ACTIVE của mình
        RentalContract contract = rentalContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê"));

        if (!contract.getCustomerId().equals(customerId)) {
            User currentUser = userRepository.findById(customerId).orElse(null);
            boolean isAdmin = currentUser != null && currentUser.getRole() != null
                    && "SYSTEM_ADMIN".equalsIgnoreCase(currentUser.getRole().getRoleName());
            if (!isAdmin) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền đặt lịch trả cho hợp đồng này");
            }
        }

        if (!"ACTIVE".equalsIgnoreCase(contract.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Chỉ hợp đồng đang hoạt động (ACTIVE) mới được đặt lịch trả kho. Trạng thái hiện tại: " + contract.getStatus());
        }

        // BR16.2: Mỗi hợp đồng chỉ có một lịch trả còn hiệu lực
        Optional<HandoverRecord> existingScheduled = handoverRecordRepository
                .findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED");
        if (existingScheduled.isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Hợp đồng này đã có một lịch trả kho đang chờ thực hiện vào lúc "
                            + existingScheduled.get().getScheduledAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        }

        LocalDateTime scheduledAt = request.getScheduledAt();
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);

        // BR16.2: Giờ hẹn không được ở quá khứ
        if (scheduledAt.isBefore(now)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Giờ hẹn trả kho không được ở trong quá khứ");
        }

        // BR16.1, BR16.2: Sprint 2 xử lý trả đúng hạn vào ngày kết thúc hợp đồng
        LocalDate scheduledDate = scheduledAt.toLocalDate();
        if (!scheduledDate.equals(contract.getEndDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn ngày hẹn trả kho vào ngày kết thúc hợp đồng ("
                            + contract.getEndDate().format(DATE_FORMATTER)
                            + ").");
        }

        // Tìm thông tin ô kho và cơ sở
        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin ô kho"));
        Facility facility = facilityRepository.findById(unit.getFacilityId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin cơ sở"));

        // BR16.2: Trong khung giờ tiếp nhận của cơ sở
        LocalTime apptTime = scheduledAt.toLocalTime();
        if (facility.getOpeningTime() != null && apptTime.isBefore(facility.getOpeningTime())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Giờ hẹn (" + apptTime.format(TIME_FORMATTER) + ") sớm hơn giờ mở cửa của cơ sở ("
                            + facility.getOpeningTime().format(TIME_FORMATTER) + ")");
        }
        if (facility.getClosingTime() != null && apptTime.isAfter(facility.getClosingTime())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Giờ hẹn (" + apptTime.format(TIME_FORMATTER) + ") muộn hơn giờ đóng cửa của cơ sở ("
                            + facility.getClosingTime().format(TIME_FORMATTER) + ")");
        }

        // Phân công nhân viên phụ trách tại cơ sở (BR16.3, BR13.2)
        UUID assignedStaffId = resolveStaffForFacility(facility.getFacilityId());

        // Tạo biên bản lịch trả kho (HANDOVER_RECORDS)
        HandoverRecord handoverRecord = new HandoverRecord();
        handoverRecord.setContractId(contract.getContractId());
        handoverRecord.setUnitId(unit.getUnitId());
        handoverRecord.setStaffId(assignedStaffId);
        handoverRecord.setHandoverType("RETURN");
        handoverRecord.setScheduledAt(scheduledAt);
        handoverRecord.setNote(request.getNote());
        handoverRecord.setStatus("SCHEDULED");

        handoverRecord = handoverRecordRepository.save(handoverRecord);

        return mapToScheduleResponse(handoverRecord, contract, unit, facility);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnScheduleResponse getReturnSchedule(UUID currentUserId, UUID contractId) {
        if (currentUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (contractId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã hợp đồng không được để trống");
        }

        RentalContract contract = rentalContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê"));

        // Kiểm tra quyền
        validateAccessToContract(currentUserId, contract);

        List<HandoverRecord> returns = handoverRecordRepository
                .findByContractIdAndHandoverTypeOrderByScheduledAtDesc(contractId, "RETURN");
        if (returns.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Hợp đồng chưa có lịch hẹn trả kho nào");
        }

        HandoverRecord latest = returns.get(0);
        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);
        Facility facility = (unit != null) ? facilityRepository.findById(unit.getFacilityId()).orElse(null) : null;

        return mapToScheduleResponse(latest, contract, unit, facility);
    }

    @Override
    @Transactional
    public ReturnScheduleResponse cancelReturnAppointment(UUID currentUserId, UUID contractId) {
        if (currentUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (contractId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã hợp đồng không được để trống");
        }

        RentalContract contract = rentalContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê"));

        validateAccessToContract(currentUserId, contract);

        HandoverRecord scheduledRecord = handoverRecordRepository
                .findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn trả kho đang chờ để hủy"));

        scheduledRecord.setStatus("CANCELLED");
        scheduledRecord = handoverRecordRepository.save(scheduledRecord);

        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);
        Facility facility = (unit != null) ? facilityRepository.findById(unit.getFacilityId()).orElse(null) : null;

        return mapToScheduleResponse(scheduledRecord, contract, unit, facility);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacilityReturnTaskItemResponse> getFacilityReturnAppointments(
            UUID currentUserId, UUID facilityId, String status) {
        if (currentUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Người dùng không tồn tại"));

        String role = (currentUser.getRole() != null) ? currentUser.getRole().getRoleName() : "";
        if (!Set.of("SYSTEM_ADMIN", "FACILITY_MANAGER", "FACILITY_STAFF").contains(role))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem lịch trả kho tại cơ sở");
        LocalDate today = LocalDate.now(VIETNAM_ZONE);

        List<UUID> assignedFacilityIds = userFacilityAssignmentRepository.findActiveFacilityIds(currentUserId, today);

        String filterStatus = (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status))
                ? status.trim().toUpperCase() : null;

        List<HandoverRecord> records;
        if ("SYSTEM_ADMIN".equalsIgnoreCase(role)) {
            if (facilityId != null) {
                records = handoverRecordRepository.findReturnHandoverRecords(facilityId, filterStatus);
            } else {
                records = handoverRecordRepository.findReturnHandoverRecords(null, filterStatus);
            }
        } else if ("FACILITY_STAFF".equals(role)) {
            if (facilityId != null && !assignedFacilityIds.contains(facilityId))
                throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền quản lý tại cơ sở này");
            records = assignedFacilityIds.isEmpty() ? List.of()
                    : facilityId != null ? handoverRecordRepository.findReturnHandoverRecordsForStaff(facilityId, filterStatus, currentUserId)
                    : handoverRecordRepository.findReturnHandoverRecordsForStaffInFacilities(assignedFacilityIds, filterStatus, currentUserId);
        } else {
            if (facilityId != null) {
                if (!assignedFacilityIds.contains(facilityId)) {
                    throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền quản lý tại cơ sở này");
                }
                records = handoverRecordRepository.findReturnHandoverRecords(facilityId, filterStatus);
            } else if (!assignedFacilityIds.isEmpty()) {
                // Hiển thị toàn bộ lịch hẹn thuộc tất cả cơ sở mà nhân viên/quản lý được phân công
                records = handoverRecordRepository.findReturnHandoverRecordsByFacilityIds(assignedFacilityIds, filterStatus);
            } else {
                records = List.of();
            }
        }

        return records.stream()
                .map(ho -> mapToTaskItemResponse(ho, currentUserId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnInspectionDetailResponse getReturnInspectionDetail(UUID currentUserId, UUID handoverId) {
        if (currentUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (handoverId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã biên bản không được để trống");
        }

        HandoverRecord handoverRecord = handoverRecordRepository.findById(handoverId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy biên bản bàn giao"));

        RentalContract contract = rentalContractRepository.findByContractId(handoverRecord.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng"));

        validateAccessToContract(currentUserId, contract);
        User viewer = userRepository.findById(currentUserId).orElse(null);
        if (viewer != null && viewer.getRole() != null
                && "FACILITY_STAFF".equals(viewer.getRole().getRoleName())
                && !currentUserId.equals(handoverRecord.getStaffId()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn chưa được phân công lịch trả kho này");

        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);
        Facility facility = (unit != null) ? facilityRepository.findById(unit.getFacilityId()).orElse(null) : null;
        User customer = userRepository.findById(contract.getCustomerId()).orElse(null);
        User inspector = userRepository.findById(handoverRecord.getStaffId()).orElse(null);

        InspectionReport report = inspectionReportRepository
                .findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(contract.getContractId(), "RETURN_INSPECTION")
                .orElse(null);

        return buildDetailResponse(handoverRecord, report, contract, unit, facility, customer, inspector);
    }

    @Override
    @Transactional
    public ReturnInspectionDetailResponse completeReturnHandoverAndInspection(
            UUID staffOrManagerId, CompleteReturnInspectionRequest request) {
        if (staffOrManagerId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (request == null || request.getHandoverId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã biên bản bàn giao không được để trống");
        }

        HandoverRecord handoverRecord = handoverRecordRepository.findById(request.getHandoverId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn bàn giao"));

        if (!"RETURN".equals(handoverRecord.getHandoverType()))
            throw new BusinessException(HttpStatus.CONFLICT, "Biên bản không phải lịch trả kho");

        RentalContract contract = rentalContractRepository.findByContractId(handoverRecord.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng"));

        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy ô kho"));

        Facility facility = facilityRepository.findById(unit.getFacilityId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở"));

        User staffUser = userRepository.findById(staffOrManagerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Tài khoản nhân viên không tồn tại"));

        // BR16.4: Chỉ STAFF được giao kiểm tra hoặc FACILITY_MANAGER tại đúng cơ sở được lập biên bản nhận lại
        validateStaffOrManagerPermission(staffUser, facility.getFacilityId());
        if ("FACILITY_STAFF".equals(staffUser.getRole().getRoleName())
                && !staffOrManagerId.equals(handoverRecord.getStaffId()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn chưa được phân công lịch trả kho này");

        // BR16.8: Idempotency - Gọi lặp lại không tạo biên bản mới
        if ("COMPLETED".equalsIgnoreCase(handoverRecord.getStatus())) {
            InspectionReport existingReport = inspectionReportRepository
                    .findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(contract.getContractId(), "RETURN_INSPECTION")
                    .orElse(null);
            User customer = userRepository.findById(contract.getCustomerId()).orElse(null);
            return buildDetailResponse(handoverRecord, existingReport, contract, unit, facility, customer, staffUser);
        }
        if (!"SCHEDULED".equalsIgnoreCase(handoverRecord.getStatus()) || !"ACTIVE".equals(contract.getStatus())
                || !"OCCUPIED".equals(unit.getStatus()))
            throw new BusinessException(HttpStatus.CONFLICT, "Lịch trả kho hoặc trạng thái kho không hợp lệ");

        // BR16.6: Chỉ hoàn tất nhận lại khi:
        // 1. Kho đã dọn trống (leftoverItems == false)
        if (request.isLeftoverItems()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Kho chưa được dọn trống, còn hàng hóa sót lại. Không thể hoàn tất nhận lại kho.");
        }

        // 2. Quyền truy cập cũ đã thu hồi (accessReturned == true)
        if (!request.isAccessReturned()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Chưa thu hồi đầy đủ khóa hoặc thẻ truy cập của ô kho. Không thể hoàn tất nhận lại kho.");
        }

        // 3. Biên bản có xác nhận của khách hàng (customerConfirmed == true)
        // BR16.3: Khách chưa tới thì không được xác nhận đã nhận lại kho
        if (!request.isCustomerConfirmed()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Khách hàng phải có mặt xác nhận biên bản trước khi hoàn tất nhận lại kho.");
        }

        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        LocalDate today = now.toLocalDate();

        // 1. Cập nhật HANDOVER_RECORDS
        handoverRecord.setStatus("COMPLETED");
        handoverRecord.setActualAt(now);
        handoverRecord.setStaffId(staffOrManagerId);
        handoverRecord.setAccessMethod(request.getAccessMethod() != null ? request.getAccessMethod() : "CARD_AND_KEY");
        handoverRecord.setAccessIdentifier(request.getAccessIdentifier() != null ? request.getAccessIdentifier() : "Khoá và thẻ đã thu hồi");
        handoverRecord.setCustomerSignature(request.getCustomerSignature() != null ? request.getCustomerSignature() : "Khách hàng xác nhận");
        handoverRecord.setStaffSignature(request.getStaffSignature() != null ? request.getStaffSignature() : staffUser.getFullName());
        handoverRecord.setNote(request.getNote());
        handoverRecord = handoverRecordRepository.save(handoverRecord);

        // 2. Tạo INSPECTION_REPORTS (BR16.5)
        InspectionReport report = new InspectionReport();
        report.setUnitId(unit.getUnitId());
        report.setContractId(contract.getContractId());
        report.setInspectedBy(staffOrManagerId);
        report.setInspectionType("RETURN_INSPECTION");
        report.setInspectionDate(now);

        String conditionStatus = request.getConditionStatus() != null ? request.getConditionStatus().toUpperCase() : "GOOD";
        report.setConditionStatus(conditionStatus);
        report.setDamageDescription(request.getDamageDescription());

        BigDecimal repairCost = (request.getEstimatedRepairCost() != null && request.getEstimatedRepairCost().compareTo(BigDecimal.ZERO) >= 0)
                ? request.getEstimatedRepairCost() : BigDecimal.ZERO;
        report.setEstimatedRepairCost(repairCost);

        report.setCustomerConfirmed(true);
        report.setTemperature(request.getTemperature());
        report.setHumidity(request.getHumidity());
        report.setNote(request.getNote());
        report = inspectionReportRepository.save(report);

        // BR16.7: Nếu kho đủ điều kiện sử dụng -> AVAILABLE. Nếu cần sửa chữa -> MAINTENANCE.
        boolean isDamaged = "DAMAGED".equalsIgnoreCase(conditionStatus)
                || repairCost.compareTo(BigDecimal.ZERO) > 0
                || "MAINTENANCE".equalsIgnoreCase(request.getUnitNextStatus());

        if (isDamaged) {
            unit.setStatus("MAINTENANCE");
        } else {
            unit.setStatus("AVAILABLE");
        }
        storageUnitRepository.save(unit);

        // BR16.8, BR16.9, BR16.10: Cập nhật hợp đồng sang RETURNED (tách biệt khỏi CLOSED)
        contract.setStatus("RETURNED");
        contract.setActualEndDate(today);
        rentalContractRepository.save(contract);

        User customer = userRepository.findById(contract.getCustomerId()).orElse(null);
        return buildDetailResponse(handoverRecord, report, contract, unit, facility, customer, staffUser);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private UUID resolveStaffForFacility(UUID facilityId) {
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        return userFacilityAssignmentRepository.findActiveUserIdsByFacility(facilityId, today).stream()
                .map(userRepository::findById).flatMap(Optional::stream)
                .filter(u -> "ACTIVE".equals(u.getStatus()) && u.getRole() != null
                        && "FACILITY_STAFF".equals(u.getRole().getRoleName()))
                .map(User::getUserId).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT,
                        "Cơ sở chưa có nhân viên đủ điều kiện xử lý lịch trả kho"));
    }

    private void validateAccessToContract(UUID currentUserId, RentalContract contract) {
        if (contract.getCustomerId().equals(currentUserId)) {
            return;
        }

        User user = userRepository.findById(currentUserId).orElse(null);
        if (user == null || user.getRole() == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập thông tin hợp đồng này");
        }

        String role = user.getRole().getRoleName();
        if ("SYSTEM_ADMIN".equalsIgnoreCase(role)) {
            return;
        }

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        List<UUID> assignedFacilityIds = userFacilityAssignmentRepository.findActiveFacilityIds(currentUserId, today);

        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);
        if (unit != null && assignedFacilityIds.contains(unit.getFacilityId())) {
            return;
        }

        throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập thông tin hợp đồng này");
    }

    private void validateStaffOrManagerPermission(User staffUser, UUID facilityId) {
        if (staffUser.getRole() == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Tài khoản chưa được phân quyền");
        }

        String role = staffUser.getRole().getRoleName();
        if ("SYSTEM_ADMIN".equalsIgnoreCase(role)) {
            return;
        }

        if (!"FACILITY_STAFF".equalsIgnoreCase(role) && !"FACILITY_MANAGER".equalsIgnoreCase(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ nhân viên hoặc quản lý cơ sở mới được thực hiện kiểm tra trả kho");
        }

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        List<UUID> assignedFacilities = userFacilityAssignmentRepository.findActiveFacilityIds(staffUser.getUserId(), today);
        if (!assignedFacilities.contains(facilityId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không được phân công làm việc tại cơ sở của hợp đồng này");
        }
    }

    private ReturnScheduleResponse mapToScheduleResponse(
            HandoverRecord record, RentalContract contract, StorageUnit unit, Facility facility) {
        User staff = (record.getStaffId() != null) ? userRepository.findById(record.getStaffId()).orElse(null) : null;

        boolean canReschedule = "SCHEDULED".equalsIgnoreCase(record.getStatus())
                && record.getScheduledAt().isAfter(LocalDateTime.now(VIETNAM_ZONE));

        return new ReturnScheduleResponse(
                record.getHandoverId(),
                contract.getContractId(),
                contract.getContractNumber(),
                (unit != null) ? unit.getUnitId() : null,
                (unit != null) ? unit.getUnitNumber() : "N/A",
                (unit != null) ? unit.getFloor() : null,
                (unit != null) ? unit.getZone() : null,
                (facility != null) ? facility.getFacilityId() : null,
                (facility != null) ? facility.getName() : "SafeBox Facility",
                (facility != null) ? facility.getAddress() : "",
                (facility != null) ? facility.getPhone() : "",
                (facility != null && facility.getOpeningTime() != null) ? facility.getOpeningTime().format(TIME_FORMATTER) : "07:00",
                (facility != null && facility.getClosingTime() != null) ? facility.getClosingTime().format(TIME_FORMATTER) : "21:00",
                record.getScheduledAt(),
                record.getStatus(),
                ReturnScheduleResponse.getStatusLabel(record.getStatus()),
                record.getStaffId(),
                (staff != null) ? staff.getFullName() : "Nhân viên trực quầy tiếp nhận",
                record.getNote(),
                contract.getEndDate(),
                canReschedule,
                record.getScheduledAt()
        );
    }

    private FacilityReturnTaskItemResponse mapToTaskItemResponse(HandoverRecord ho, UUID currentUserId) {
        RentalContract contract = rentalContractRepository.findByContractId(ho.getContractId()).orElse(null);
        StorageUnit unit = (contract != null) ? storageUnitRepository.findById(contract.getUnitId()).orElse(null) : null;
        Facility facility = (unit != null) ? facilityRepository.findById(unit.getFacilityId()).orElse(null) : null;
        User customer = (contract != null) ? userRepository.findById(contract.getCustomerId()).orElse(null) : null;
        User staff = (ho.getStaffId() != null) ? userRepository.findById(ho.getStaffId()).orElse(null) : null;

        boolean isAssignedToMe = currentUserId != null && currentUserId.equals(ho.getStaffId());

        return new FacilityReturnTaskItemResponse(
                ho.getHandoverId(),
                (contract != null) ? contract.getContractId() : null,
                (contract != null) ? contract.getContractNumber() : "N/A",
                (customer != null) ? customer.getFullName() : "Khách hàng",
                (customer != null) ? customer.getPhone() : "",
                (customer != null) ? customer.getEmail() : "",
                (unit != null) ? unit.getUnitId() : null,
                (unit != null) ? unit.getUnitNumber() : "N/A",
                (unit != null) ? unit.getFloor() : null,
                (unit != null) ? unit.getZone() : null,
                (facility != null) ? facility.getFacilityId() : null,
                (facility != null) ? facility.getName() : "SafeBox Facility",
                ho.getScheduledAt(),
                ho.getActualAt(),
                ho.getStatus(),
                FacilityReturnTaskItemResponse.getStatusLabel(ho.getStatus()),
                ho.getStaffId(),
                (staff != null) ? staff.getFullName() : "Chưa phân công",
                isAssignedToMe,
                ho.getNote()
        );
    }

    private ReturnInspectionDetailResponse buildDetailResponse(
            HandoverRecord handoverRecord,
            InspectionReport report,
            RentalContract contract,
            StorageUnit unit,
            Facility facility,
            User customer,
            User inspector) {

        long unpaidInvoices = invoiceRepository.findByContractId(contract.getContractId()).stream()
                .filter(inv -> !"PAID".equalsIgnoreCase(inv.getStatus()))
                .count();

        // BR16.9, BR16.10: Sprint 2 lưu khoản chưa tất toán để theo dõi, không tự ý hoàn cọc hay phạt
        String financialNotes = "Hợp đồng đã nhận lại kho vật lý (RETURNED). Tiền cọc ("
                + String.format("%,.0f", contract.getDepositAmount())
                + " VNĐ) và các chi phí phát sinh nếu có được lưu giữ để theo dõi; việc hoàn tiền cọc và thanh toán bù trừ sẽ được tất toán tại Sprint 3.";

        return new ReturnInspectionDetailResponse(
                handoverRecord.getHandoverId(),
                (report != null) ? report.getInspectionId() : null,
                contract.getContractId(),
                contract.getContractNumber(),
                contract.getCustomerId(),
                (customer != null) ? customer.getFullName() : "Khách hàng",
                (customer != null) ? customer.getPhone() : "",
                (customer != null) ? customer.getEmail() : "",
                (unit != null) ? unit.getUnitId() : null,
                (unit != null) ? unit.getUnitNumber() : "N/A",
                (unit != null) ? unit.getFloor() : null,
                (unit != null) ? unit.getZone() : null,
                (facility != null) ? facility.getFacilityId() : null,
                (facility != null) ? facility.getName() : "SafeBox Facility",
                (facility != null) ? facility.getAddress() : "",
                handoverRecord.getScheduledAt(),
                handoverRecord.getActualAt(),
                handoverRecord.getStatus(),
                contract.getStatus(),
                (unit != null) ? unit.getStatus() : "AVAILABLE",
                (report != null) ? report.getConditionStatus() : "GOOD",
                (report != null) ? report.getDamageDescription() : null,
                (report != null) ? report.getEstimatedRepairCost() : BigDecimal.ZERO,
                (report != null) ? report.getTemperature() : null,
                (report != null) ? report.getHumidity() : null,
                handoverRecord.getActualAt() != null,
                handoverRecord.getAccessMethod(),
                handoverRecord.getAccessIdentifier(),
                (report != null) && report.isCustomerConfirmed(),
                handoverRecord.getCustomerSignature(),
                handoverRecord.getStaffSignature(),
                handoverRecord.getStaffId(),
                (inspector != null) ? inspector.getFullName() : "Nhân viên kiểm tra",
                contract.getDepositAmount(),
                unpaidInvoices,
                false, // BR16.9: financialObligationsSettled is false in Sprint 2
                financialNotes,
                handoverRecord.getNote()
        );
    }
}
