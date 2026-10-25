package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.overdue.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.OverdueService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class OverdueServiceImpl implements OverdueService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RentalContractRepository rentalContractRepository;
    private final ContractPenaltyRepository contractPenaltyRepository;
    private final PenaltyAdjustmentRepository penaltyAdjustmentRepository;
    private final RentalPolicyRepository rentalPolicyRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final HandoverRecordRepository handoverRecordRepository;
    private final RenewalRepository renewalRepository;
    private final StorageUnitRepository storageUnitRepository;
    private final StorageUnitTypeRepository storageUnitTypeRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final UserFacilityAssignmentRepository userFacilityAssignmentRepository;

    public OverdueServiceImpl(
            RentalContractRepository rentalContractRepository,
            ContractPenaltyRepository contractPenaltyRepository,
            PenaltyAdjustmentRepository penaltyAdjustmentRepository,
            RentalPolicyRepository rentalPolicyRepository,
            InvoiceRepository invoiceRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            HandoverRecordRepository handoverRecordRepository,
            RenewalRepository renewalRepository,
            StorageUnitRepository storageUnitRepository,
            StorageUnitTypeRepository storageUnitTypeRepository,
            FacilityRepository facilityRepository,
            UserRepository userRepository,
            UserFacilityAssignmentRepository userFacilityAssignmentRepository) {
        this.rentalContractRepository = rentalContractRepository;
        this.contractPenaltyRepository = contractPenaltyRepository;
        this.penaltyAdjustmentRepository = penaltyAdjustmentRepository;
        this.rentalPolicyRepository = rentalPolicyRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.handoverRecordRepository = handoverRecordRepository;
        this.renewalRepository = renewalRepository;
        this.storageUnitRepository = storageUnitRepository;
        this.storageUnitTypeRepository = storageUnitTypeRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.userFacilityAssignmentRepository = userFacilityAssignmentRepository;
    }

    // =========================================================================
    // BR18.4: Danh sách hợp đồng quá hạn cho Quản lý cơ sở
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public Page<OverdueContractResponse> getManagerOverdueContracts(
            UUID managerUserId, UUID facilityId, String status, String search, Pageable pageable) {

        if (managerUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        User user = userRepository.findById(managerUserId).orElse(null);
        boolean isAdmin = user != null && user.getRole() != null
                && "SYSTEM_ADMIN".equalsIgnoreCase(user.getRole().getRoleName());

        List<UUID> assignedFacilityIds = userFacilityAssignmentRepository.findActiveFacilityIds(managerUserId, today);

        if (facilityId != null) {
            if (!isAdmin && !assignedFacilityIds.contains(facilityId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền quản lý tại cơ sở này");
            }
        } else if (!isAdmin && assignedFacilityIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<RentalContract> allContracts;
        if (facilityId != null) {
            allContracts = rentalContractRepository.findByFacilityId(facilityId);
        } else if (!isAdmin) {
            allContracts = rentalContractRepository.findByFacilityIds(assignedFacilityIds);
        } else {
            allContracts = rentalContractRepository.findAll();
        }

        // Lọc theo điều kiện quá hạn (BR18.1)
        List<OverdueContractResponse> overdueList = allContracts.stream()
                .filter(c -> isContractEligibleForOverdue(c, today))
                .map(c -> mapToOverdueResponse(c, today))
                .filter(res -> {
                    if (search != null && !search.isBlank()) {
                        String q = search.toLowerCase();
                        boolean matchNum = res.contractNumber() != null && res.contractNumber().toLowerCase().contains(q);
                        boolean matchCust = res.customerName() != null && res.customerName().toLowerCase().contains(q);
                        boolean matchUnit = res.unitNumber() != null && res.unitNumber().toLowerCase().contains(q);
                        if (!matchNum && !matchCust && !matchUnit) return false;
                    }
                    if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                        if ("DEBT".equalsIgnoreCase(status) && res.totalPenaltyDebt().compareTo(BigDecimal.ZERO) <= 0) {
                            return false;
                        }
                        if ("CLEARED".equalsIgnoreCase(status) && res.totalPenaltyDebt().compareTo(BigDecimal.ZERO) > 0) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparingInt(OverdueContractResponse::overdueDays).reversed())
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), overdueList.size());
        List<OverdueContractResponse> subList = (start <= end) ? overdueList.subList(start, end) : Collections.emptyList();

        return new PageImpl<>(subList, pageable, overdueList.size());
    }

    // =========================================================================
    // BR18.4: Danh sách hợp đồng quá hạn cho Khách hàng
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public Page<OverdueContractResponse> getCustomerOverdueContracts(UUID customerId, Pageable pageable) {
        if (customerId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        List<RentalContract> contracts = rentalContractRepository.findByCustomerIdOrderByStartDateDesc(customerId);

        List<OverdueContractResponse> list = contracts.stream()
                .filter(c -> isContractEligibleForOverdue(c, today))
                .map(c -> mapToOverdueResponse(c, today))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());
        List<OverdueContractResponse> subList = (start <= end) ? list.subList(start, end) : Collections.emptyList();

        return new PageImpl<>(subList, pageable, list.size());
    }

    // =========================================================================
    // Chi tiết hợp đồng quá hạn
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public OverdueContractResponse getOverdueContractDetail(UUID currentUserId, UUID contractId) {
        RentalContract contract = rentalContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng"));

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Không xác định người dùng"));

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        boolean isAdmin = user.getRole() != null && "SYSTEM_ADMIN".equalsIgnoreCase(user.getRole().getRoleName());
        boolean isCustomer = user.getRole() != null && "CUSTOMER".equalsIgnoreCase(user.getRole().getRoleName());

        if (isCustomer && !contract.getCustomerId().equals(currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem hợp đồng này");
        }

        if (!isAdmin && !isCustomer) {
            StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);
            UUID facilityId = (unit != null) ? unit.getFacilityId() : null;
            List<UUID> assigned = userFacilityAssignmentRepository.findActiveFacilityIds(currentUserId, today);
            if (facilityId != null && !assigned.contains(facilityId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không được phân công quản lý tại cơ sở này");
            }
        }

        return mapToOverdueResponse(contract, today);
    }

    // =========================================================================
    // BR18.1, BR18.2, BR18.3: Tính phí phạt quá hạn (Idempotent, có log công thức)
    // =========================================================================
    @Override
    @Transactional
    public PenaltyDetailResponse calculateOverdueFeeForContract(UUID currentUserId, UUID contractId, LocalDate targetDate) {
        RentalContract contract = rentalContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng"));

        LocalDate date = (targetDate != null) ? targetDate : LocalDate.now(VIETNAM_ZONE);

        // BR18.1: Kiểm tra tính quá hạn
        if (!isContractEligibleForOverdue(contract, date)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Hợp đồng không thuộc diện quá hạn tính phí (chưa tới hạn kết thúc, đã gia hạn hợp lệ hoặc đã hoàn tất trả kho).");
        }

        int overdueDays = (int) ChronoUnit.DAYS.between(contract.getEndDate(), date);
        if (overdueDays <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ngày tính phí phải sau ngày kết thúc hợp đồng.");
        }

        // BR18.3: Kiểm tra Idempotency - Mỗi kỳ phí chỉ phát sinh một lần
        String idempotencyKey = "PENALTY-" + contractId + "-" + date;
        Optional<ContractPenalty> existing = contractPenaltyRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return mapToPenaltyDetailResponse(existing.get(), contract.getContractNumber());
        }

        // BR18.2: Tra cứu chính sách có cấu hình và ngày hiệu lực rõ ràng
        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);
        UUID facilityId = (unit != null) ? unit.getFacilityId() : null;

        RentalPolicy policy = null;
        if (facilityId != null) {
            policy = rentalPolicyRepository
                    .findFirstByFacilityIdAndStatusOrderByEffectiveFromDesc(facilityId, "ACTIVE")
                    .orElse(null);
        }
        if (policy == null) {
            policy = rentalPolicyRepository
                    .findFirstByFacilityIdIsNullAndStatusOrderByEffectiveFromDesc("ACTIVE")
                    .orElseGet(() -> rentalPolicyRepository
                            .findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")
                            .orElse(null));
        }

        int graceDays = (policy != null && policy.getOverdueGraceDays() != null) ? policy.getOverdueGraceDays() : 0;
        int billableDays = Math.max(0, overdueDays - graceDays);

        BigDecimal dailyPenaltyRate = (policy != null && policy.getDailyPenaltyRate() != null)
                ? policy.getDailyPenaltyRate()
                : new BigDecimal("0.05"); // 5%/ngày

        BigDecimal monthlyFee = contract.getMonthlyRentalFee() != null ? contract.getMonthlyRentalFee() : BigDecimal.ZERO;
        BigDecimal dailyRate = monthlyFee.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);

        BigDecimal penaltyAmount = BigDecimal.ZERO;
        if (billableDays > 0) {
            String pType = (policy != null && policy.getPenaltyType() != null) ? policy.getPenaltyType() : "PERCENT_DAILY";
            if ("FIXED_DAILY".equalsIgnoreCase(pType) && policy.getFixedPenaltyAmount() != null) {
                penaltyAmount = policy.getFixedPenaltyAmount().multiply(BigDecimal.valueOf(billableDays));
            } else {
                penaltyAmount = dailyRate.multiply(BigDecimal.valueOf(billableDays))
                        .multiply(dailyPenaltyRate)
                        .setScale(0, RoundingMode.HALF_UP);
            }
        }

        // BR18.3: Tạo log giải thích cách tính minh bạch
        String rateText = dailyPenaltyRate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
        String explanation = String.format(
                "Đơn giá thuê: %,d đ/tháng (~%,.0f đ/ngày). Quá hạn %d ngày (Ân hạn: %d ngày -> Tính phí: %d ngày). "
                        + "Chính sách áp dụng: %s/ngày theo quy định cơ sở. Tổng phí phạt = %,.0f đ.",
                monthlyFee.longValue(),
                dailyRate.doubleValue(),
                overdueDays,
                graceDays,
                billableDays,
                rateText,
                penaltyAmount.doubleValue()
        );

        // Tạo hóa đơn tương ứng trong bảng INVOICES loại PENALTY
        Invoice invoice = new Invoice();
        invoice.invoiceNumber = "INV-PEN-" + contract.getContractNumber().replace("SB-CT-", "") + "-" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        invoice.contractId = contractId;
        invoice.invoiceType = "PENALTY";
        invoice.description = "Phí quá hạn hợp đồng " + contract.getContractNumber() + " (Kỳ: " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ")";
        invoice.subtotal = penaltyAmount;
        invoice.penaltyAmount = penaltyAmount;
        invoice.discountAmount = BigDecimal.ZERO;
        invoice.totalAmount = penaltyAmount;
        invoice.paidAmount = BigDecimal.ZERO;
        invoice.issuedAt = LocalDateTime.now(VIETNAM_ZONE);
        invoice.dueDate = date.atTime(23, 59, 59);
        invoice.status = (penaltyAmount.compareTo(BigDecimal.ZERO) == 0) ? "PAID" : "UNPAID";
        Invoice savedInvoice = invoiceRepository.saveAndFlush(invoice);

        // Tạo bản ghi CONTRACT_PENALTIES
        ContractPenalty penalty = new ContractPenalty();
        penalty.setContractId(contractId);
        penalty.setPolicyId(policy != null ? policy.getPolicyId() : null);
        penalty.setInvoiceId(savedInvoice.invoiceId);
        penalty.setPeriodDate(date);
        penalty.setOverdueDays(overdueDays);
        penalty.setBillableDays(billableDays);
        penalty.setDailyRate(dailyRate);
        penalty.setPenaltyRate(dailyPenaltyRate);
        penalty.setPenaltyAmount(penaltyAmount);
        penalty.setPaidAmount(BigDecimal.ZERO);
        penalty.setCalculationExplanation(explanation);
        penalty.setIdempotencyKey(idempotencyKey);
        penalty.setStatus((penaltyAmount.compareTo(BigDecimal.ZERO) == 0) ? "PAID" : "UNPAID");
        penalty.setCreatedAt(LocalDateTime.now(VIETNAM_ZONE));

        ContractPenalty savedPenalty = contractPenaltyRepository.saveAndFlush(penalty);

        // Cập nhật trạng thái hợp đồng thành OVERDUE nếu chưa cập nhật
        if (!"OVERDUE".equalsIgnoreCase(contract.getStatus()) && !"TERMINATED".equalsIgnoreCase(contract.getStatus())) {
            contract.setStatus("OVERDUE");
            rentalContractRepository.save(contract);
        }

        return mapToPenaltyDetailResponse(savedPenalty, contract.getContractNumber());
    }

    // =========================================================================
    // Quản lý quét và tính phí quá hạn hàng loạt cho cơ sở
    // =========================================================================
    @Override
    @Transactional
    public int batchCalculateOverdueFeesForFacility(UUID managerUserId, UUID facilityId, LocalDate targetDate) {
        LocalDate date = (targetDate != null) ? targetDate : LocalDate.now(VIETNAM_ZONE);
        List<RentalContract> contracts = (facilityId != null)
                ? rentalContractRepository.findByFacilityId(facilityId)
                : rentalContractRepository.findAll();

        int count = 0;
        for (RentalContract c : contracts) {
            if (isContractEligibleForOverdue(c, date)) {
                String idempotencyKey = "PENALTY-" + c.getContractId() + "-" + date;
                if (!contractPenaltyRepository.existsByIdempotencyKey(idempotencyKey)) {
                    calculateOverdueFeeForContract(managerUserId, c.getContractId(), date);
                    count++;
                }
            }
        }
        return count;
    }

    // =========================================================================
    // BR18.5: Quản lý điều chỉnh hoặc miễn giảm nợ có lưu vết kiểm toán
    // =========================================================================
    @Override
    @Transactional
    public PenaltyDetailResponse adjustPenaltyDebt(UUID managerUserId, UUID penaltyId, AdjustPenaltyRequest request) {
        if (managerUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }

        ContractPenalty penalty = contractPenaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản phí phạt"));

        RentalContract contract = rentalContractRepository.findByContractId(penalty.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng liên quan"));

        // Kiểm tra quyền quản lý tại cơ sở (BR18.4)
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        User user = userRepository.findById(managerUserId).orElse(null);
        boolean isAdmin = user != null && user.getRole() != null
                && "SYSTEM_ADMIN".equalsIgnoreCase(user.getRole().getRoleName());

        if (!isAdmin) {
            StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);
            UUID facilityId = (unit != null) ? unit.getFacilityId() : null;
            List<UUID> assigned = userFacilityAssignmentRepository.findActiveFacilityIds(managerUserId, today);
            if (facilityId != null && !assigned.contains(facilityId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền điều chỉnh công nợ tại cơ sở này");
            }
        }

        BigDecimal originalAmount = penalty.getPenaltyAmount();
        BigDecimal newAmount = request.getAdjustedAmount();

        if (newAmount.compareTo(originalAmount) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số tiền điều chỉnh không thể lớn hơn số nợ gốc.");
        }

        BigDecimal discountAmount = originalAmount.subtract(newAmount);

        // BR18.5: Lưu vết kiểm toán vào bảng PENALTY_ADJUSTMENTS
        PenaltyAdjustment adjustment = new PenaltyAdjustment();
        adjustment.setPenaltyId(penaltyId);
        adjustment.setContractId(contract.getContractId());
        adjustment.setOriginalAmount(originalAmount);
        adjustment.setAdjustedAmount(newAmount);
        adjustment.setDiscountAmount(discountAmount);
        adjustment.setAdjustmentType(request.getAdjustmentType());
        adjustment.setReason(request.getReason());
        adjustment.setAdjustedBy(managerUserId);
        adjustment.setAdjustedAt(LocalDateTime.now(VIETNAM_ZONE));
        adjustment.setNote(request.getNote());
        penaltyAdjustmentRepository.save(adjustment);

        // Cập nhật nợ trên ContractPenalty
        penalty.setPenaltyAmount(newAmount);
        if (newAmount.compareTo(BigDecimal.ZERO) == 0 || penalty.getPaidAmount().compareTo(newAmount) >= 0) {
            penalty.setStatus("ADJUSTED");
        }
        penalty.setUpdatedAt(LocalDateTime.now(VIETNAM_ZONE));
        contractPenaltyRepository.save(penalty);

        // Cập nhật hóa đơn tương ứng
        if (penalty.getInvoiceId() != null) {
            invoiceRepository.findById(penalty.getInvoiceId()).ifPresent(inv -> {
                inv.discountAmount = discountAmount;
                inv.totalAmount = newAmount;
                if (newAmount.compareTo(BigDecimal.ZERO) == 0 || inv.paidAmount.compareTo(newAmount) >= 0) {
                    inv.status = "PAID";
                }
                invoiceRepository.save(inv);
            });
        }

        return mapToPenaltyDetailResponse(penalty, contract.getContractNumber());
    }

    // =========================================================================
    // Thanh toán phí phạt quá hạn (Khách hàng thanh toán chờ duyệt, Quản lý thu tiền mặt ngay)
    // =========================================================================
    @Override
    @Transactional
    public PenaltyDetailResponse payPenaltyDebt(UUID currentUserId, UUID penaltyId, PayPenaltyRequest request) {
        ContractPenalty penalty = contractPenaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản phí phạt"));

        RentalContract contract = rentalContractRepository.findByContractId(penalty.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng liên quan"));

        if ("PAID".equalsIgnoreCase(penalty.getStatus()) || "ADJUSTED".equalsIgnoreCase(penalty.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Khoản phí phạt này đã được thanh toán hoặc điều chỉnh miễn giảm.");
        }

        if ("PENDING_APPROVAL".equalsIgnoreCase(penalty.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Khoản phí phạt này đã được gửi thanh toán và đang chờ Quản lý cơ sở xét duyệt.");
        }

        BigDecimal remaining = penalty.getPenaltyAmount().subtract(penalty.getPaidAmount());
        if (request.getAmount().compareTo(remaining) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    String.format("Số tiền thanh toán (%,.0f đ) vượt quá số nợ còn lại (%,.0f đ)",
                            request.getAmount().doubleValue(), remaining.doubleValue()));
        }

        boolean isCashCollection = "CASH".equalsIgnoreCase(request.getPaymentMethod());

        // Nếu quản lý thu tiền mặt trực tiếp tại quầy -> Xác nhận ngay
        if (isCashCollection) {
            BigDecimal newPaidAmount = penalty.getPaidAmount().add(request.getAmount());
            penalty.setPaidAmount(newPaidAmount);
            if (newPaidAmount.compareTo(penalty.getPenaltyAmount()) >= 0) {
                penalty.setStatus("PAID");
            } else {
                penalty.setStatus("PARTIAL");
            }
        } else {
            // Khách hàng thanh toán trực tuyến (VNPAY, Chuyển khoản...) -> Chờ Quản lý cơ sở duyệt
            penalty.setStatus("PENDING_APPROVAL");
        }

        penalty.setUpdatedAt(LocalDateTime.now(VIETNAM_ZONE));
        contractPenaltyRepository.save(penalty);

        // Tạo giao dịch thanh toán
        if (penalty.getInvoiceId() != null) {
            PaymentTransaction tx = new PaymentTransaction();
            tx.setInvoiceId(penalty.getInvoiceId());
            tx.contractId = contract.getContractId();
            tx.transactionType = "PAYMENT";
            tx.paymentType = "PENALTY";
            tx.paymentMethod = request.getPaymentMethod();
            tx.amount = request.getAmount();
            tx.transactionDate = LocalDateTime.now(VIETNAM_ZONE);
            tx.referenceCode = request.getReferenceCode();
            tx.status = isCashCollection ? "SUCCESS" : "PENDING";
            tx.idempotencyKey = request.getIdempotencyKey();
            tx.createdBy = currentUserId;
            paymentTransactionRepository.save(tx);

            if (isCashCollection) {
                invoiceRepository.findById(penalty.getInvoiceId()).ifPresent(inv -> {
                    inv.paidAmount = inv.paidAmount.add(request.getAmount());
                    if (inv.paidAmount.compareTo(inv.totalAmount) >= 0) {
                        inv.status = "PAID";
                    } else {
                        inv.status = "PARTIAL";
                    }
                    invoiceRepository.save(inv);
                });
            }
        }

        return mapToPenaltyDetailResponse(penalty, contract.getContractNumber());
    }

    // =========================================================================
    // Quản lý cơ sở xác nhận / duyệt thanh toán phí phạt khi khách hàng thanh toán
    // =========================================================================
    @Override
    @Transactional
    public PenaltyDetailResponse approvePenaltyPayment(UUID managerUserId, UUID penaltyId, boolean approved, String note) {
        if (managerUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }

        ContractPenalty penalty = contractPenaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản phí phạt"));

        RentalContract contract = rentalContractRepository.findByContractId(penalty.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng liên quan"));

        // Kiểm tra quyền cơ sở phụ trách (BR18.4)
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        User user = userRepository.findById(managerUserId).orElse(null);
        boolean isAdmin = user != null && user.getRole() != null
                && "SYSTEM_ADMIN".equalsIgnoreCase(user.getRole().getRoleName());

        if (!isAdmin) {
            StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);
            UUID facilityId = (unit != null) ? unit.getFacilityId() : null;
            List<UUID> assigned = userFacilityAssignmentRepository.findActiveFacilityIds(managerUserId, today);
            if (facilityId != null && !assigned.contains(facilityId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền duyệt thanh toán tại cơ sở này");
            }
        }

        if (!"PENDING_APPROVAL".equalsIgnoreCase(penalty.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Khoản phí phạt này không ở trạng thái chờ duyệt thanh toán.");
        }

        // Tìm giao dịch thanh toán PENDING của hóa đơn phí phạt này
        PaymentTransaction pendingTx = null;
        if (penalty.getInvoiceId() != null) {
            List<PaymentTransaction> txList = paymentTransactionRepository.findByInvoiceId(penalty.getInvoiceId());
            pendingTx = txList.stream()
                    .filter(tx -> "PENDING".equalsIgnoreCase(tx.status))
                    .findFirst()
                    .orElse(null);
        }

        BigDecimal payAmount = (pendingTx != null && pendingTx.amount != null)
                ? pendingTx.amount
                : penalty.getPenaltyAmount().subtract(penalty.getPaidAmount());

        if (approved) {
            BigDecimal newPaidAmount = penalty.getPaidAmount().add(payAmount);
            penalty.setPaidAmount(newPaidAmount);
            if (newPaidAmount.compareTo(penalty.getPenaltyAmount()) >= 0) {
                penalty.setStatus("PAID");
            } else {
                penalty.setStatus("PARTIAL");
            }

            if (pendingTx != null) {
                pendingTx.status = "SUCCESS";
                paymentTransactionRepository.save(pendingTx);
            }

            if (penalty.getInvoiceId() != null) {
                invoiceRepository.findById(penalty.getInvoiceId()).ifPresent(inv -> {
                    inv.paidAmount = inv.paidAmount.add(payAmount);
                    if (inv.paidAmount.compareTo(inv.totalAmount) >= 0) {
                        inv.status = "PAID";
                    } else {
                        inv.status = "PARTIAL";
                    }
                    invoiceRepository.save(inv);
                });
            }
        } else {
            // Từ chối thanh toán (ví dụ: chưa nhận được tiền hoặc chuyển khoản không đúng cú pháp)
            penalty.setStatus("UNPAID");
            if (pendingTx != null) {
                pendingTx.status = "FAILED";
                paymentTransactionRepository.save(pendingTx);
            }
        }

        penalty.setUpdatedAt(LocalDateTime.now(VIETNAM_ZONE));
        contractPenaltyRepository.save(penalty);

        return mapToPenaltyDetailResponse(penalty, contract.getContractNumber());
    }


    // =========================================================================
    // Helper: BR18.1 Kiểm tra xem hợp đồng có thuộc diện quá hạn hay không
    // =========================================================================
    private boolean isContractEligibleForOverdue(RentalContract contract, LocalDate checkDate) {
        if (contract == null || contract.getEndDate() == null) return false;

        // 1. Chỉ đánh dấu quá hạn khi quá thời điểm kết thúc
        if (!checkDate.isAfter(contract.getEndDate())) {
            return false;
        }

        // Không áp dụng cho hợp đồng đã bị hủy trước khi chạy
        if ("CANCELLED".equalsIgnoreCase(contract.getStatus())) {
            return false;
        }

        // 2. Chưa có gia hạn hợp lệ (Renewals đang APPROVED chưa hết hạn hoặc COMPLETED)
        List<Renewal> activeRenewals = renewalRepository.findByContractIdAndStatusIn(
                contract.getContractId(), List.of("COMPLETED", "APPROVED"));
        if (!activeRenewals.isEmpty()) {
            boolean hasValidRenewal = activeRenewals.stream().anyMatch(r -> {
                if ("COMPLETED".equalsIgnoreCase(r.getStatus())) return true;
                if ("APPROVED".equalsIgnoreCase(r.getStatus())) {
                    return r.getPaymentDeadline() == null || r.getPaymentDeadline().isAfter(LocalDateTime.now(VIETNAM_ZONE));
                }
                return false;
            });
            if (hasValidRenewal) return false;
        }

        // 3. Đã hoàn tất trả kho
        Optional<HandoverRecord> returnRecord = handoverRecordRepository
                .findFirstByContractIdAndHandoverTypeAndStatus(contract.getContractId(), "RETURN", "COMPLETED");
        if (returnRecord.isPresent()) {
            return false;
        }

        return true;
    }

    private OverdueContractResponse mapToOverdueResponse(RentalContract contract, LocalDate today) {
        User customer = userRepository.findById(contract.getCustomerId()).orElse(null);
        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);
        Facility facility = (unit != null) ? facilityRepository.findById(unit.getFacilityId()).orElse(null) : null;
        StorageUnitType unitType = (unit != null) ? storageUnitTypeRepository.findById(unit.getTypeId()).orElse(null) : null;

        int overdueDays = Math.max(0, (int) ChronoUnit.DAYS.between(contract.getEndDate(), today));

        RentalPolicy policy = null;
        if (facility != null) {
            policy = rentalPolicyRepository
                    .findFirstByFacilityIdAndStatusOrderByEffectiveFromDesc(facility.getFacilityId(), "ACTIVE")
                    .orElse(null);
        }
        if (policy == null) {
            policy = rentalPolicyRepository
                    .findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")
                    .orElse(null);
        }

        int graceDays = (policy != null && policy.getOverdueGraceDays() != null) ? policy.getOverdueGraceDays() : 0;
        int billableDays = Math.max(0, overdueDays - graceDays);

        BigDecimal monthlyFee = contract.getMonthlyRentalFee() != null ? contract.getMonthlyRentalFee() : BigDecimal.ZERO;
        BigDecimal dailyRent = monthlyFee.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);

        List<ContractPenalty> penalties = contractPenaltyRepository.findByContractIdOrderByPeriodDateDesc(contract.getContractId());
        List<PenaltyDetailResponse> penaltyDetails = penalties.stream()
                .map(p -> mapToPenaltyDetailResponse(p, contract.getContractNumber()))
                .toList();

        BigDecimal totalPenaltyDebt = penalties.stream()
                .filter(p -> "UNPAID".equalsIgnoreCase(p.getStatus()) || "PARTIAL".equalsIgnoreCase(p.getStatus()))
                .map(p -> p.getPenaltyAmount().subtract(p.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPenaltyPaid = penalties.stream()
                .map(ContractPenalty::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OverdueContractResponse(
                contract.getContractId(),
                contract.getContractNumber(),
                contract.getCustomerId(),
                (customer != null) ? customer.getFullName() : "Khách hàng SafeBox",
                (customer != null) ? customer.getEmail() : "",
                (customer != null) ? customer.getPhone() : "",
                (facility != null) ? facility.getFacilityId() : null,
                (facility != null) ? facility.getName() : "SafeBox Facility",
                (facility != null) ? facility.getAddress() : "",
                contract.getUnitId(),
                (unit != null) ? unit.getUnitNumber() : "--",
                (unitType != null) ? unitType.getTypeName() : "Kho tiêu chuẩn",
                contract.getStartDate(),
                contract.getEndDate(),
                monthlyFee,
                dailyRent,
                overdueDays,
                graceDays,
                billableDays,
                true,
                false,
                false,
                totalPenaltyDebt,
                totalPenaltyPaid,
                contract.getStatus(),
                penaltyDetails
        );
    }

    private PenaltyDetailResponse mapToPenaltyDetailResponse(ContractPenalty p, String contractNumber) {
        Invoice invoice = (p.getInvoiceId() != null) ? invoiceRepository.findById(p.getInvoiceId()).orElse(null) : null;
        List<PenaltyAdjustment> adjustments = penaltyAdjustmentRepository.findByPenaltyIdOrderByAdjustedAtDesc(p.getPenaltyId());

        List<AdjustmentDetailResponse> adjResponses = adjustments.stream()
                .map(a -> {
                    User adjUser = userRepository.findById(a.getAdjustedBy()).orElse(null);
                    return new AdjustmentDetailResponse(
                            a.getAdjustmentId(),
                            a.getPenaltyId(),
                            a.getContractId(),
                            a.getOriginalAmount(),
                            a.getAdjustedAmount(),
                            a.getDiscountAmount(),
                            a.getAdjustmentType(),
                            a.getReason(),
                            a.getAdjustedBy(),
                            (adjUser != null) ? adjUser.getFullName() : "Quản lý cơ sở",
                            a.getAdjustedAt(),
                            a.getNote()
                    );
                })
                .toList();

        BigDecimal remaining = p.getPenaltyAmount().subtract(p.getPaidAmount());

        return new PenaltyDetailResponse(
                p.getPenaltyId(),
                p.getContractId(),
                contractNumber,
                p.getInvoiceId(),
                (invoice != null) ? invoice.invoiceNumber : "--",
                p.getPeriodDate(),
                p.getOverdueDays(),
                p.getBillableDays(),
                p.getDailyRate(),
                p.getPenaltyRate(),
                p.getPenaltyAmount(),
                p.getPaidAmount(),
                remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO,
                p.getCalculationExplanation(),
                p.getIdempotencyKey(),
                p.getStatus(),
                p.getCreatedAt(),
                adjResponses
        );
    }
}
