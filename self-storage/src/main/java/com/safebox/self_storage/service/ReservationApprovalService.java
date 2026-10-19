package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.dto.RejectReservationRequest;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationApprovalService {
    public record Review(UUID reservationId, UUID customerId, String customerName, UUID facilityId, String facilityName,
                         Integer typeId, String typeName, String status, BigDecimal monthlyPrice,
                         BigDecimal depositAmount, java.time.LocalDate startDate, java.time.LocalDate endDate,
                         LocalDateTime createdAt) {}

    private final Sprint2Access access;
    private final ReservationRepository reservations;
    private final RentalContractRepository contracts;
    private final InvoiceRepository invoices;
    private final StorageUnitRepository units;
    private final StorageUnitTypeRepository types;
    private final FacilityRepository facilities;
    private final UserRepository users;

    public ReservationApprovalService(Sprint2Access access, ReservationRepository reservations,
            RentalContractRepository contracts, InvoiceRepository invoices, StorageUnitRepository units,
            StorageUnitTypeRepository types, FacilityRepository facilities, UserRepository users) {
        this.access = access; this.reservations = reservations; this.contracts = contracts;
        this.invoices = invoices; this.units = units; this.types = types; this.facilities = facilities; this.users = users;
    }

    private void manager() {
        if (!"FACILITY_MANAGER".equals(access.role()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ quản lý cơ sở được duyệt yêu cầu.");
    }
    private void assigned(Reservation r) {
        if (!access.facilities().contains(r.getFacilityId()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Yêu cầu không thuộc cơ sở được phân công.");
    }
    @Transactional(readOnly = true)
    public List<Review> pending() {
        manager();
        List<UUID> allowed = access.facilities();
        if (allowed.isEmpty()) return List.of();
        List<Reservation> rows = reservations.findByStatusAndFacilityIdInOrderByCreatedAtDesc("PENDING", allowed);
        if (rows.isEmpty()) return List.of();
        Map<UUID, User> customerMap = new HashMap<>();
        users.findAllById(rows.stream().map(Reservation::getCustomerId).distinct().toList())
                .forEach(u -> customerMap.put(u.getUserId(), u));
        Map<UUID, Facility> facilityMap = new HashMap<>();
        facilities.findAllById(rows.stream().map(Reservation::getFacilityId).distinct().toList())
                .forEach(f -> facilityMap.put(f.getFacilityId(), f));
        Map<Integer, StorageUnitType> typeMap = new HashMap<>();
        types.findAllById(rows.stream().map(Reservation::getTypeId).distinct().toList())
                .forEach(t -> typeMap.put(t.getTypeId(), t));
        return rows.stream().map(r -> view(r, customerMap, facilityMap, typeMap)).toList();
    }
    private Review view(Reservation r, Map<UUID, User> customerMap,
            Map<UUID, Facility> facilityMap, Map<Integer, StorageUnitType> typeMap) {
        return new Review(r.getReservationId(), r.getCustomerId(),
                Optional.ofNullable(customerMap.get(r.getCustomerId())).map(User::getFullName).orElse(""),
                r.getFacilityId(),
                Optional.ofNullable(facilityMap.get(r.getFacilityId())).map(Facility::getName).orElse(""),
                r.getTypeId(), Optional.ofNullable(typeMap.get(r.getTypeId())).map(StorageUnitType::getTypeName).orElse(""),
                r.getStatus(), r.getQuotedMonthlyPrice(), r.getEstimatedDepositAmount(), r.getStartDate(), r.getEndDate(), r.getCreatedAt());
    }
    @Transactional
    public RentalContract approve(UUID id) {
        manager();
        Reservation r = reservations.lockById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt kho."));
        assigned(r);
        if ("CONFIRMED".equals(r.getStatus()))
            return contracts.findByReservationId(id)
                    .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Yêu cầu đã xác nhận nhưng thiếu hợp đồng."));
        if (!"PENDING".equals(r.getStatus()))
            throw new BusinessException(HttpStatus.CONFLICT, "Chỉ yêu cầu đang chờ mới được xác nhận.");
        if (contracts.findByReservationId(id).isPresent())
            throw new BusinessException(HttpStatus.CONFLICT, "Yêu cầu đã có hợp đồng.");
        User customer = users.findById(r.getCustomerId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Không tìm thấy khách hàng."));
        if (!"ACTIVE".equals(customer.getStatus()) || !"CUSTOMER".equals(customer.getRole().getRoleName()))
            throw new BusinessException(HttpStatus.CONFLICT, "Tài khoản khách hàng không hợp lệ.");
        Facility facility = facilities.findById(r.getFacilityId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Không tìm thấy cơ sở."));
        StorageUnitType type = types.findById(r.getTypeId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Không tìm thấy loại kho."));
        if (!"ACTIVE".equals(facility.getStatus()) || !"ACTIVE".equals(type.getStatus())
                || r.getStartDate() == null || r.getEndDate() == null
                || r.getStartDate().isBefore(access.today()) || !r.getEndDate().isAfter(r.getStartDate())
                || (r.getHoldExpiresAt() != null && !r.getHoldExpiresAt().isAfter(access.now())))
            throw new BusinessException(HttpStatus.CONFLICT, "Yêu cầu đã hết hiệu lực hoặc thông tin kho không hợp lệ.");
        BigDecimal monthly = r.getQuotedMonthlyPrice();
        if (monthly == null && !"v2".equals(r.getPricingVersion()) && r.getEstimatedRentalAmount() != null) {
            long months = java.time.temporal.ChronoUnit.MONTHS.between(r.getStartDate(), r.getEndDate());
            if (months > 0) monthly = r.getEstimatedRentalAmount()
                    .divide(BigDecimal.valueOf(months), 2, java.math.RoundingMode.HALF_UP);
        }
        if (monthly == null || monthly.signum() < 0)
            throw new BusinessException(HttpStatus.CONFLICT, "Yêu cầu chưa có giá thuê đã chốt.");
        boolean v2 = "v2".equals(r.getPricingVersion());
        ContractPricing price = null;
        if (v2) {
            try { price = ContractPricing.of(monthly, r.getStartDate(), r.getEndDate()); }
            catch (IllegalArgumentException ex) { throw new BusinessException(HttpStatus.CONFLICT, ex.getMessage()); }
        }
        BigDecimal deposit = v2 ? price.deposit() : r.getEstimatedDepositAmount();
        if (deposit == null || deposit.signum() < 0)
            throw new BusinessException(HttpStatus.CONFLICT, "Yêu cầu thiếu báo giá tiền cọc.");
        StorageUnit unit = units.lockFirstAvailable(r.getFacilityId(), r.getTypeId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Không còn ô kho phù hợp."));
        unit.setStatus("RESERVED");
        units.save(unit);
        LocalDateTime now = access.now();
        RentalContract c = new RentalContract();
        c.contractNumber = "SB-CT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        c.reservationId = id; c.customerId = r.getCustomerId(); c.unitId = unit.getUnitId();
        c.startDate = r.getStartDate(); c.endDate = r.getEndDate();
        c.monthlyRentalFee = monthly; c.depositAmount = deposit;
        c.billingCycle = "MONTHLY"; c.termsVersion = v2 ? "v2" : "v1"; c.status = "CONFIRMED";
        c.approvedBy = access.currentId(); c.approvedAt = now; c.createdAt = now;
        RentalContract saved = contracts.saveAndFlush(c);
        invoices.save(invoice(saved, "DEPOSIT", deposit, now));
        invoices.save(invoice(saved, v2 ? "REMAINING_RENT" : "INITIAL_RENT", v2 ? price.remaining() : monthly, now));
        r.setStatus("CONFIRMED");
        reservations.saveAndFlush(r);
        return saved;
    }
    @Transactional
    public void reject(UUID id, RejectReservationRequest request) {
        manager();
        Reservation r = reservations.lockById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đặt kho."));
        assigned(r);
        if (!"PENDING".equals(r.getStatus()) || contracts.findByReservationId(id).isPresent())
            throw new BusinessException(HttpStatus.CONFLICT, "Chỉ yêu cầu đang chờ và chưa có hợp đồng mới được từ chối.");
        String reason = request == null ? null : request.rejectionReason();
        r.setStatus("REJECTED");
        r.setCancellationReason(reason == null || reason.isBlank() ? "Quản lý cơ sở từ chối yêu cầu." : reason.trim());
        r.setCancelledAt(access.now());
        reservations.saveAndFlush(r);
    }
    private Invoice invoice(RentalContract c, String type, BigDecimal amount, LocalDateTime now) {
        Invoice i = new Invoice();
        i.invoiceNumber = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        i.contractId = c.contractId; i.invoiceType = type;
        i.description = "DEPOSIT".equals(type) ? "v2".equals(c.termsVersion) ? "Tiền cọc (1/3 tổng gồm VAT)" : "Tiền cọc" :
                "v2".equals(c.termsVersion) ? "Phần tiền thuê còn lại (2/3 tổng gồm VAT)" : "Tiền thuê ban đầu";
        i.subtotal = amount; i.penaltyAmount = BigDecimal.ZERO; i.discountAmount = BigDecimal.ZERO;
        i.totalAmount = amount; i.paidAmount = BigDecimal.ZERO;
        i.issuedAt = now; i.dueDate = c.startDate.atStartOfDay(); i.status = "UNPAID";
        return i;
    }
}
