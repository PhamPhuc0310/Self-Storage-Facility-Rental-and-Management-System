package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.contract.AssignableUnitResponse;
import com.safebox.self_storage.dto.contract.ConfirmContractRequest;
import com.safebox.self_storage.dto.contract.ContractResponse;
import com.safebox.self_storage.dto.contract.CreateContractRequest;
import com.safebox.self_storage.dto.response.ReservationResponse;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.ContractService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Lớp cài đặt (Implementation) các nghiệp vụ hợp đồng thuê kho:
 * - UC09: Gán kho vật lý và lập hợp đồng thuê (FACILITY_MANAGER)
 * - UC10: Xem và xác nhận hợp đồng thuê (CUSTOMER)
 */
@Service
public class ContractServiceImpl implements ContractService {

    private static final List<String> DEFAULT_TERMS = List.of(
            "1. Bên thuê cam kết sử dụng kho đúng mục đích lưu trữ hợp pháp, không lưu trữ hàng cấm, hóa chất nguy hiểm, vũ khí hay chất dễ cháy nổ.",
            "2. Khách hàng tuân thủ nội quy an toàn, phòng cháy chữa cháy và quy trình ra vào của cơ sở SafeBox.",
            "3. Kỳ thanh toán định kỳ hàng tháng theo giá thuê đã thỏa thuận. Tiền đặt cọc được giữ để bảo đảm thực hiện hợp đồng.",
            "4. Tiền đặt cọc sẽ được hoàn trả đầy đủ sau khi hợp đồng kết thúc và ô kho được bàn giao lại đúng hiện trạng ban đầu.",
            "5. Hợp đồng có hiệu lực sau khi được hoàn tất bàn giao thực tế tại cơ sở."
    );

    private final RentalContractRepository contracts;
    private final ReservationRepository reservations;
    private final StorageUnitRepository units;
    private final StorageUnitTypeRepository unitTypes;
    private final FacilityRepository facilities;
    private final UserRepository users;
    private final PricingPolicyRepository pricing;
    private final UserFacilityAssignmentRepository assignments;

    public ContractServiceImpl(
            RentalContractRepository contracts,
            ReservationRepository reservations,
            StorageUnitRepository units,
            StorageUnitTypeRepository unitTypes,
            FacilityRepository facilities,
            UserRepository users,
            PricingPolicyRepository pricing,
            UserFacilityAssignmentRepository assignments) {
        this.contracts = contracts;
        this.reservations = reservations;
        this.units = units;
        this.unitTypes = unitTypes;
        this.facilities = facilities;
        this.users = users;
        this.pricing = pricing;
        this.assignments = assignments;
    }

    // =========================================================================
    // UC09: Dành cho Quản lý cơ sở (FACILITY_MANAGER)
    // =========================================================================

