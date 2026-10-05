package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.CreateReservationRequest;
import com.safebox.self_storage.dto.response.ReservationResponse;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.ReservationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Lớp cài đặt (Implementation) các nghiệp vụ đặt kho:
 * - UC05: Tạo yêu cầu đặt kho
 */
@Service
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;
    private final StorageUnitTypeRepository storageUnitTypeRepository;
    private final StorageUnitRepository storageUnitRepository;
    private final PricingPolicyRepository pricingPolicyRepository;
    private final RentalPolicyRepository rentalPolicyRepository;

    public ReservationServiceImpl(
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            FacilityRepository facilityRepository,
            StorageUnitTypeRepository storageUnitTypeRepository,
            StorageUnitRepository storageUnitRepository,
            PricingPolicyRepository pricingPolicyRepository,
            RentalPolicyRepository rentalPolicyRepository) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.facilityRepository = facilityRepository;
        this.storageUnitTypeRepository = storageUnitTypeRepository;
        this.storageUnitRepository = storageUnitRepository;
        this.pricingPolicyRepository = pricingPolicyRepository;
        this.rentalPolicyRepository = rentalPolicyRepository;
    }

    // =========================================================================
    // UC05: Tạo yêu cầu đặt kho (Create Storage Reservation Request)
    // =========================================================================

    /**
     * Triển khai nghiệp vụ UC05: Tạo yêu cầu đặt kho cho khách hàng.
     * Quy trình xử lý gồm 8 bước:
     * 1. Xác thực tài khoản khách hàng: phải tồn tại, đang ACTIVE và đã xác thực email.
     * 2. Xác thực cơ sở (Facility): phải tồn tại và đang ACTIVE.
     * 3. Xác thực loại kho (StorageUnitType): phải tồn tại và đang ACTIVE.
     * 4. Xác thực thời gian thuê: ngày bắt đầu không ở quá khứ, tính toán ngày kết thúc và số tháng thuê.
     * 5. Kiểm tra tình trạng kho: đảm bảo cơ sở còn ít nhất một ô kho trống khả dụng của loại kho này.
     * 6. Tra cứu bảng giá hiện hành: lấy chính sách giá ACTIVE tại thời điểm bắt đầu thuê, tính tiền thuê và tiền cọc.
     * 7. Xác định thời hạn giữ chỗ: lấy cấu hình số phút giữ kho từ RentalPolicy (hoặc mặc định 24 giờ).
     * 8. Lưu đối tượng Reservation với trạng thái ban đầu là PENDING (Chờ xác nhận).
     */
    @Override
    @Transactional
    public ReservationResponse createReservation(UUID customerId, CreateReservationRequest request) {
        if (customerId == null) {
            throw new IllegalArgumentException("Khách hàng không hợp lệ");
        }
        if (request == null) {
            throw new IllegalArgumentException("Thông tin yêu cầu đặt kho không được để trống");
        }

        // Bước 1: Kiểm tra tài khoản khách hàng
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy thông tin khách hàng"));
        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            throw new IllegalStateException("Tài khoản khách hàng đang bị khóa hoặc không hoạt động");
        }
        if (!customer.isEmailVerified()) {
            throw new IllegalStateException("Vui lòng xác thực email trước khi thực hiện đặt kho");
        }

        // Bước 2: Kiểm tra tính hợp lệ của cơ sở
        Facility facility = facilityRepository.findById(request.facilityId())
                .orElseThrow(() -> new NoSuchElementException("Cơ sở không tồn tại"));
        if (!"ACTIVE".equalsIgnoreCase(facility.getStatus())) {
            throw new IllegalStateException("Cơ sở hiện không hoạt động");
        }

        // Bước 3: Kiểm tra tính hợp lệ của loại kho
        StorageUnitType unitType = storageUnitTypeRepository.findById(request.typeId())
                .orElseThrow(() -> new NoSuchElementException("Loại kho không tồn tại"));
        if (!"ACTIVE".equalsIgnoreCase(unitType.getStatus())) {
            throw new IllegalStateException("Loại kho hiện không hoạt động");
        }

        // Bước 4: Kiểm tra ngày bắt đầu thuê và tính ngày kết thúc
        LocalDate startDate = request.startDate();
        if (startDate == null) {
            throw new IllegalArgumentException("Ngày bắt đầu thuê không được để trống");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày bắt đầu thuê không được trong quá khứ");
        }

        int months = (request.months() != null && request.months() > 0) ? request.months() : 1;
        LocalDate endDate = (request.endDate() != null) ? request.endDate() : startDate.plusMonths(months);

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Ngày kết thúc thuê phải lớn hơn hoặc bằng ngày bắt đầu");
        }
        if (request.endDate() != null && request.months() == null) {
            months = (int) ChronoUnit.MONTHS.between(startDate, endDate);
            if (months <= 0) months = 1;
        }

        // Bước 5: Kiểm tra số lượng ô kho còn trống (StorageUnit với trạng thái AVAILABLE)
        long availableUnits = storageUnitRepository.countAvailableUnitsByFacilityAndType(request.facilityId(), request.typeId());
        if (availableUnits <= 0) {
            throw new IllegalStateException("Hiện không còn ô kho trống cho loại kho này tại cơ sở đã chọn");
        }

        // Bước 6: Tra cứu bảng giá có hiệu lực tại ngày thuê
        List<PricingPolicy> policies = pricingPolicyRepository.findByFacilityIdAndStatus(request.facilityId(), "ACTIVE");
        PricingPolicy policy = CurrentPricing.forType(policies, request.typeId(), startDate)
                .orElseThrow(() -> new NoSuchElementException("Cơ sở chưa có bảng giá đang hiệu lực cho loại kho này"));

        BigDecimal monthlyPrice = policy.getMonthlyPrice();
        BigDecimal estimatedRentalAmount = monthlyPrice.multiply(BigDecimal.valueOf(months));
        BigDecimal estimatedDepositAmount = policy.getDepositAmount() != null ? policy.getDepositAmount() : BigDecimal.ZERO;

        // Bước 7: Xác định thời hạn giữ chỗ (hold_expires_at)
        int holdMinutes = rentalPolicyRepository
                .findFirstByFacilityIdAndStatusOrderByEffectiveFromDesc(request.facilityId(), "ACTIVE")
                .map(RentalPolicy::getReservationHoldMinutes)
                .orElse(1440); // Mặc định giữ chỗ trong 24 giờ (1440 phút)
        LocalDateTime holdExpiresAt = LocalDateTime.now().plusMinutes(holdMinutes);

        // Bước 8: Khởi tạo và lưu thực thể Reservation
        Reservation reservation = new Reservation();
        reservation.setCustomerId(customerId);
        reservation.setFacilityId(request.facilityId());
        reservation.setTypeId(request.typeId());
        reservation.setPricingId(policy.getPricingId());
        reservation.setStartDate(startDate);
        reservation.setEndDate(endDate);
        reservation.setHoldExpiresAt(holdExpiresAt);
        reservation.setEstimatedRentalAmount(estimatedRentalAmount);
        reservation.setEstimatedDepositAmount(estimatedDepositAmount);
        reservation.setStatus("PENDING");
        reservation.setCreatedAt(LocalDateTime.now());

        Reservation saved = reservationRepository.save(reservation);

        return toResponse(saved, customer, facility, unitType, policy);
    }

    // =========================================================================
    // Các hàm phụ trợ (Helper Methods)
    // =========================================================================

    /**
     * Hàm phụ trợ: Ánh xạ đầy đủ các thuộc tính của Reservation và các thực thể liên quan
     * vào DTO ReservationResponse để trả về client.
     */
    private ReservationResponse toResponse(Reservation res, User customer, Facility facility, StorageUnitType unitType, PricingPolicy policy) {
        String requestCode = ReservationResponse.formatRequestCode(res.getReservationId(), res.getCreatedAt());
        String statusLabel = ReservationResponse.getStatusLabel(res.getStatus());

        int months = (int) ChronoUnit.MONTHS.between(res.getStartDate(), res.getEndDate());
        if (months <= 0) months = 1;

        BigDecimal area = null;
        if (unitType != null && unitType.getWidth() != null && unitType.getLength() != null) {
            area = unitType.getWidth().multiply(unitType.getLength());
        }

        BigDecimal monthlyPrice = (policy != null) ? policy.getMonthlyPrice() : null;
        if (monthlyPrice == null && months > 0 && res.getEstimatedRentalAmount() != null) {
            monthlyPrice = res.getEstimatedRentalAmount().divide(BigDecimal.valueOf(months), 2, java.math.RoundingMode.HALF_UP);
        }

        return new ReservationResponse(
                res.getReservationId(),
                requestCode,
                res.getCustomerId(),
                customer != null ? customer.getFullName() : null,
                customer != null ? customer.getEmail() : null,
                customer != null ? customer.getPhone() : null,
                res.getFacilityId(),
                facility != null ? facility.getName() : null,
                facility != null ? facility.getAddress() : null,
                facility != null ? facility.getPhone() : null,
                res.getTypeId(),
                unitType != null ? unitType.getTypeName() : null,
                unitType != null ? unitType.getStorageMode() : null,
                unitType != null ? unitType.getSizeName() : null,
                area,
                unitType != null ? unitType.getImagePath() : null,
                res.getPricingId(),
                monthlyPrice,
                months,
                res.getStartDate(),
                res.getEndDate(),
                res.getHoldExpiresAt(),
                res.getEstimatedRentalAmount(),
                res.getEstimatedDepositAmount(),
                res.getStatus(),
                statusLabel,
                res.getCreatedAt(),
                res.getCancelledAt(),
                res.getCancellationReason()
        );
    }
}
