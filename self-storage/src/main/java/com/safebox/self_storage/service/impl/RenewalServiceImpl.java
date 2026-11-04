package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.renewal.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.RenewalService;
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
import java.util.*;

@Service
@Transactional
public class RenewalServiceImpl implements RenewalService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RenewalRepository renewalRepository;
    private final RentalContractRepository rentalContractRepository;
    private final StorageUnitRepository storageUnitRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final PricingPolicyRepository pricingPolicyRepository;
    private final RentalPolicyRepository rentalPolicyRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final HandoverRecordRepository handoverRecordRepository;
    private final TerminationRequestRepository terminationRequestRepository;
    private final UserFacilityAssignmentRepository userFacilityAssignmentRepository;
    private final ActivityLogRepository activityLogRepository;

    public RenewalServiceImpl(
            RenewalRepository renewalRepository,
            RentalContractRepository rentalContractRepository,
            StorageUnitRepository storageUnitRepository,
            FacilityRepository facilityRepository,
            UserRepository userRepository,
            PricingPolicyRepository pricingPolicyRepository,
            RentalPolicyRepository rentalPolicyRepository,
            InvoiceRepository invoiceRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            HandoverRecordRepository handoverRecordRepository,
            TerminationRequestRepository terminationRequestRepository,
            UserFacilityAssignmentRepository userFacilityAssignmentRepository,
            ActivityLogRepository activityLogRepository) {
        this.renewalRepository = renewalRepository;
        this.rentalContractRepository = rentalContractRepository;
        this.storageUnitRepository = storageUnitRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.pricingPolicyRepository = pricingPolicyRepository;
        this.rentalPolicyRepository = rentalPolicyRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.handoverRecordRepository = handoverRecordRepository;
        this.terminationRequestRepository = terminationRequestRepository;
        this.userFacilityAssignmentRepository = userFacilityAssignmentRepository;
        this.activityLogRepository = activityLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public RenewalPreviewResponse previewRenewal(UUID customerId, UUID contractId, Integer months) {
        if (customerId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (contractId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã hợp đồng không được để trống");
        }
        int extensionMonths = (months != null && months >= 1) ? months : 1;

        RentalContract contract = rentalContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê"));

        // BR17.1: Chỉ CUSTOMER sở hữu hợp đồng mới được thao tác
        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác với hợp đồng này");
        }

        boolean canRenew = true;
        String warningMessage = "Ước tính phí gia hạn thành công";

        if (!"ACTIVE".equalsIgnoreCase(contract.getStatus())) {
            canRenew = false;
            warningMessage = "Chỉ hợp đồng đang hoạt động mới được yêu cầu gia hạn.";
        }

        // BR17.3: Kiểm tra yêu cầu trả kho đang mở
        Optional<HandoverRecord> openReturn = handoverRecordRepository
                .findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED");
        if (openReturn.isPresent()) {
            canRenew = false;
            warningMessage = "Hợp đồng đang có lịch hẹn trả kho. Vui lòng hủy lịch trả kho trước khi yêu cầu gia hạn.";
        }
        if (hasOpenEarlyReturn(contractId)) {
            canRenew = false;
            warningMessage = "Hợp đồng đang có yêu cầu trả kho sớm. Vui lòng xử lý yêu cầu này trước khi gia hạn.";
        }

        // BR17.5: Kiểm tra yêu cầu gia hạn đang chờ xử lý
        List<Renewal> activeRenewals = renewalRepository.findByContractIdAndStatusIn(
                contractId, List.of("PENDING", "APPROVED"));
        if (!activeRenewals.isEmpty()) {
            canRenew = false;
            warningMessage = "Hợp đồng đã có yêu cầu gia hạn đang được xử lý.";
        }

        // BR17.2: Ngày bắt đầu kỳ gia hạn nối tiếp ngày kết thúc hiện tại
        LocalDate oldEndDate = contract.getEndDate();
        LocalDate newEndDate = oldEndDate.plusMonths(extensionMonths);

        // BR17.4: Tra cứu chính sách giá snapshot
        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);
        UUID facilityId = (unit != null) ? unit.getFacilityId() : null;
        Integer typeId = (unit != null) ? unit.getTypeId() : null;

        BigDecimal monthlyPrice = contract.getMonthlyRentalFee();
        if (facilityId != null && typeId != null) {
            List<PricingPolicy> policies = pricingPolicyRepository.findByFacilityIdAndStatus(facilityId, "ACTIVE");
            Optional<PricingPolicy> currentPolicy = CurrentPricing.forType(policies, typeId, oldEndDate);
            if (currentPolicy.isPresent() && currentPolicy.get().getMonthlyPrice() != null) {
                monthlyPrice = currentPolicy.get().getMonthlyPrice();
            }
        }

        BigDecimal subtotal = monthlyPrice.multiply(BigDecimal.valueOf(extensionMonths)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal vat = subtotal.multiply(new BigDecimal("0.10")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = subtotal.add(vat).subtract(discount);

        int paymentHours = 24;
        if (facilityId != null) {
            paymentHours = rentalPolicyRepository
                    .findFirstByFacilityIdAndStatusOrderByEffectiveFromDesc(facilityId, "ACTIVE")
                    .map(RentalPolicy::getRenewalPaymentHours)
                    .orElse(24);
        }

        return new RenewalPreviewResponse(
                contractId,
                contract.getContractNumber(),
                oldEndDate,
                newEndDate,
                extensionMonths,
                monthlyPrice,
                subtotal,
                vat,
                discount,
                total,
                paymentHours,
                canRenew,
                warningMessage
        );
    }

    @Override
    public RenewalResponse requestRenewal(UUID customerId, UUID contractId, CreateRenewalRequest request) {
        if (customerId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (contractId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã hợp đồng không được để trống");
        }
        if (request == null || request.getMonths() == null || request.getMonths() < 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Kỳ hạn gia hạn tối thiểu là 1 tháng");
        }

        RentalContract contract = rentalContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê"));

        // BR17.1: Chỉ CUSTOMER sở hữu hợp đồng ACTIVE mới yêu cầu gia hạn
        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền yêu cầu gia hạn hợp đồng này");
        }
        if (!"ACTIVE".equalsIgnoreCase(contract.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Chỉ hợp đồng đang hoạt động mới được yêu cầu gia hạn.");
        }

        // BR17.5 & Rule: Retry không tạo gia hạn trùng / chỉ 1 yêu cầu gia hạn đang mở
        List<Renewal> activeRenewals = renewalRepository.findByContractIdAndStatusIn(
                contractId, List.of("PENDING", "APPROVED"));
        if (!activeRenewals.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Hợp đồng đã có yêu cầu gia hạn đang được xử lý.");
        }

        // BR17.3: Kiểm tra các yêu cầu trả kho đang mở trước khi lập gia hạn
        Optional<HandoverRecord> openReturn = handoverRecordRepository
                .findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED");
        if (openReturn.isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Hợp đồng đang có lịch hẹn trả kho. Vui lòng hủy lịch trả kho trước khi yêu cầu gia hạn.");
        }
        if (hasOpenEarlyReturn(contractId)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Hợp đồng đang có yêu cầu trả kho sớm. Vui lòng xử lý yêu cầu này trước khi gia hạn.");
        }

        // BR17.2: Ngày bắt đầu kỳ gia hạn nối tiếp ngày kết thúc hiện tại; không ghi đè lịch sử kỳ cũ
        LocalDate oldEndDate = contract.getEndDate();
        int months = request.getMonths();
        LocalDate newEndDate = oldEndDate.plusMonths(months);

        // BR17.4: Giá, VAT, ưu đãi được chốt thành snapshot của lần gia hạn
        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kho vật lý liên kết"));

        List<PricingPolicy> policies = pricingPolicyRepository.findByFacilityIdAndStatus(unit.getFacilityId(), "ACTIVE");
        Optional<PricingPolicy> currentPolicy = CurrentPricing.forType(policies, unit.getTypeId(), oldEndDate);

        BigDecimal monthlyPrice = currentPolicy.map(PricingPolicy::getMonthlyPrice).orElse(contract.getMonthlyRentalFee());
        UUID pricingId = currentPolicy.map(PricingPolicy::getPricingId).orElse(null);

        BigDecimal subtotal = monthlyPrice.multiply(BigDecimal.valueOf(months)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal vatAmount = subtotal.multiply(new BigDecimal("0.10")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal discountAmount = (request.getDiscountAmount() != null && request.getDiscountAmount().signum() >= 0)
                ? request.getDiscountAmount().setScale(0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal renewalAmount = subtotal.add(vatAmount).subtract(discountAmount);
        if (renewalAmount.compareTo(BigDecimal.ZERO) < 0) {
            renewalAmount = BigDecimal.ZERO;
        }

        Renewal renewal = new Renewal();
        renewal.setContractId(contractId);
        renewal.setOldEndDate(oldEndDate);
        renewal.setNewEndDate(newEndDate);
        renewal.setExtensionMonths(months);
        renewal.setMonthlyPrice(monthlyPrice);
        renewal.setSubtotal(subtotal);
        renewal.setVatAmount(vatAmount);
        renewal.setDiscountAmount(discountAmount);
        renewal.setRenewalAmount(renewalAmount);
        renewal.setPricingId(pricingId);
        renewal.setRequestedAt(LocalDateTime.now(VIETNAM_ZONE));
        renewal.setStatus("PENDING");

        Renewal saved = renewalRepository.save(renewal);
        return mapToRenewalResponse(saved, contract, unit);
    }

    @Override
    public RenewalResponse reviewRenewal(UUID managerUserId, UUID renewalId, ReviewRenewalRequest request) {
        if (managerUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (renewalId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã yêu cầu gia hạn không được để trống");
        }
        if (request == null || request.getAction() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Hành động xét duyệt không được để trống");
        }

        Renewal renewal = renewalRepository.findById(renewalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu gia hạn"));

        if (!"PENDING".equalsIgnoreCase(renewal.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Yêu cầu gia hạn này đã được xử lý trước đó. Trạng thái hiện tại: " + renewal.getStatus());
        }

        RentalContract contract = rentalContractRepository.findByContractId(renewal.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê liên quan"));

        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kho vật lý liên kết"));

        // Kiểm tra quyền hạn quản lý cơ sở
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        List<UUID> assignedFacilityIds = userFacilityAssignmentRepository.findActiveFacilityIds(managerUserId, today);
        User managerUser = userRepository.findById(managerUserId).orElse(null);
        boolean isAdmin = managerUser != null && managerUser.getRole() != null
                && "SYSTEM_ADMIN".equalsIgnoreCase(managerUser.getRole().getRoleName());

        if (!isAdmin && !assignedFacilityIds.contains(unit.getFacilityId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền quản lý tại cơ sở của hợp đồng này");
        }

        String action = request.getAction().toUpperCase();

        if ("REJECT".equals(action)) {
            if (request.getReason() == null || request.getReason().trim().isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối yêu cầu gia hạn");
            }
            renewal.setStatus("REJECTED");
            renewal.setRejectionReason(request.getReason().trim());
            renewal.setReviewedAt(LocalDateTime.now(VIETNAM_ZONE));
            renewal.setApprovedBy(managerUserId);
            Renewal saved = renewalRepository.save(renewal);
            return mapToRenewalResponse(saved, contract, unit);
        }

        if ("APPROVE".equals(action)) {
            // BR17.3: Phải kiểm tra khả dụng/điều kiện cơ sở và các yêu cầu trả kho đang mở trước khi duyệt
            if ("MAINTENANCE".equalsIgnoreCase(unit.getStatus()) || "INACTIVE".equalsIgnoreCase(unit.getStatus())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Không thể duyệt gia hạn vì ô kho đang ở trạng thái bảo trì hoặc ngưng hoạt động");
            }

            Facility facility = facilityRepository.findById(unit.getFacilityId()).orElse(null);
            if (facility != null && !"ACTIVE".equalsIgnoreCase(facility.getStatus())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Không thể duyệt gia hạn vì cơ sở đang tạm ngưng hoạt động");
            }

            Optional<HandoverRecord> openReturn = handoverRecordRepository
                    .findFirstByContractIdAndHandoverTypeAndStatus(contract.getContractId(), "RETURN", "SCHEDULED");
            if (openReturn.isPresent() || hasOpenEarlyReturn(contract.getContractId())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Không thể duyệt gia hạn do hợp đồng đang có yêu cầu trả kho đang mở/chờ xử lý");
            }

            // Xác định thời hạn thanh toán (payment_deadline)
            int paymentHours = 24;
            if (request.getCustomPaymentHours() != null && request.getCustomPaymentHours() > 0) {
                paymentHours = request.getCustomPaymentHours();
            } else {
                paymentHours = rentalPolicyRepository
                        .findFirstByFacilityIdAndStatusOrderByEffectiveFromDesc(unit.getFacilityId(), "ACTIVE")
                        .map(RentalPolicy::getRenewalPaymentHours)
                        .orElse(24);
            }
            LocalDateTime paymentDeadline = LocalDateTime.now(VIETNAM_ZONE).plusHours(paymentHours);

            renewal.setStatus("APPROVED");
            renewal.setApprovedBy(managerUserId);
            renewal.setReviewedAt(LocalDateTime.now(VIETNAM_ZONE));
            renewal.setPaymentDeadline(paymentDeadline);
            Renewal saved = renewalRepository.save(renewal);

            // Hệ thống tính phí, tạo khoản phải thu (Tạo hóa đơn RENEWAL)
            Invoice invoice = new Invoice();
            invoice.setContractId(contract.getContractId());
            invoice.setRenewalId(saved.getRenewalId());
            invoice.setInvoiceType("RENEWAL");
            invoice.setInvoiceNumber("INV-RNW-" + LocalDate.now(VIETNAM_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
            invoice.setDescription("Phí gia hạn hợp đồng " + contract.getContractNumber()
                    + " (" + saved.getExtensionMonths() + " tháng, từ "
                    + saved.getOldEndDate().format(DATE_FORMATTER) + " đến " + saved.getNewEndDate().format(DATE_FORMATTER) + ")");
            invoice.setSubtotal(saved.getSubtotal());
            invoice.setDiscountAmount(saved.getDiscountAmount() != null ? saved.getDiscountAmount() : BigDecimal.ZERO);
            invoice.setPenaltyAmount(BigDecimal.ZERO);
            invoice.setTotalAmount(saved.getRenewalAmount());
            invoice.setPaidAmount(BigDecimal.ZERO);
            invoice.setIssuedAt(LocalDateTime.now(VIETNAM_ZONE));
            invoice.setDueDate(paymentDeadline);
            invoice.setStatus("UNPAID");
            invoiceRepository.save(invoice);

            return mapToRenewalResponse(saved, contract, unit);
        }

        throw new BusinessException(HttpStatus.BAD_REQUEST, "Hành động xét duyệt không hợp lệ: " + action);
    }

    @Override
    public RenewalPaymentResponse payRenewal(UUID customerId, UUID renewalId, PayRenewalRequest request) {
        if (customerId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (renewalId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã yêu cầu gia hạn không được để trống");
        }
        if (request == null || request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số tiền thanh toán không hợp lệ");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã idempotency key không được để trống");
        }

        Renewal renewal = renewalRepository.findById(renewalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu gia hạn"));

        RentalContract contract = rentalContractRepository.lockById(renewal.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê liên quan"));

        // BR17.1: Chỉ CUSTOMER sở hữu hợp đồng mới được thanh toán
        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền thanh toán yêu cầu gia hạn này");
        }

        String method = request.getPaymentMethod() == null ? "" : request.getPaymentMethod().toUpperCase(Locale.ROOT);
        if (!Set.of("CASH", "BANK_TRANSFER").contains(method)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Phương thức thanh toán gia hạn chưa được hỗ trợ.");
        }
        if (request.getIdempotencyKey().length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã yêu cầu thanh toán không hợp lệ.");
        }

        Invoice invoice = invoiceRepository.findFirstByRenewalId(renewalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hóa đơn gia hạn liên kết"));
        if (!contract.getContractId().equals(invoice.getContractId()) || !"RENEWAL".equals(invoice.getInvoiceType())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Hóa đơn gia hạn không hợp lệ.");
        }

        Optional<PaymentTransaction> prevTx = paymentTransactionRepository
                .findByCreatedByAndIdempotencyKey(customerId, request.getIdempotencyKey());
        if (prevTx.isPresent()) {
            PaymentTransaction tx = prevTx.get();
            if (!invoice.getInvoiceId().equals(tx.getInvoiceId()) || !contract.getContractId().equals(tx.getContractId())
                    || !"RENEWAL".equals(tx.getPaymentType()) || !method.equals(tx.getPaymentMethod())
                    || tx.getAmount().compareTo(request.getAmount()) != 0) {
                throw new BusinessException(HttpStatus.CONFLICT, "Mã yêu cầu đã dùng cho khoản thanh toán khác.");
            }
            return renewalPaymentResponse(tx, renewal, contract, "Yêu cầu thanh toán đã được ghi nhận trước đó.");
        }

        if ("COMPLETED".equalsIgnoreCase(renewal.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Yêu cầu gia hạn này đã được thanh toán và hoàn tất thành công.");
        }

        if (!"APPROVED".equalsIgnoreCase(renewal.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Yêu cầu gia hạn chưa được quản lý duyệt hoặc không ở trạng thái chờ thanh toán. Trạng thái: " + renewal.getStatus());
        }

        // Kiểm tra hạn chót thanh toán
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        if (renewal.getPaymentDeadline() != null && now.isAfter(renewal.getPaymentDeadline())) {
            renewal.setStatus("EXPIRED");
            renewalRepository.save(renewal);
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Yêu cầu gia hạn đã hết hạn thanh toán vào lúc "
                            + renewal.getPaymentDeadline().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ".");
        }

        // Kiểm tra số tiền thanh toán
        if (request.getAmount().compareTo(renewal.getRenewalAmount()) != 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Số tiền thanh toán (" + request.getAmount() + " VND) không khớp với số tiền cần thanh toán (" + renewal.getRenewalAmount() + " VND).");
        }

        if (!"ACTIVE".equals(contract.getStatus()) || !renewal.getOldEndDate().equals(contract.getEndDate())
                || !"UNPAID".equals(invoice.getStatus()) || invoice.getPaidAmount().signum() != 0
                || invoice.getTotalAmount().compareTo(renewal.getRenewalAmount()) != 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Hợp đồng hoặc hóa đơn gia hạn đã thay đổi.");
        }
        if (paymentTransactionRepository.findByInvoiceId(invoice.getInvoiceId()).stream()
                .anyMatch(tx -> "PENDING".equals(tx.getStatus()) || "SUCCESS".equals(tx.getStatus()))) {
            throw new BusinessException(HttpStatus.CONFLICT, "Đã có yêu cầu thanh toán gia hạn đang chờ xác nhận.");
        }

        // Reuse the existing payment transaction and invoice lifecycle. Customer input is only a request.
        PaymentTransaction tx = new PaymentTransaction();
        tx.setContractId(contract.getContractId());
        tx.setInvoiceId(invoice.getInvoiceId());
        tx.setTransactionType("RENEWAL");
        tx.setPaymentType("RENEWAL");
        tx.setPaymentMethod(method);
        tx.setAmount(request.getAmount());
        tx.setTransactionDate(now);
        tx.setCreatedAt(now);
        tx.setUpdatedAt(now);
        tx.setCreatedBy(customerId);
        tx.setIdempotencyKey(request.getIdempotencyKey());
        tx.setStatus("PENDING");

        PaymentTransaction savedTx = paymentTransactionRepository.saveAndFlush(tx);
        savedTx.setReferenceCode("PAY-RNW-" + now.format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + savedTx.getTransactionId().toString().replace("-", "").substring(0, 8).toUpperCase());
        paymentTransactionRepository.save(savedTx);

        return renewalPaymentResponse(savedTx, renewal, contract,
                "Yêu cầu thanh toán đang chờ Quản lý cơ sở xác nhận sau khi nhận tiền.");
    }

    @Override
    @Transactional(readOnly = true)
    public List<RenewalPaymentResponse> pendingRenewalPayments(UUID managerId) {
        requireManagerRole(managerId);
        List<UUID> facilities = userFacilityAssignmentRepository.findActiveFacilityIds(managerId, LocalDate.now(VIETNAM_ZONE));
        if (facilities.isEmpty()) return List.of();
        return paymentTransactionRepository.pendingForFacilities(facilities).stream()
                .filter(p -> "RENEWAL".equals(p.getPaymentType()))
                .map(p -> {
                    Invoice invoice = invoiceRepository.findById(p.getInvoiceId()).orElse(null);
                    if (invoice == null || invoice.getRenewalId() == null || !"RENEWAL".equals(invoice.getInvoiceType())) return null;
                    Renewal renewal = renewalRepository.findById(invoice.getRenewalId()).orElse(null);
                    RentalContract contract = rentalContractRepository.findByContractId(p.getContractId()).orElse(null);
                    if (renewal == null || contract == null || !renewal.getContractId().equals(contract.getContractId())) return null;
                    return renewalPaymentResponse(p, renewal, contract, "Chờ xác nhận thanh toán.");
                })
                .filter(Objects::nonNull).toList();
    }

    @Override
    public RenewalPaymentResponse confirmRenewalPayment(UUID managerId, UUID renewalId, UUID paymentId,
                                                       ConfirmRenewalPaymentRequest request) {
        requireManagerRole(managerId);
        Renewal renewal = renewalRepository.findById(renewalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu gia hạn"));
        RentalContract contract = rentalContractRepository.lockById(renewal.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê"));
        requireManagerFacility(managerId, contract);
        Invoice invoice = invoiceRepository.findFirstByRenewalId(renewalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Không tìm thấy hóa đơn gia hạn"));
        PaymentTransaction payment = paymentTransactionRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"));
        validateRenewalPaymentLinks(renewal, contract, invoice, payment);

        if ("SUCCESS".equals(payment.getStatus())) {
            if (!"COMPLETED".equals(renewal.getStatus()) || !"PAID".equals(invoice.getStatus())
                    || !renewal.getNewEndDate().equals(contract.getEndDate())) {
                throw new BusinessException(HttpStatus.CONFLICT, "Trạng thái thanh toán gia hạn không nhất quán.");
            }
            return renewalPaymentResponse(payment, renewal, contract, "Khoản thanh toán đã được xác nhận trước đó.");
        }
        if (!"PENDING".equals(payment.getStatus()) || !"APPROVED".equals(renewal.getStatus())
                || !"ACTIVE".equals(contract.getStatus()) || !renewal.getOldEndDate().equals(contract.getEndDate())
                || !"UNPAID".equals(invoice.getStatus()) || invoice.getPaidAmount() == null
                || invoice.getPaidAmount().signum() != 0 || invoice.getTotalAmount().compareTo(payment.getAmount()) != 0
                || renewal.getRenewalAmount().compareTo(payment.getAmount()) != 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Khoản thanh toán gia hạn không còn hợp lệ để xác nhận.");
        }
        if ("BANK_TRANSFER".equals(payment.getPaymentMethod())) {
            String evidence = request == null ? null : request.verificationReference();
            if (evidence == null || evidence.isBlank() || evidence.trim().length() > 100) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Cần mã đối soát ngân hàng đã được kiểm chứng độc lập.");
            }
            payment.setReferenceCode(evidence.trim());
        } else if (!"CASH".equals(payment.getPaymentMethod())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Phương thức thanh toán gia hạn không hợp lệ.");
        }

        LocalDateTime confirmedAt = LocalDateTime.now(VIETNAM_ZONE);
        payment.setStatus("SUCCESS");
        payment.confirmedBy = managerId;
        payment.confirmedAt = confirmedAt;
        payment.setUpdatedAt(confirmedAt);
        invoice.setPaidAmount(payment.getAmount());
        invoice.setStatus("PAID");
        renewal.setStatus("COMPLETED");
        contract.setEndDate(renewal.getNewEndDate());
        paymentTransactionRepository.save(payment);
        invoiceRepository.save(invoice);
        renewalRepository.save(renewal);
        rentalContractRepository.save(contract);
        activityLogRepository.saveAndFlush(new ActivityLog(managerId, "CONFIRM_RENEWAL_PAYMENT", "PAYMENT_TRANSACTION",
                payment.getTransactionId(), "role=FACILITY_MANAGER;result=SUCCESS;paymentType=RENEWAL;method="
                        + payment.getPaymentMethod() + ";invoiceId=" + invoice.getInvoiceId()
                        + ";invoiceNumber=" + invoice.getInvoiceNumber() + ";renewalId=" + renewalId
                        + ";paymentReference=" + payment.getReferenceCode(), confirmedAt));
        return renewalPaymentResponse(payment, renewal, contract,
                "Đã xác nhận thanh toán. Hợp đồng được gia hạn đến " + contract.getEndDate().format(DATE_FORMATTER));
    }

    @Override
    public RenewalPaymentResponse rejectRenewalPayment(UUID managerId, UUID renewalId, UUID paymentId, String reason) {
        requireManagerRole(managerId);
        Renewal renewal = renewalRepository.findById(renewalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu gia hạn"));
        RentalContract contract = rentalContractRepository.lockById(renewal.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê"));
        requireManagerFacility(managerId, contract);
        Invoice invoice = invoiceRepository.findFirstByRenewalId(renewalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Không tìm thấy hóa đơn gia hạn"));
        PaymentTransaction payment = paymentTransactionRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"));
        validateRenewalPaymentLinks(renewal, contract, invoice, payment);
        if (!"PENDING".equals(payment.getStatus()) || !"APPROVED".equals(renewal.getStatus())
                || !"UNPAID".equals(invoice.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Khoản thanh toán đã được xử lý.");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cần lý do từ chối thanh toán.");
        }
        payment.setStatus("FAILED");
        payment.setFailureReason(reason.trim());
        payment.setUpdatedAt(LocalDateTime.now(VIETNAM_ZONE));
        paymentTransactionRepository.save(payment);
        activityLogRepository.saveAndFlush(new ActivityLog(managerId, "REJECT_RENEWAL_PAYMENT", "PAYMENT_TRANSACTION",
                paymentId, "role=FACILITY_MANAGER;result=FAILED;invoiceId=" + invoice.getInvoiceId()
                        + ";renewalId=" + renewalId, payment.getUpdatedAt()));
        return renewalPaymentResponse(payment, renewal, contract, "Yêu cầu thanh toán đã bị từ chối.");
    }

    private void requireManagerRole(UUID managerId) {
        User actor = managerId == null ? null : userRepository.findById(managerId).orElse(null);
        if (actor == null || actor.getRole() == null
                || !"FACILITY_MANAGER".equals(actor.getRole().getRoleName())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ Quản lý cơ sở được xác nhận thanh toán gia hạn.");
        }
    }

    private void requireManagerFacility(UUID managerId, RentalContract contract) {
        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Không tìm thấy kho của hợp đồng."));
        if (!userFacilityAssignmentRepository.findActiveFacilityIds(managerId, LocalDate.now(VIETNAM_ZONE))
                .contains(unit.getFacilityId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không quản lý cơ sở của hợp đồng này.");
        }
    }

    private void validateRenewalPaymentLinks(Renewal renewal, RentalContract contract, Invoice invoice,
                                             PaymentTransaction payment) {
        if (!"RENEWAL".equals(invoice.getInvoiceType()) || !renewal.getRenewalId().equals(invoice.getRenewalId())
                || !contract.getContractId().equals(invoice.getContractId())
                || !invoice.getInvoiceId().equals(payment.getInvoiceId())
                || !contract.getContractId().equals(payment.getContractId())
                || !"RENEWAL".equals(payment.getPaymentType()) || !"RENEWAL".equals(payment.getTransactionType())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Giao dịch không thuộc hóa đơn gia hạn này.");
        }
    }

    private RenewalPaymentResponse renewalPaymentResponse(PaymentTransaction payment, Renewal renewal,
                                                          RentalContract contract, String message) {
        return new RenewalPaymentResponse(payment.getTransactionId(), renewal.getRenewalId(), contract.getContractId(),
                payment.getReferenceCode(), payment.getAmount(), payment.getPaymentMethod(), payment.getStatus(),
                payment.getTransactionDate(), contract.getEndDate(), message);
    }

    @Override
    public RenewalResponse cancelRenewal(UUID customerId, UUID renewalId) {
        if (customerId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (renewalId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã yêu cầu gia hạn không được để trống");
        }

        Renewal renewal = renewalRepository.findById(renewalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu gia hạn"));

        RentalContract contract = rentalContractRepository.findByContractId(renewal.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê liên quan"));

        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác với yêu cầu gia hạn này");
        }

        if (!"PENDING".equalsIgnoreCase(renewal.getStatus()) && !"APPROVED".equalsIgnoreCase(renewal.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Không thể hủy yêu cầu gia hạn ở trạng thái: " + renewal.getStatus());
        }

        Invoice pendingInvoice = invoiceRepository.findFirstByRenewalId(renewalId).orElse(null);
        if (pendingInvoice != null && paymentTransactionRepository.findByInvoiceId(pendingInvoice.getInvoiceId()).stream()
                .anyMatch(p -> "PENDING".equals(p.getStatus()))) {
            throw new BusinessException(HttpStatus.CONFLICT, "Không thể hủy gia hạn khi thanh toán đang chờ xác nhận.");
        }

        renewal.setStatus("CANCELLED");
        Renewal saved = renewalRepository.save(renewal);

        // Nếu đã có hóa đơn phát sinh chưa thanh toán, hủy hóa đơn
        invoiceRepository.findFirstByRenewalId(renewalId).ifPresent(inv -> {
            if (!"PAID".equalsIgnoreCase(inv.getStatus())) {
                inv.setStatus("CANCELLED");
                invoiceRepository.save(inv);
            }
        });

        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);
        return mapToRenewalResponse(saved, contract, unit);
    }

    @Override
    @Transactional(readOnly = true)
    public RenewalResponse getRenewalDetail(UUID currentUserId, UUID renewalId) {
        if (currentUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (renewalId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã yêu cầu gia hạn không được để trống");
        }

        Renewal renewal = renewalRepository.findById(renewalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu gia hạn"));

        RentalContract contract = rentalContractRepository.findByContractId(renewal.getContractId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê liên quan"));

        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);

        // Kiểm tra phân quyền truy cập
        boolean isOwner = contract.getCustomerId().equals(currentUserId);
        if (!isOwner) {
            LocalDate today = LocalDate.now(VIETNAM_ZONE);
            List<UUID> assignedFacilityIds = userFacilityAssignmentRepository.findActiveFacilityIds(currentUserId, today);
            User user = userRepository.findById(currentUserId).orElse(null);
            boolean isAdmin = user != null && user.getRole() != null
                    && "SYSTEM_ADMIN".equalsIgnoreCase(user.getRole().getRoleName());
            boolean isAssignedManager = unit != null && assignedFacilityIds.contains(unit.getFacilityId());

            if (!isAdmin && !isAssignedManager) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem thông tin yêu cầu gia hạn này");
            }
        }

        return mapToRenewalResponse(renewal, contract, unit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RenewalResponse> getContractRenewals(UUID currentUserId, UUID contractId) {
        if (currentUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (contractId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã hợp đồng không được để trống");
        }

        RentalContract contract = rentalContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê"));

        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);

        boolean isOwner = contract.getCustomerId().equals(currentUserId);
        if (!isOwner) {
            LocalDate today = LocalDate.now(VIETNAM_ZONE);
            List<UUID> assignedFacilityIds = userFacilityAssignmentRepository.findActiveFacilityIds(currentUserId, today);
            User user = userRepository.findById(currentUserId).orElse(null);
            boolean isAdmin = user != null && user.getRole() != null
                    && "SYSTEM_ADMIN".equalsIgnoreCase(user.getRole().getRoleName());
            boolean isAssignedManager = unit != null && assignedFacilityIds.contains(unit.getFacilityId());

            if (!isAdmin && !isAssignedManager) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem thông tin hợp đồng này");
            }
        }

        List<Renewal> renewals = renewalRepository.findByContractIdOrderByRequestedAtDesc(contractId);
        return renewals.stream()
                .map(r -> mapToRenewalResponse(r, contract, unit))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RenewalResponse> getCustomerRenewals(UUID customerId, String status, Pageable pageable) {
        if (customerId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }

        Collection<String> statuses = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status))
                ? List.of(status.toUpperCase())
                : null;

        Page<Renewal> page = renewalRepository.findCustomerRenewalsWithFilter(customerId, statuses, pageable);
        List<RenewalResponse> items = page.getContent().stream()
                .map(r -> {
                    RentalContract contract = rentalContractRepository.findByContractId(r.getContractId()).orElse(null);
                    StorageUnit unit = (contract != null) ? storageUnitRepository.findById(contract.getUnitId()).orElse(null) : null;
                    return mapToRenewalResponse(r, contract, unit);
                })
                .toList();

        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RenewalResponse> getFacilityRenewals(UUID managerUserId, UUID facilityId, String status, Pageable pageable) {
        if (managerUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        List<UUID> assignedFacilityIds = userFacilityAssignmentRepository.findActiveFacilityIds(managerUserId, today);
        User user = userRepository.findById(managerUserId).orElse(null);
        boolean isAdmin = user != null && user.getRole() != null
                && "SYSTEM_ADMIN".equalsIgnoreCase(user.getRole().getRoleName());

        UUID targetFacilityId = facilityId;
        if (targetFacilityId != null) {
            if (!isAdmin && !assignedFacilityIds.contains(targetFacilityId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không được phân công quản lý tại cơ sở này");
            }
        } else if (!isAdmin) {
            if (assignedFacilityIds.isEmpty()) {
                return Page.empty(pageable);
            }
            if (assignedFacilityIds.size() == 1) {
                targetFacilityId = assignedFacilityIds.get(0);
            }
        }

        Collection<String> statuses = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status))
                ? List.of(status.toUpperCase())
                : null;

        Page<Renewal> page;
        if (targetFacilityId != null) {
            page = renewalRepository.findFacilityRenewals(targetFacilityId, statuses, pageable);
        } else if (!assignedFacilityIds.isEmpty()) {
            page = renewalRepository.findByFacilityIdsAndStatusIn(assignedFacilityIds, statuses, pageable);
        } else {
            page = renewalRepository.findFacilityRenewals(null, statuses, pageable);
        }

        List<RenewalResponse> items = page.getContent().stream()
                .map(r -> {
                    RentalContract contract = rentalContractRepository.findByContractId(r.getContractId()).orElse(null);
                    StorageUnit unit = (contract != null) ? storageUnitRepository.findById(contract.getUnitId()).orElse(null) : null;
                    return mapToRenewalResponse(r, contract, unit);
                })
                .toList();

        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    private boolean hasOpenEarlyReturn(UUID contractId) {
        return terminationRequestRepository.existsByContractIdAndTypeAndStatusIn(
                contractId, "EARLY_RETURN", List.of("REQUESTED", "SETTLEMENT_APPROVED", "REFUNDED"));
    }

    private RenewalResponse mapToRenewalResponse(Renewal r, RentalContract contract, StorageUnit unit) {
        Facility facility = null;
        if (unit != null) {
            facility = facilityRepository.findById(unit.getFacilityId()).orElse(null);
        }

        User customer = (contract != null) ? userRepository.findById(contract.getCustomerId()).orElse(null) : null;
        User reviewer = (r.getApprovedBy() != null) ? userRepository.findById(r.getApprovedBy()).orElse(null) : null;

        Invoice invoice = invoiceRepository.findFirstByRenewalId(r.getRenewalId()).orElse(null);
        boolean paymentPending = invoice != null && Optional.ofNullable(paymentTransactionRepository
                .findByInvoiceId(invoice.getInvoiceId())).orElseGet(List::of).stream()
                .anyMatch(p -> "PENDING".equals(p.getStatus()));

        boolean canPay = "APPROVED".equalsIgnoreCase(r.getStatus())
                && !paymentPending && (r.getPaymentDeadline() == null
                || !LocalDateTime.now(VIETNAM_ZONE).isAfter(r.getPaymentDeadline()));

        boolean canCancel = "PENDING".equalsIgnoreCase(r.getStatus())
                || ("APPROVED".equalsIgnoreCase(r.getStatus()) && !paymentPending
                && (invoice == null || !"PAID".equalsIgnoreCase(invoice.getStatus())));

        return new RenewalResponse(
                r.getRenewalId(),
                r.getContractId(),
                (contract != null) ? contract.getContractNumber() : "N/A",
                (contract != null) ? contract.getCustomerId() : null,
                (customer != null) ? customer.getFullName() : "N/A",
                (facility != null) ? facility.getFacilityId() : null,
                (facility != null) ? facility.getName() : "N/A",
                (unit != null) ? unit.getUnitNumber() : "N/A",
                r.getOldEndDate(),
                r.getNewEndDate(),
                r.getExtensionMonths(),
                r.getMonthlyPrice(),
                r.getSubtotal(),
                r.getVatAmount(),
                r.getDiscountAmount(),
                r.getRenewalAmount(),
                r.getRequestedAt(),
                r.getReviewedAt(),
                r.getApprovedBy(),
                (reviewer != null) ? reviewer.getFullName() : null,
                r.getPaymentDeadline(),
                r.getStatus(),
                paymentPending ? "Chờ xác nhận thanh toán" : RenewalResponse.getStatusLabel(r.getStatus()),
                r.getRejectionReason(),
                (invoice != null) ? invoice.getInvoiceId() : null,
                (invoice != null) ? invoice.getInvoiceNumber() : null,
                (invoice != null) ? invoice.getStatus() : null,
                canPay,
                canCancel
        );
    }
}