    /**
     * BR09.9: Lấy danh sách kho vật lý khả dụng để Quản lý gán cho yêu cầu đặt kho.
     * Kho phải thuộc đúng cơ sở, loại kho, trạng thái AVAILABLE và không bị trùng lịch thuê.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AssignableUnitResponse> getAssignableUnits(UUID managerId, UUID reservationId) {
        if (managerId == null || reservationId == null) {
            throw new IllegalArgumentException("Thông tin yêu cầu không hợp lệ.");
        }

        Reservation res = reservations.findById(reservationId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt kho."));

        // BR09.1: Kiểm tra quyền quản lý cơ sở
        requireManagedFacility(managerId, res.getFacilityId());

        // BR09.1: Chỉ yêu cầu CONFIRMED mới được xử lý gán kho
        if (!"CONFIRMED".equalsIgnoreCase(res.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Chỉ có thể gán kho cho yêu cầu ở trạng thái CONFIRMED (Hiện tại: " + res.getStatus() + ").");
        }

        // Lấy tất cả ô kho thuộc cơ sở & loại kho có trạng thái AVAILABLE
        List<StorageUnit> availableUnits = units.findByFacilityIdAndTypeIdAndStatusOrderByUnitNumberAsc(
                res.getFacilityId(), res.getTypeId(), "AVAILABLE");

        if (availableUnits.isEmpty()) {
            return List.of();
        }

        // Lọc bỏ những ô kho có lịch thuê bị trùng lặp (BR09.2, BR09.4)
        List<UUID> candidateIds = availableUnits.stream().map(StorageUnit::getUnitId).toList();
        Set<UUID> overlappingIds = contracts.findOverlappingUnitIds(candidateIds, res.getStartDate(), res.getEndDate());

        return availableUnits.stream()
                .filter(u -> !overlappingIds.contains(u.getUnitId()))
                .map(u -> new AssignableUnitResponse(
                        u.getUnitId(),
                        u.getUnitNumber(),
                        u.getTypeId(),
                        u.getFloor(),
                        u.getZone(),
                        u.getStatus()
                ))
                .toList();
    }

    /**
     * BR09.1 - BR09.8: Gán kho vật lý và lập hợp đồng thuê trong cùng transaction.
     */
    @Override
    @Transactional
    public ContractResponse createContract(UUID managerId, UUID reservationId, CreateContractRequest request) {
        if (managerId == null || reservationId == null || request == null) {
            throw new IllegalArgumentException("Dữ liệu lập hợp đồng không hợp lệ.");
        }

        Reservation res = reservations.findById(reservationId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt kho."));

        // BR09.1: Chỉ FACILITY_MANAGER được lập hợp đồng cho yêu cầu tại cơ sở mình phụ trách
        Facility facility = requireManagedFacility(managerId, res.getFacilityId());

        // BR09.1: Chỉ yêu cầu có trạng thái CONFIRMED mới được lập hợp đồng
        if (!"CONFIRMED".equalsIgnoreCase(res.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Chỉ được lập hợp đồng cho yêu cầu đặt kho đã được xác nhận (CONFIRMED). Trạng thái hiện tại: " + res.getStatus());
        }

        // BR09.3: Mỗi yêu cầu chỉ có một hợp đồng chưa hủy. Gửi lại thao tác lập hợp đồng phải trả về hợp đồng đã tạo hoặc báo xung đột
        Optional<RentalContract> existingContractOpt = contracts.findActiveOrPendingContractByReservationId(reservationId);
        if (existingContractOpt.isPresent()) {
            RentalContract existing = existingContractOpt.get();
            // Nếu gửi lại đúng kho đó, trả về hợp đồng đã tạo; nếu khác kho thì báo xung đột không tạo bản sao
            if (existing.getUnitId().equals(request.unitId())) {
                return toResponse(existing);
            }
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Yêu cầu đặt kho này đã có hợp đồng thuê (" + existing.getContractNumber() + "). Không thể tạo hợp đồng mới.");
        }

        // BR09.2: Kho được gán phải tồn tại, thuộc đúng cơ sở, loại kho khách đặt, đang AVAILABLE
        StorageUnit unit = units.findById(request.unitId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy ô kho vật lý đã chọn."));

        if (!unit.getFacilityId().equals(res.getFacilityId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ô kho vật lý không thuộc cơ sở của yêu cầu thuê này.");
        }

        if (!unit.getTypeId().equals(res.getTypeId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ô kho vật lý không đúng loại kho khách hàng đã đặt.");
        }

        // Kho OCCUPIED, MAINTENANCE hoặc INACTIVE không được gán trong Sprint 2
        if (!"AVAILABLE".equalsIgnoreCase(unit.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Kho đang ở trạng thái " + unit.getStatus() + ". Kho OCCUPIED, MAINTENANCE hoặc INACTIVE không được gán.");
        }

        // BR09.4: Khoảng thuê dùng ngày bắt đầu < ngày kết thúc. Hai lịch trùng khi: bắt đầu mới < kết thúc cũ VÀ kết thúc mới > bắt đầu cũ.
        if (!res.getEndDate().isAfter(res.getStartDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ngày kết thúc thuê phải sau ngày bắt đầu.");
        }

        long overlappingCount = contracts.countOverlappingContractsForUnit(unit.getUnitId(), res.getStartDate(), res.getEndDate());
        if (overlappingCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Ô kho " + unit.getUnitNumber() + " đã có lịch thuê trùng lặp trong khoảng thời gian này ("
                            + res.getStartDate() + " đến " + res.getEndDate() + ").");
        }

        // BR09.6 & BR09.7: Tính toán tiền bằng BigDecimal từ dữ liệu đã chốt ở Sprint 1
        int months = (int) ChronoUnit.MONTHS.between(res.getStartDate(), res.getEndDate());
        if (months <= 0) months = 1;

        BigDecimal monthlyRentalFee = null;
        if (res.getEstimatedRentalAmount() != null && months > 0) {
            monthlyRentalFee = res.getEstimatedRentalAmount().divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        if (monthlyRentalFee == null) {
            PricingPolicy policy = pricing.findById(res.getPricingId()).orElse(null);
            monthlyRentalFee = (policy != null) ? policy.getMonthlyPrice() : BigDecimal.ZERO;
        }

        BigDecimal depositAmount = res.getEstimatedDepositAmount() != null
                ? res.getEstimatedDepositAmount()
                : BigDecimal.ZERO;

        // Sinh mã số hợp đồng duy nhất dạng HD-YYYYMMDD-XXXX
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String contractNumber;
        do {
            String randomSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            contractNumber = "HD-" + datePrefix + "-" + randomSuffix;
        } while (contracts.existsByContractNumber(contractNumber));

        String termsVersion = (request.termsVersion() != null && !request.termsVersion().isBlank())
                ? request.termsVersion().trim()
                : "TERMS-2026-V1";

        // BR09.5 & BR09.8: Tạo hợp đồng với trạng thái ban đầu là PENDING_CONFIRMATION
        // Gán kho CHƯA chuyển ô kho thành OCCUPIED. Chỉ bàn giao thành công mới chuyển sang đang sử dụng.
        RentalContract contract = new RentalContract();
        contract.setContractNumber(contractNumber);
        contract.setReservationId(res.getReservationId());
        contract.setCustomerId(res.getCustomerId());
        contract.setUnitId(unit.getUnitId());
        contract.setStartDate(res.getStartDate());
        contract.setEndDate(res.getEndDate());
        contract.setMonthlyRentalFee(monthlyRentalFee);
        contract.setDepositAmount(depositAmount);
        contract.setBillingCycle("MONTHLY");
        contract.setTermsVersion(termsVersion);
        contract.setApprovedBy(managerId);
        contract.setApprovedAt(LocalDateTime.now());
        contract.setCompanySignature("SafeBox Storage — " + facility.getName());
        contract.setStatus("PENDING_CONFIRMATION");
        contract.setCreatedAt(LocalDateTime.now());

        RentalContract saved = contracts.save(contract);
        return toResponse(saved, res, facility, unit);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractResponse getContractByReservationForManager(UUID managerId, UUID reservationId) {
        Reservation res = reservations.findById(reservationId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt kho."));
        requireManagedFacility(managerId, res.getFacilityId());

        RentalContract contract = contracts.findActiveOrPendingContractByReservationId(reservationId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Yêu cầu đặt kho này chưa có hợp đồng thuê."));

        return toResponse(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractResponse getContractDetailForManager(UUID managerId, UUID contractId) {
        RentalContract contract = contracts.findById(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng."));

        StorageUnit unit = units.findById(contract.getUnitId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy ô kho vật lý của hợp đồng."));

        requireManagedFacility(managerId, unit.getFacilityId());
        return toResponse(contract);
    }

    // =========================================================================
    // UC10: Dành cho Khách hàng (CUSTOMER)
    // =========================================================================

    /**
     * BR10.1: CUSTOMER xem danh sách hợp đồng của mình. customerId lấy từ JWT.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ContractResponse> getMyContracts(UUID customerId, String status) {
        if (customerId == null) {
            throw new IllegalArgumentException("Khách hàng không hợp lệ.");
        }

        List<RentalContract> list;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
            list = contracts.findByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, status.trim().toUpperCase());
        } else {
            list = contracts.findByCustomerIdOrderByCreatedAtDesc(customerId);
        }

        return list.stream().map(this::toResponse).toList();
    }

    /**
     * BR10.1 & BR10.2: Xem chi tiết hợp đồng của mình.
     * BR10.9: Truy cập hợp đồng người khác trả 403 Forbidden hoặc 404 Not Found.
     */
    @Override
    @Transactional(readOnly = true)
    public ContractResponse getMyContractDetail(UUID customerId, UUID contractId) {
        if (customerId == null || contractId == null) {
            throw new IllegalArgumentException("Thông tin tra cứu không hợp lệ.");
        }

        RentalContract contract = contracts.findById(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng."));

        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập hợp đồng này.");
        }

        return toResponse(contract);
    }

    /**
     * Xem hợp đồng theo mã đặt kho của khách hàng.
     */
    @Override
    @Transactional(readOnly = true)
    public ContractResponse getMyContractByReservation(UUID customerId, UUID reservationId) {
        if (customerId == null || reservationId == null) {
            throw new IllegalArgumentException("Thông tin tra cứu không hợp lệ.");
        }

        Reservation res = reservations.findByReservationIdAndCustomerId(reservationId, customerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt kho của bạn."));

        RentalContract contract = contracts.findActiveOrPendingContractByReservationId(res.getReservationId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Yêu cầu đặt kho này chưa có hợp đồng thuê."));

        return toResponse(contract);
    }

    /**
     * BR10.3 - BR10.8: Khách hàng xác nhận hợp đồng thuê trực tuyến.
     */
    @Override
    @Transactional
    public ContractResponse confirmContract(UUID customerId, UUID contractId, ConfirmContractRequest request) {
        if (customerId == null || contractId == null || request == null) {
            throw new IllegalArgumentException("Thông tin xác nhận hợp đồng không hợp lệ.");
        }

        RentalContract contract = contracts.findById(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng."));

        // BR10.1 & BR10.9: customerId lấy từ JWT, không lấy từ tham số FE
        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền xác nhận hợp đồng này.");
        }

        // BR10.3: Khách phải đồng ý điều khoản bằng một thao tác rõ ràng trên FE
        if (request.agreedToTerms() == null || !request.agreedToTerms()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Khách hàng phải đồng ý với các điều khoản thuê kho trước khi xác nhận hợp đồng.");
        }

        // BR10.3, BR10.8: Chỉ hợp đồng chờ xác nhận mới được xác nhận
        String currentStatus = contract.getStatus();
        if ("CANCELLED".equalsIgnoreCase(currentStatus) || "TERMINATED".equalsIgnoreCase(currentStatus) || "EXPIRED".equalsIgnoreCase(currentStatus)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Hợp đồng đã ở trạng thái " + currentStatus + ", không được phép xác nhận.");
        }

        if ("PENDING_HANDOVER".equalsIgnoreCase(currentStatus) || "ACTIVE".equalsIgnoreCase(currentStatus)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Hợp đồng này đã được xác nhận trước đó (Trạng thái: " + ContractResponse.getStatusLabel(currentStatus) + ").");
        }

        if (!"PENDING_CONFIRMATION".equalsIgnoreCase(currentStatus)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Hợp đồng không ở trạng thái chờ xác nhận (Trạng thái hiện tại: " + currentStatus + ").");
        }

        User customer = users.findById(customerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin khách hàng."));

        // BR10.4: BE lưu thời điểm xác nhận, ID khách và phiên bản điều khoản. Chữ ký điện tử trong hệ thống.
        contract.setCustomerSignedAt(LocalDateTime.now());
        String signature = (request.signature() != null && !request.signature().isBlank())
                ? request.signature().trim()
                : customer.getFullName() + " [Xác nhận trực tuyến qua SafeBox Online]";
        contract.setCustomerSignature(signature);

        // BR10.5: Sau khi xác nhận, hợp đồng chuyển sang chờ bàn giao. Hợp đồng chỉ chuyển sang ACTIVE khi UC14 hoàn thành.
        contract.setStatus("PENDING_HANDOVER");

        RentalContract updated = contracts.save(contract);
        return toResponse(updated);
    }

    // =========================================================================
    // Các hàm phụ trợ (Helper Methods)
    // =========================================================================

    private Facility requireManagedFacility(UUID managerId, UUID facilityId) {
        List<UUID> assignedFacilityIds = assignments.findActiveFacilityIds(managerId, LocalDate.now());
        if (!assignedFacilityIds.contains(facilityId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền quản lý cơ sở này.");
        }
        return facilities.findById(facilityId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở."));
    }

    private ContractResponse toResponse(RentalContract c) {
        Reservation res = reservations.findById(c.getReservationId()).orElse(null);
        StorageUnit unit = units.findById(c.getUnitId()).orElse(null);
        Facility facility = null;
        if (unit != null) {
            facility = facilities.findById(unit.getFacilityId()).orElse(null);
        } else if (res != null) {
            facility = facilities.findById(res.getFacilityId()).orElse(null);
        }
        return toResponse(c, res, facility, unit);
    }

    private ContractResponse toResponse(RentalContract c, Reservation res, Facility facility, StorageUnit unit) {
        User customer = users.findById(c.getCustomerId()).orElse(null);
        User manager = (c.getApprovedBy() != null) ? users.findById(c.getApprovedBy()).orElse(null) : null;

        Integer typeId = (unit != null) ? unit.getTypeId() : (res != null ? res.getTypeId() : null);
        StorageUnitType unitType = (typeId != null) ? unitTypes.findById(typeId).orElse(null) : null;

        String requestCode = (res != null)
                ? ReservationResponse.formatRequestCode(res.getReservationId(), res.getCreatedAt())
                : null;

        int months = (int) ChronoUnit.MONTHS.between(c.getStartDate(), c.getEndDate());
        if (months <= 0) months = 1;

        BigDecimal monthlyRentalFee = c.getMonthlyRentalFee();
        BigDecimal totalRentalAmount = monthlyRentalFee.multiply(BigDecimal.valueOf(months));
        BigDecimal depositAmount = c.getDepositAmount();
        BigDecimal totalPayable = totalRentalAmount.add(depositAmount);

        BigDecimal area = null;
        if (unitType != null && unitType.getWidth() != null && unitType.getLength() != null) {
            area = unitType.getWidth().multiply(unitType.getLength());
        }

        return new ContractResponse(
                c.getContractId(),
                c.getContractNumber(),
                c.getReservationId(),
                requestCode,
                c.getCustomerId(),
                customer != null ? customer.getFullName() : null,
                customer != null ? customer.getEmail() : null,
                customer != null ? customer.getPhone() : null,
                customer != null ? customer.getIdentityNumber() : null,
                customer != null ? customer.getAddress() : null,
                facility != null ? facility.getFacilityId() : null,
                facility != null ? facility.getName() : null,
                facility != null ? facility.getAddress() : null,
                facility != null ? facility.getPhone() : null,
                c.getUnitId(),
                unit != null ? unit.getUnitNumber() : null,
                unit != null ? unit.getFloor() : null,
                unit != null ? unit.getZone() : null,
                typeId,
                unitType != null ? unitType.getTypeName() : null,
                unitType != null ? unitType.getSizeName() : null,
                area,
                unitType != null ? unitType.getStorageMode() : null,
                c.getStartDate(),
                c.getEndDate(),
                months,
                monthlyRentalFee,
                totalRentalAmount,
                depositAmount,
                totalPayable,
                c.getBillingCycle(),
                c.getTermsVersion(),
                DEFAULT_TERMS,
                c.getCustomerSignedAt(),
                c.getCustomerSignature(),
                c.getApprovedBy(),
                manager != null ? manager.getFullName() : null,
                c.getApprovedAt(),
                c.getCompanySignature(),
                c.getStatus(),
                ContractResponse.getStatusLabel(c.getStatus()),
                c.getCreatedAt()
        );
    }
}
