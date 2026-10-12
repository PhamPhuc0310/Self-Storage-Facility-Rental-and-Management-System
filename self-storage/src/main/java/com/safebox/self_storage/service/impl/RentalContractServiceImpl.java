package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.response.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.RentalContractService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class RentalContractServiceImpl implements RentalContractService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RentalContractRepository rentalContractRepository;
    private final StorageUnitRepository storageUnitRepository;
    private final StorageUnitTypeRepository storageUnitTypeRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final HandoverRecordRepository handoverRecordRepository;
    private final InspectionReportRepository inspectionReportRepository;
    private final UserFacilityAssignmentRepository userFacilityAssignmentRepository;

    public RentalContractServiceImpl(
            RentalContractRepository rentalContractRepository,
            StorageUnitRepository storageUnitRepository,
            StorageUnitTypeRepository storageUnitTypeRepository,
            FacilityRepository facilityRepository,
            UserRepository userRepository,
            InvoiceRepository invoiceRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            HandoverRecordRepository handoverRecordRepository,
            InspectionReportRepository inspectionReportRepository,
            UserFacilityAssignmentRepository userFacilityAssignmentRepository) {
        this.rentalContractRepository = rentalContractRepository;
        this.storageUnitRepository = storageUnitRepository;
        this.storageUnitTypeRepository = storageUnitTypeRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.handoverRecordRepository = handoverRecordRepository;
        this.inspectionReportRepository = inspectionReportRepository;
        this.userFacilityAssignmentRepository = userFacilityAssignmentRepository;
    }

    @Override
    public Page<RentalContractListItemResponse> getCustomerRentals(
            UUID customerId, String filterGroup, String search, Pageable pageable) {
        if (customerId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }

        Collection<String> statuses = resolveStatusFilter(filterGroup);
        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Page<RentalContract> contractPage = rentalContractRepository.findCustomerContractsWithFilter(
                customerId, statuses, cleanSearch, pageable);

        List<RentalContractListItemResponse> items = contractPage.getContent().stream()
                .map(this::mapToListItemResponse)
                .toList();

        return new PageImpl<>(items, pageable, contractPage.getTotalElements());
    }

    @Override
    public RentalContractDetailResponse getCustomerRentalDetail(UUID customerId, UUID contractId) {
        if (customerId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (contractId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã hợp đồng không được để trống");
        }

        RentalContract contract = rentalContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê"));

        // BR15.9: Không để khách xem hợp đồng người khác khi sửa ID trên URL
        if (!contract.getCustomerId().equals(customerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem thông tin hợp đồng này");
        }

        return mapToDetailResponse(contract);
    }

    @Override
    public Page<RentalContractListItemResponse> getFacilityRentals(
            UUID managerUserId, UUID facilityId, String filterGroup, String search, Pageable pageable) {
        if (managerUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        List<UUID> assignedFacilityIds = userFacilityAssignmentRepository.findActiveFacilityIds(managerUserId, today);

        UUID targetFacilityId = facilityId;
        if (targetFacilityId == null) {
            if (assignedFacilityIds.isEmpty()) {
                return Page.empty(pageable);
            }
            targetFacilityId = assignedFacilityIds.get(0);
        } else {
            // BR15.1: Quản lý cơ sở chỉ xem khách và hợp đồng tại cơ sở được phân công
            if (!assignedFacilityIds.contains(targetFacilityId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không được phân công phụ trách cơ sở này");
            }
        }

        Collection<String> statuses = resolveStatusFilter(filterGroup);
        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Page<RentalContract> contractPage = rentalContractRepository.findFacilityContractsWithFilter(
                targetFacilityId, statuses, cleanSearch, pageable);

        List<RentalContractListItemResponse> items = contractPage.getContent().stream()
                .map(this::mapToListItemResponse)
                .toList();

        return new PageImpl<>(items, pageable, contractPage.getTotalElements());
    }

    @Override
    public RentalContractDetailResponse getFacilityRentalDetail(UUID managerUserId, UUID contractId) {
        if (managerUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        if (contractId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã hợp đồng không được để trống");
        }

        RentalContract contract = rentalContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng thuê"));

        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kho vật lý liên kết"));

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        List<UUID> assignedFacilityIds = userFacilityAssignmentRepository.findActiveFacilityIds(managerUserId, today);
        if (!assignedFacilityIds.contains(unit.getFacilityId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Hợp đồng không thuộc cơ sở bạn được phân công phụ trách");
        }

        return mapToDetailResponse(contract);
    }

    /**
     * Chuyển đổi bộ lọc trạng thái theo quy tắc nghiệp vụ BR15.4:
     * - ALL: Tất cả trạng thái
     * - PENDING_HANDOVER: Chờ bàn giao (CONFIRMED, PENDING_HANDOVER, PENDING_CUSTOMER_CONFIRMATION)
     * - ACTIVE: Đang thuê (ACTIVE)
     * - ENDED: Đã kết thúc (CLOSED, COMPLETED, TERMINATED, CANCELLED)
     */
    private Collection<String> resolveStatusFilter(String filterGroup) {
        if (filterGroup == null || filterGroup.isBlank() || "ALL".equalsIgnoreCase(filterGroup)) {
            return null;
        }

        return switch (filterGroup.toUpperCase()) {
            case "PENDING_HANDOVER" -> List.of("CONFIRMED", "PENDING_HANDOVER", "PENDING_CUSTOMER_CONFIRMATION");
            case "ACTIVE" -> List.of("ACTIVE");
            case "ENDED" -> List.of("CLOSED", "COMPLETED", "TERMINATED", "CANCELLED");
            default -> List.of(filterGroup.toUpperCase());
        };
    }

    /**
     * Ánh xạ thực thể RentalContract sang RentalContractListItemResponse (BR15.2, BR15.3, BR15.6, BR15.8, BR15.10).
     */
    private RentalContractListItemResponse mapToListItemResponse(RentalContract contract) {
        StorageUnit unit = storageUnitRepository.findById(contract.getUnitId()).orElse(null);
        Facility facility = null;
        StorageUnitType unitType = null;

        if (unit != null) {
            facility = facilityRepository.findById(unit.getFacilityId()).orElse(null);
            unitType = storageUnitTypeRepository.findById(unit.getTypeId()).orElse(null);
        }

        // Kích thước và diện tích
        BigDecimal length = (unitType != null) ? unitType.getLength() : BigDecimal.ZERO;
        BigDecimal width = (unitType != null) ? unitType.getWidth() : BigDecimal.ZERO;
        BigDecimal height = (unitType != null) ? unitType.getHeight() : BigDecimal.ZERO;
        BigDecimal area = length.multiply(width);
        BigDecimal volume = area.multiply(height);
        String dimensions = String.format("%.1fm x %.1fm x %.1fm", length, width, height);

        // Chuỗi khoảng thời gian thuê
        String rentalPeriod = "";
        if (contract.getStartDate() != null && contract.getEndDate() != null) {
            rentalPeriod = contract.getStartDate().format(DATE_FORMATTER) + " - " + contract.getEndDate().format(DATE_FORMATTER);
        }

        // BR15.6: Số ngày còn lại tính theo Asia/Ho_Chi_Minh
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        long daysRemaining = 0;
        boolean isOverdue = false;
        long overdueDays = 0;

        if (contract.getEndDate() != null) {
            long diff = ChronoUnit.DAYS.between(today, contract.getEndDate());
            boolean isStillActive = "ACTIVE".equalsIgnoreCase(contract.getStatus()) || "CONFIRMED".equalsIgnoreCase(contract.getStatus());
            if (diff < 0) {
                if (isStillActive) {
                    isOverdue = true;
                    overdueDays = Math.abs(diff);
                }
                daysRemaining = 0;
            } else {
                daysRemaining = diff;
            }
        }

        // BR15.8: Tình trạng thanh toán lấy từ hóa đơn và giao dịch SUCCESS
        List<Invoice> invoices = invoiceRepository.findByContractId(contract.getContractId());
        BigDecimal totalBilled = invoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Nếu chưa có bảng invoices, mặc định là tiền thuê + tiền cọc
        if (totalBilled.compareTo(BigDecimal.ZERO) == 0) {
            totalBilled = contract.getMonthlyRentalFee().add(contract.getDepositAmount());
        }

        List<PaymentTransaction> successfulTransactions = paymentTransactionRepository.findByContractIdAndStatus(
                contract.getContractId(), "SUCCESS");

        BigDecimal totalPaid = successfulTransactions.stream()
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Nếu hóa đơn có ghi paidAmount mà giao dịch chưa liên kết trực tiếp contractId, cộng từ invoices
        if (totalPaid.compareTo(BigDecimal.ZERO) == 0) {
            totalPaid = invoices.stream()
                    .map(Invoice::getPaidAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal remainingAmount = totalBilled.subtract(totalPaid);
        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
            remainingAmount = BigDecimal.ZERO;
        }

        String paymentStatus = "UNPAID";
        if (totalPaid.compareTo(totalBilled) >= 0 && totalBilled.compareTo(BigDecimal.ZERO) > 0) {
            paymentStatus = "PAID";
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            paymentStatus = "PARTIALLY_PAID";
        }

        // BR15.10: Cho phép chuyển sang đặt lịch trả kho UC16 nếu đang ở trạng thái ACTIVE
        boolean canScheduleReturn = "ACTIVE".equalsIgnoreCase(contract.getStatus());

        return new RentalContractListItemResponse(
                contract.getContractId(),
                contract.getContractNumber(),
                contract.getReservationId(),
                contract.getCustomerId(),
                contract.getUnitId(),
                (unit != null) ? unit.getUnitNumber() : "N/A",
                (unit != null) ? unit.getFloor() : null,
                (unit != null) ? unit.getZone() : null,
                (facility != null) ? facility.getFacilityId() : null,
                (facility != null) ? facility.getName() : "SafeBox Facility",
                (facility != null) ? facility.getAddress() : "",
                (facility != null) ? facility.getPhone() : "",
                (unitType != null) ? unitType.getTypeId() : null,
                (unitType != null) ? unitType.getTypeName() : "Kho tiêu chuẩn",
                (unitType != null) ? unitType.getStorageMode() : "Tự quản",
                (unitType != null) ? unitType.getSizeName() : "",
                length,
                width,
                height,
                area,
                volume,
                dimensions,
                (unitType != null) ? unitType.getImagePath() : null,
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getActualStartDate(),
                contract.getActualEndDate(),
                rentalPeriod,
                contract.getMonthlyRentalFee(),
                contract.getDepositAmount(),
                totalBilled,
                totalPaid,
                remainingAmount,
                contract.getStatus(),
                RentalContractListItemResponse.getStatusLabel(contract.getStatus()),
                RentalContractListItemResponse.getStatusBadgeClass(contract.getStatus()),
                paymentStatus,
                RentalContractListItemResponse.getPaymentStatusLabel(paymentStatus),
                RentalContractListItemResponse.getPaymentBadgeClass(paymentStatus),
                daysRemaining,
                isOverdue,
                overdueDays,
                canScheduleReturn,
                contract.getCreatedAt()
        );
    }

    /**
     * Ánh xạ chi tiết hợp đồng và các hóa đơn, giao dịch, biên bản bàn giao (BR15.5).
     */
    private RentalContractDetailResponse mapToDetailResponse(RentalContract contract) {
        RentalContractListItemResponse listItem = mapToListItemResponse(contract);

        User customer = userRepository.findById(contract.getCustomerId()).orElse(null);

        // Danh sách hóa đơn
        List<Invoice> invoices = invoiceRepository.findByContractId(contract.getContractId());
        List<InvoiceSummaryResponse> invoiceResponses = invoices.stream()
                .map(inv -> {
                    BigDecimal remaining = inv.getTotalAmount().subtract(inv.getPaidAmount());
                    if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
                    return new InvoiceSummaryResponse(
                            inv.getInvoiceId(),
                            inv.getInvoiceNumber(),
                            inv.getInvoiceType(),
                            InvoiceSummaryResponse.getInvoiceTypeLabel(inv.getInvoiceType()),
                            inv.getDescription(),
                            inv.getSubtotal(),
                            inv.getTotalAmount(),
                            inv.getPaidAmount(),
                            remaining,
                            inv.getStatus(),
                            InvoiceSummaryResponse.getStatusLabel(inv.getStatus()),
                            inv.getIssuedAt(),
                            inv.getDueDate()
                    );
                })
                .toList();

        // Danh sách giao dịch thanh toán
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByContractId(contract.getContractId());
        if (transactions.isEmpty() && !invoices.isEmpty()) {
            List<UUID> invoiceIds = invoices.stream().map(Invoice::getInvoiceId).toList();
            transactions = paymentTransactionRepository.findByInvoiceIdIn(invoiceIds);
        }

        List<PaymentTransactionSummaryResponse> paymentResponses = transactions.stream()
                .map(tx -> new PaymentTransactionSummaryResponse(
                        tx.getTransactionId(),
                        tx.getInvoiceId(),
                        tx.getContractId(),
                        tx.getReferenceCode(),
                        tx.getTransactionType(),
                        tx.getPaymentType(),
                        tx.getPaymentMethod(),
                        PaymentTransactionSummaryResponse.getPaymentMethodLabel(tx.getPaymentMethod()),
                        tx.getAmount(),
                        tx.getStatus(),
                        PaymentTransactionSummaryResponse.getStatusLabel(tx.getStatus()),
                        tx.getFailureReason(),
                        tx.getTransactionDate()
                ))
                .toList();

        // Biên bản bàn giao
        List<HandoverRecord> handovers = handoverRecordRepository.findByContractIdOrderByScheduledAtDesc(contract.getContractId());
        List<HandoverRecordSummaryResponse> handoverResponses = handovers.stream()
                .map(ho -> new HandoverRecordSummaryResponse(
                        ho.getHandoverId(),
                        ho.getHandoverType(),
                        HandoverRecordSummaryResponse.getHandoverTypeLabel(ho.getHandoverType()),
                        ho.getScheduledAt(),
                        ho.getActualAt(),
                        ho.getAccessMethod(),
                        ho.getAccessIdentifier(),
                        ho.getNote(),
                        ho.getStatus(),
                        HandoverRecordSummaryResponse.getStatusLabel(ho.getStatus())
                ))
                .toList();

        // Biên bản kiểm tra tình trạng kho
        List<InspectionReport> inspections = inspectionReportRepository.findByContractIdOrderByInspectionDateDesc(contract.getContractId());
        List<InspectionReportSummaryResponse> inspectionResponses = inspections.stream()
                .map(ir -> new InspectionReportSummaryResponse(
                        ir.getInspectionId(),
                        ir.getInspectionType(),
                        ir.getInspectionDate(),
                        ir.getConditionStatus(),
                        ir.getTemperature(),
                        ir.getHumidity(),
                        ir.getDamageDescription(),
                        ir.getEstimatedRepairCost(),
                        ir.isCustomerConfirmed(),
                        ir.getNote()
                ))
                .toList();

        return new RentalContractDetailResponse(
                listItem,
                contract.getBillingCycle(),
                contract.getTermsVersion(),
                contract.getCustomerSignedAt(),
                contract.getApprovedAt(),
                (customer != null) ? customer.getFullName() : "Khách hàng",
                (customer != null) ? customer.getEmail() : "",
                (customer != null) ? customer.getPhone() : "",
                invoiceResponses,
                paymentResponses,
                handoverResponses,
                inspectionResponses
        );
    }
}
