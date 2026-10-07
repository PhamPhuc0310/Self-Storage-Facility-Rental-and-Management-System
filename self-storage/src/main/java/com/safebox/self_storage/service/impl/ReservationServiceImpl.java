package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.CancelReservationRequest;
import com.safebox.self_storage.dto.CreateReservationRequest;
import com.safebox.self_storage.dto.RejectReservationRequest;
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
 * - UC06: Xem danh sách, chi tiết và hủy yêu cầu của tôi
 * - UC08: Xem tất cả yêu cầu, duyệt hoặc từ chối yêu cầu đặt kho
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
    // UC06: Xem và hủy yêu cầu đặt kho của tôi (Customer)
    // =========================================================================

    /**
     * Triển khai nghiệp vụ UC06: Lấy danh sách yêu cầu đặt kho của khách hàng hiện tại.
     * Cho phép lọc theo trạng thái (PENDING, CONFIRMED, REJECTED, CANCELLED)
     * và tìm kiếm theo chuỗi ký tự (mã yêu cầu, tên kho,...).
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations(UUID customerId, String status, String search) {
        if (customerId == null) {
            throw new IllegalArgumentException("Khách hàng không hợp lệ");
        }

        List<Reservation> list;
        // Nếu có chỉ định trạng thái khác ALL thì lọc theo trạng thái
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
            list = reservationRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, status.trim().toUpperCase());
        } else {
            // Mặc định lấy toàn bộ sắp xếp theo thời gian tạo mới nhất
            list = reservationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        }

        return list.stream()
                .map(this::toResponse)
                .filter(res -> matchesSearch(res, search))
                .toList();
    }

    /**
     * Triển khai nghiệp vụ UC06: Xem chi tiết một yêu cầu đặt kho của khách hàng hiện tại.
     * Đảm bảo kiểm tra quyền sở hữu (yêu cầu phải thuộc customerId tương ứng).
     */
    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getMyReservationDetail(UUID customerId, UUID reservationId) {
        if (customerId == null || reservationId == null) {
            throw new IllegalArgumentException("Thông tin yêu cầu không hợp lệ");
        }
        Reservation reservation = reservationRepository.findByReservationIdAndCustomerId(reservationId, customerId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy yêu cầu đặt kho của bạn"));
        return toResponse(reservation);
    }

    /**
     * Triển khai nghiệp vụ UC06: Khách hàng tự hủy yêu cầu đặt kho của mình.
     * Quy tắc nghiệp vụ:
     * - Yêu cầu phải thuộc về khách hàng đang đăng nhập.
     * - Không thể hủy yêu cầu đã bị hủy trước đó (CANCELLED).
     * - Không thể hủy yêu cầu đã bị từ chối (REJECTED).
     * - Chỉ cho phép hủy khi đang ở trạng thái PENDING hoặc CONFIRMED (trước khi nhận phòng).
     * - Cập nhật trạng thái thành CANCELLED, lưu ngày giờ hủy và lý do hủy.
     */
    @Override
    @Transactional
    public ReservationResponse cancelMyReservation(UUID customerId, UUID reservationId, CancelReservationRequest request) {
        if (customerId == null || reservationId == null) {
            throw new IllegalArgumentException("Thông tin yêu cầu không hợp lệ");
        }
        Reservation reservation = reservationRepository.findByReservationIdAndCustomerId(reservationId, customerId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy yêu cầu đặt kho của bạn"));

        String currentStatus = reservation.getStatus();
        if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
            throw new IllegalStateException("Yêu cầu đặt kho này đã được hủy trước đó");
        }
        if ("REJECTED".equalsIgnoreCase(currentStatus)) {
            throw new IllegalStateException("Yêu cầu này đã bị từ chối, không thể hủy");
        }
        if (!"PENDING".equalsIgnoreCase(currentStatus) && !"CONFIRMED".equalsIgnoreCase(currentStatus)) {
            throw new IllegalStateException("Không thể hủy yêu cầu ở trạng thái: " + currentStatus);
        }

        reservation.setStatus("CANCELLED");
        reservation.setCancelledAt(LocalDateTime.now());
        String reason = (request != null && request.cancellationReason() != null && !request.cancellationReason().isBlank())
                ? request.cancellationReason().trim()
                : "Khách hàng yêu cầu hủy";
        reservation.setCancellationReason(reason);

        Reservation updated = reservationRepository.save(reservation);
        return toResponse(updated);
    }

    // =========================================================================
    // UC08: Xác nhận hoặc từ chối yêu cầu đặt kho (Manager / Staff)
    // =========================================================================

    /**
     * Triển khai nghiệp vụ UC08: Xem tất cả yêu cầu đặt kho dành cho cấp quản lý/nhân viên.
     * Hỗ trợ bộ lọc đa điều kiện: trạng thái, cơ sở kho và từ khóa tìm kiếm.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations(String status, UUID facilityId, String search) {
        String filterStatus = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim()))
                ? status.trim().toUpperCase()
                : null;

        List<Reservation> list = reservationRepository.searchReservations(null, filterStatus, facilityId);

        return list.stream()
                .map(this::toResponse)
                .filter(res -> matchesSearch(res, search))
                .toList();
    }

    /**
     * Triển khai nghiệp vụ UC08: Xem chi tiết một yêu cầu bất kỳ theo mã reservationId.
     */
    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationDetail(UUID reservationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("Mã yêu cầu không hợp lệ");
        }
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy yêu cầu đặt kho"));
        return toResponse(reservation);
    }

    /**
     * Triển khai nghiệp vụ UC08: Phê duyệt (Xác nhận) yêu cầu đặt kho từ khách hàng.
     * Quy tắc nghiệp vụ:
     * - Yêu cầu phải tồn tại và đang ở trạng thái PENDING.
     * - Kiểm tra số ô kho thực tế còn trống tại thời điểm duyệt.
     * - Nếu còn chỗ, cập nhật trạng thái sang CONFIRMED.
     */
    @Override
    @Transactional
    public ReservationResponse approveReservation(UUID reservationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("Mã yêu cầu không hợp lệ");
        }
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy yêu cầu đặt kho"));

        // Kiểm tra trạng thái hiện tại
        if (!"PENDING".equalsIgnoreCase(reservation.getStatus())) {
            throw new IllegalStateException("Chỉ có thể xác nhận yêu cầu ở trạng thái Chờ xác nhận (PENDING). Trạng thái hiện tại: " + reservation.getStatus());
        }

        // Kiểm tra xem kho còn ô trống khả dụng hay không
        long availableUnits = storageUnitRepository.countAvailableUnitsByFacilityAndType(reservation.getFacilityId(), reservation.getTypeId());
        if (availableUnits <= 0) {
            throw new IllegalStateException("Không thể xác nhận yêu cầu vì cơ sở hiện không còn ô kho trống cho loại kho này");
        }

        reservation.setStatus("CONFIRMED");
        Reservation updated = reservationRepository.save(reservation);
        return toResponse(updated);
    }

    /**
     * Triển khai nghiệp vụ UC08: Từ chối yêu cầu đặt kho của khách hàng.
     * Quy tắc nghiệp vụ:
     * - Yêu cầu phải tồn tại và đang ở trạng thái PENDING.
     * - Cập nhật trạng thái sang REJECTED, ghi nhận thời gian từ chối và lý do từ chối.
     */
    @Override
    @Transactional
    public ReservationResponse rejectReservation(UUID reservationId, RejectReservationRequest request) {
        if (reservationId == null) {
            throw new IllegalArgumentException("Mã yêu cầu không hợp lệ");
        }
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy yêu cầu đặt kho"));

        // Kiểm tra trạng thái hiện tại
        if (!"PENDING".equalsIgnoreCase(reservation.getStatus())) {
            throw new IllegalStateException("Chỉ có thể từ chối yêu cầu ở trạng thái Chờ xác nhận (PENDING). Trạng thái hiện tại: " + reservation.getStatus());
        }

        reservation.setStatus("REJECTED");
        reservation.setCancelledAt(LocalDateTime.now());
        String reason = (request != null && request.rejectionReason() != null && !request.rejectionReason().isBlank())
                ? request.rejectionReason().trim()
                : "Ban quản lý từ chối yêu cầu đặt kho";
        reservation.setCancellationReason(reason);

        Reservation updated = reservationRepository.save(reservation);
        return toResponse(updated);
    }

    // =========================================================================
    // Các hàm phụ trợ (Helper Methods)
    // =========================================================================

    /**
     * Hàm phụ trợ: Kiểm tra xem một yêu cầu đặt kho có khớp với từ khóa tìm kiếm hay không.
     * So khớp trên: mã yêu cầu (#SB-REQ-...), tên cơ sở, loại kho, họ tên khách hàng, số điện thoại.
     */
    private boolean matchesSearch(ReservationResponse res, String search) {
        if (search == null || search.isBlank()) return true;
        String q = search.trim().toLowerCase();
        return (res.requestCode() != null && res.requestCode().toLowerCase().contains(q))
                || (res.facilityName() != null && res.facilityName().toLowerCase().contains(q))
                || (res.typeName() != null && res.typeName().toLowerCase().contains(q))
                || (res.customerName() != null && res.customerName().toLowerCase().contains(q))
                || (res.customerPhone() != null && res.customerPhone().toLowerCase().contains(q));
    }

    /**
     * Hàm phụ trợ: Chuyển đổi thực thể Reservation thành DTO ReservationResponse
     * bằng cách tự động tra cứu các thông tin liên quan (khách hàng, cơ sở, loại kho, chính sách giá).
     */
    private ReservationResponse toResponse(Reservation res) {
        User customer = userRepository.findById(res.getCustomerId()).orElse(null);
        Facility facility = facilityRepository.findById(res.getFacilityId()).orElse(null);
        StorageUnitType unitType = storageUnitTypeRepository.findById(res.getTypeId()).orElse(null);
        PricingPolicy policy = pricingPolicyRepository.findById(res.getPricingId()).orElse(null);
        return toResponse(res, customer, facility, unitType, policy);
    }

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
