package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.PaymentRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    public record Component(BigDecimal amountDue, BigDecimal paidAmount, BigDecimal remainingAmount, String status) {}
    public record Balance(BigDecimal depositPaidAmount, BigDecimal depositRemainingAmount,
                          BigDecimal rentRemainingAmount, boolean readyForHandover) {}
    public record Summary(UUID contractId, Component deposit, Component initialRent, boolean readyForHandover,
                          String termsVersion, String rentPaymentType, BigDecimal subtotal, BigDecimal vatAmount,
                          BigDecimal contractTotal, LocalDate remainingDueDate, boolean remainingPaymentAvailable) {
        public Summary(UUID contractId, Component deposit, Component initialRent, boolean readyForHandover) {
            this(contractId, deposit, initialRent, readyForHandover, "v1", "INITIAL_RENT",
                    deposit.amountDue().add(initialRent.amountDue()), BigDecimal.ZERO,
                    deposit.amountDue().add(initialRent.amountDue()), null, true);
        }
    }
    private final Sprint2Access access;
    private final PaymentTransactionRepository payments;
    private final InvoiceRepository invoices;
    private final PickupAppointmentRepository appointments;
    private final DepositRefundRepository refunds;
    public PaymentService(Sprint2Access access, PaymentTransactionRepository payments, InvoiceRepository invoices,
                          PickupAppointmentRepository appointments, DepositRefundRepository refunds) {
        this.access = access; this.payments = payments; this.invoices = invoices; this.appointments = appointments; this.refunds = refunds;
    }
    @Transactional(readOnly = true)
    public Summary summary(UUID id) {
        RentalContract r = access.contract(id, false);
        if ("FACILITY_STAFF".equals(access.role())) requireAssignedPickup(r, false);
        return calculate(r);
    }
    public Summary calculate(RentalContract r) {
        boolean v2 = "v2".equals(r.termsVersion);
        ContractPricing price = v2 ? ContractPricing.of(r.monthlyRentalFee, r.startDate, r.endDate) : null;
        Component deposit = component(r.contractId, "DEPOSIT", r.depositAmount);
        Component rent = component(r.contractId, v2 ? "REMAINING_RENT" : "INITIAL_RENT", v2 ? price.remaining() : r.monthlyRentalFee);
        LocalDate dueDate = v2 ? appointments.findFirstByContractIdAndStatus(r.contractId, "SCHEDULED")
                .map(a -> a.appointmentTime.toLocalDate()).orElse(null) : r.startDate;
        return new Summary(r.contractId, deposit, rent,
                "CONFIRMED".equals(r.status) && deposit.remainingAmount().signum() == 0 && rent.remainingAmount().signum() == 0,
                v2 ? "v2" : "v1", v2 ? "REMAINING_RENT" : "INITIAL_RENT",
                v2 ? price.subtotal() : r.depositAmount.add(r.monthlyRentalFee),
                v2 ? price.vatAmount() : BigDecimal.ZERO,
                v2 ? price.total() : r.depositAmount.add(r.monthlyRentalFee),
                dueDate, !v2 || (dueDate != null && !access.today().isBefore(dueDate)));
    }
    private Component component(UUID id, String type, BigDecimal due) {
        BigDecimal paid = payments.successful(id, type).stream().map(p -> p.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if ("DEPOSIT".equals(type)) paid = paid.subtract(Optional.ofNullable(refunds.completedTotal(id)).orElse(BigDecimal.ZERO));
        BigDecimal remaining = due.subtract(paid);
        return new Component(due, paid, remaining, remaining.signum() == 0 ? "PAID" : paid.signum() > 0 ? "PARTIAL" : "UNPAID");
    }
    public Map<UUID, Boolean> readyForHandover(Collection<RentalContract> contracts) {
        Map<UUID, Boolean> ready = new HashMap<>();
        balances(contracts).forEach((id, balance) -> ready.put(id, balance.readyForHandover()));
        return ready;
    }
    public Map<UUID, Balance> balances(Collection<RentalContract> contracts) {
        if (contracts.isEmpty()) return Map.of();
        Map<UUID, Map<String, BigDecimal>> totals = new HashMap<>();
        for (Object[] row : payments.successfulTotals(contracts.stream().map(c -> c.contractId).toList()))
            totals.computeIfAbsent((UUID) row[0], ignored -> new HashMap<>()).put((String) row[1], (BigDecimal) row[2]);
        for (Object[] row : Optional.ofNullable(refunds.completedTotals(contracts.stream().map(c -> c.contractId).toList())).orElseGet(List::of))
            totals.computeIfAbsent((UUID) row[0], ignored -> new HashMap<>())
                    .merge("DEPOSIT", ((BigDecimal) row[1]).negate(), BigDecimal::add);
        Map<UUID, Balance> result = new HashMap<>();
        for (RentalContract c : contracts) {
            Map<String, BigDecimal> byType = totals.getOrDefault(c.contractId, Map.of());
            BigDecimal depositPaid = byType.getOrDefault("DEPOSIT", BigDecimal.ZERO);
            BigDecimal depositLeft = c.depositAmount.subtract(depositPaid);
            BigDecimal rentDue = "v2".equals(c.termsVersion)
                    ? ContractPricing.of(c.monthlyRentalFee, c.startDate, c.endDate).remaining() : c.monthlyRentalFee;
            BigDecimal rentLeft = rentDue.subtract(byType.getOrDefault("v2".equals(c.termsVersion) ? "REMAINING_RENT" : "INITIAL_RENT", BigDecimal.ZERO));
            result.put(c.contractId, new Balance(depositPaid, depositLeft, rentLeft,
                    "CONFIRMED".equals(c.status) && depositLeft.signum() <= 0 && rentLeft.signum() <= 0));
        }
        return result;
    }
    @Transactional(readOnly = true)
    public List<PaymentTransaction> history(UUID id) {
        RentalContract r = access.contract(id, false);
        if ("FACILITY_STAFF".equals(access.role())) requireAssignedPickup(r, false);
        return payments.findByContractIdOrderByCreatedAtDesc(id);
    }
    @Transactional
    public PaymentTransaction pay(UUID id, PaymentRequest request) {
        RentalContract r = access.contract(id, true);
        access.confirmed(r);
        UUID actor = access.currentId();
        String role = access.role();
        String rentType = "v2".equals(r.termsVersion) ? "REMAINING_RENT" : "INITIAL_RENT";
        if (request == null || request.paymentType() == null || !Set.of("DEPOSIT", rentType).contains(request.paymentType())) throw new BusinessException(HttpStatus.BAD_REQUEST, "Loại khoản thu không hợp lệ.");
        if (request.amount() == null || request.amount().signum() <= 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Số tiền thanh toán không hợp lệ.");
        if ("v2".equals(r.termsVersion) && request.amount().stripTrailingZeros().scale() > 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số tiền thanh toán phải theo đơn vị VND.");
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank() || request.idempotencyKey().length() > 100)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mã yêu cầu thanh toán không hợp lệ.");
        Optional<PaymentTransaction> previous = payments.findByCreatedByAndIdempotencyKey(actor, request.idempotencyKey());
        if (previous.isPresent()) {
            PaymentTransaction p = previous.get();
            if (!p.contractId.equals(id) || !p.paymentType.equals(request.paymentType()) || p.amount.compareTo(request.amount()) != 0 || !p.paymentMethod.equals(request.paymentMethod()))
                throw new BusinessException(HttpStatus.CONFLICT, "Mã yêu cầu đã dùng cho khoản thanh toán khác.");
            return p;
        }
        boolean staff = "FACILITY_STAFF".equals(role);
        if (staff ? !rentType.equals(request.paymentType()) || !Set.of("CASH", "BANK_TRANSFER").contains(request.paymentMethod())
                  : !"CUSTOMER".equals(role) || !"DEPOSIT".equals(request.paymentType()) || !"BANK_TRANSFER".equals(request.paymentMethod()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Phương thức thanh toán không được phép.");
        if (staff) requireAssignedPickup(r, true);
        Summary summary = calculate(r);
        if (rentType.equals(request.paymentType()) && !summary.remainingPaymentAvailable())
            throw new BusinessException(HttpStatus.CONFLICT, "Phần tiền còn lại chỉ được thanh toán từ ngày nhận kho theo lịch hiện hành.");
        BigDecimal remaining = "DEPOSIT".equals(request.paymentType()) ? summary.deposit.remainingAmount() : summary.initialRent.remainingAmount();
        if (request.amount().compareTo(remaining) > 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "Số tiền vượt quá khoản còn thiếu.");
        Invoice invoice = invoices.findFirstByContractIdAndInvoiceType(id, request.paymentType())
                .orElseGet(() -> createInvoice(r, request.paymentType(), "DEPOSIT".equals(request.paymentType()) ? summary.deposit.amountDue() : summary.initialRent.amountDue()));
        PaymentTransaction p = new PaymentTransaction();
        p.transactionId = UUID.randomUUID(); p.referenceCode = "PAY-" + access.today().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + p.transactionId.toString().replace("-", "").toUpperCase();
        p.contractId = id; p.invoiceId = invoice.invoiceId; p.transactionType = "INITIAL_PAYMENT";
        p.paymentType = request.paymentType(); p.amount = request.amount();
        p.paymentMethod = request.paymentMethod(); p.status = "PENDING"; p.createdBy = actor;
        p.idempotencyKey = request.idempotencyKey(); p.createdAt = access.now(); p.updatedAt = p.createdAt; p.transactionDate = p.createdAt;
        payments.saveAndFlush(p);
        // The coursework backend processes customer deposits locally; no bank transfer is initiated.
        // Staff cash is recorded only after the assigned staff member confirms receipt.
        if (!staff || "CASH".equals(p.paymentMethod)) p.status = "SUCCESS";
        p.updatedAt = access.now();
        if ("SUCCESS".equals(p.status)) updateInvoice(invoice, p.amount);
        return payments.save(p);
    }
    private Invoice createInvoice(RentalContract r, String type, BigDecimal amount) {
        Invoice i = new Invoice(); i.invoiceId = UUID.randomUUID(); i.contractId = r.contractId; i.invoiceType = type;
        i.invoiceNumber = "INV-" + i.invoiceId.toString().substring(0, 8).toUpperCase();
        i.description = "DEPOSIT".equals(type) ? "Tiền cọc" : "v2".equals(r.termsVersion) ? "Phần tiền thuê còn lại" : "Tiền thuê ban đầu";
        i.subtotal = amount; i.totalAmount = amount; i.penaltyAmount = BigDecimal.ZERO;
        i.discountAmount = BigDecimal.ZERO; i.paidAmount = BigDecimal.ZERO;
        i.issuedAt = access.now();
        i.dueDate = "REMAINING_RENT".equals(type)
                ? appointments.findFirstByContractIdAndStatus(r.contractId, "SCHEDULED")
                    .map(a -> a.appointmentTime.toLocalDate().atStartOfDay()).orElse(r.startDate.atStartOfDay())
                : r.startDate.atStartOfDay();
        i.status = "UNPAID";
        return invoices.saveAndFlush(i);
    }
    private void updateInvoice(Invoice i, BigDecimal amount) {
        i.paidAmount = i.paidAmount.add(amount);
        i.status = i.paidAmount.compareTo(i.totalAmount) >= 0 ? "PAID" : "PARTIAL";
        invoices.save(i);
    }
    private PickupAppointment requireAssignedPickup(RentalContract r, boolean requireDue) {
        PickupAppointment pickup = appointments.findFirstByContractIdAndStatus(r.contractId, "SCHEDULED")
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Hợp đồng chưa có lịch nhận kho hợp lệ."));
        if (!access.currentId().equals(pickup.assignedStaffId) || !access.facilityId(r).equals(pickup.facilityId)
                || !r.customerId.equals(pickup.customerId))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn chưa được phân công lịch nhận kho này.");
        if (requireDue && access.today().isBefore(pickup.appointmentTime.toLocalDate()))
            throw new BusinessException(HttpStatus.CONFLICT, "Phần tiền còn lại chưa đến ngày nhận kho.");
        return pickup;
    }
    @Transactional
    public PaymentTransaction confirmTransfer(UUID id, UUID paymentId) {
        RentalContract r = access.contract(id, true);
        if (!"FACILITY_STAFF".equals(access.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ nhân viên cơ sở được xác nhận chuyển khoản.");
        access.confirmed(r);
        PaymentTransaction p = payments.findById(paymentId).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch."));
        if (!p.contractId.equals(id) || !"BANK_TRANSFER".equals(p.paymentMethod) || !"PENDING".equals(p.status))
            throw new BusinessException(HttpStatus.CONFLICT, "Khoản thanh toán đã được xử lý.");
        String rentType = "v2".equals(r.termsVersion) ? "REMAINING_RENT" : "INITIAL_RENT";
        if (!rentType.equals(p.paymentType))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Nhân viên chỉ được xác nhận phần tiền thuê còn lại.");
        requireAssignedPickup(r, true);
        Summary summary = calculate(r);
        if ("REMAINING_RENT".equals(p.paymentType) && !summary.remainingPaymentAvailable())
            throw new BusinessException(HttpStatus.CONFLICT, "Chưa đến hạn thanh toán phần tiền còn lại.");
        BigDecimal remaining = "DEPOSIT".equals(p.paymentType) ? summary.deposit.remainingAmount() : summary.initialRent.remainingAmount();
        if (p.amount.compareTo(remaining) > 0) throw new BusinessException(HttpStatus.CONFLICT, "Số tiền còn thiếu đã thay đổi.");
        p.status = "SUCCESS"; p.updatedAt = access.now();
        updateInvoice(invoices.findById(p.invoiceId).orElseThrow(), p.amount);
        return payments.save(p);
    }
}
