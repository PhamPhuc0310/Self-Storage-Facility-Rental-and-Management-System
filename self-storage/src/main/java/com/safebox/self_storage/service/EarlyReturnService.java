package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EarlyReturnService {
    private static final List<String> OPEN = List.of("REQUESTED", "SETTLEMENT_APPROVED", "REFUNDED");
    private final Sprint2Access access;
    private final TerminationRequestRepository requests;
    private final EarlyReturnPolicyRepository policies;
    private final EarlyReturnSettlementRepository settlements;
    private final HandoverRecordRepository handovers;
    private final InspectionReportRepository inspections;
    private final PaymentTransactionRepository payments;
    private final InvoiceRepository invoices;
    private final DepositRefundRepository refunds;

    public EarlyReturnService(Sprint2Access access, TerminationRequestRepository requests,
            EarlyReturnPolicyRepository policies, EarlyReturnSettlementRepository settlements,
            HandoverRecordRepository handovers, InspectionReportRepository inspections,
            PaymentTransactionRepository payments, InvoiceRepository invoices, DepositRefundRepository refunds) {
        this.access = access; this.requests = requests; this.policies = policies; this.settlements = settlements;
        this.handovers = handovers; this.inspections = inspections; this.payments = payments;
        this.invoices = invoices; this.refunds = refunds;
    }

    public record RequestInput(LocalDateTime proposedReturnAt, String reason, String notes) {}
    public record ApprovalInput(BigDecimal inspectionDeduction, String deductionReason) {}
    public record RejectInput(String reason) {}
    public record SettlementView(String status, EarlyReturnSettlement settlement, String message) {}

    @Transactional
    public TerminationRequest request(UUID contractId, RequestInput input) {
        if (!"CUSTOMER".equals(access.role())) throw forbidden();
        RentalContract contract = access.contract(contractId, true);
        if (!"ACTIVE".equals(contract.status)) throw conflict("Only an active contract may be returned early.");
        if (input == null || input.proposedReturnAt() == null || input.reason() == null || input.reason().isBlank())
            throw bad("A proposed return time and reason are required.");
        if (!input.proposedReturnAt().isAfter(access.now()) || !input.proposedReturnAt().toLocalDate().isBefore(contract.endDate))
            throw bad("The return time must be in the future and before the contract end date.");
        if (input.notes() != null && input.notes().length() > 2000) throw bad("Notes are too long.");
        if (requests.existsByContractIdAndTypeAndStatusIn(contractId, "EARLY_RETURN", OPEN))
            throw conflict("An early return is already in progress.");
        if (refunds.existsByContractIdAndStatusIn(contractId, List.of("REQUESTED", "UNDER_REVIEW", "APPROVED")))
            throw conflict("Resolve the existing deposit refund request before starting early return.");
        if (handovers.findFirstByContractIdAndHandoverTypeAndStatus(contractId, "RETURN", "SCHEDULED").isPresent())
            throw conflict("A return appointment already exists.");
        TerminationRequest row = new TerminationRequest();
        row.id = UUID.randomUUID(); row.contractId = contractId; row.type = "EARLY_RETURN";
        row.requestedBy = access.currentId(); row.requestedAt = access.now();
        row.scheduledReturnAt = input.proposedReturnAt();
        row.reason = input.reason().trim() + (input.notes() == null || input.notes().isBlank() ? "" : "\nNotes: " + input.notes().trim());
        row.depositAmount = contract.depositAmount; row.deductionAmount = BigDecimal.ZERO;
        row.refundAmount = BigDecimal.ZERO; row.status = "REQUESTED";
        return requests.saveAndFlush(row);
    }

    @Transactional(readOnly = true)
    public List<TerminationRequest> customerList() {
        if (!"CUSTOMER".equals(access.role())) throw forbidden();
        return requests.findByRequestedByAndTypeOrderByRequestedAtDesc(access.currentId(), "EARLY_RETURN");
    }

    @Transactional(readOnly = true)
    public TerminationRequest customerGet(UUID id) {
        if (!"CUSTOMER".equals(access.role())) throw forbidden();
        TerminationRequest row = find(id);
        if (!row.requestedBy.equals(access.currentId())) throw forbidden();
        return row;
    }

    @Transactional(readOnly = true)
    public List<TerminationRequest> managerList() {
        managerRole();
        List<UUID> scope = access.facilities();
        return scope.isEmpty() ? List.of() : requests.forFacilities(scope);
    }

    @Transactional(readOnly = true)
    public SettlementView settlement(UUID id) {
        TerminationRequest row = managerRequest(id, false);
        Optional<EarlyReturnSettlement> existing = settlements.findByTerminationId(id);
        if (existing.isPresent()) return new SettlementView(existing.get().status, existing.get(), null);
        UUID facility = access.facilityId(access.contract(row.contractId, false));
        List<EarlyReturnPolicy> applicable = policies.applicable(facility, row.scheduledReturnAt.toLocalDate());
        if (applicable.isEmpty())
            return new SettlementView("PENDING_POLICY", null, "No approved early-return financial policy is configured for this facility and date.");
        if (applicable.size() > 1)
            return new SettlementView("POLICY_CONFLICT", null, "Overlapping approved policies must be resolved before settlement.");
        if (!inspectionReady(row.contractId))
            return new SettlementView("PENDING_INSPECTION", null, "UC16/UC20 handover and inspection must be completed first.");
        return new SettlementView("READY_FOR_APPROVAL", null, "Manager must finalize inspection deduction with a reason.");
    }

    @Transactional
    public EarlyReturnSettlement approve(UUID id, ApprovalInput input) {
        TerminationRequest row = managerRequest(id, true);
        if (!"REQUESTED".equals(row.status)) throw conflict("This request has already been decided.");
        if (settlements.findByTerminationId(id).isPresent()) throw conflict("A settlement already exists.");
        RentalContract contract = access.contract(row.contractId, true);
        UUID facility = access.facilityId(contract);
        List<EarlyReturnPolicy> applicable = policies.applicable(facility, row.scheduledReturnAt.toLocalDate());
        if (applicable.size() != 1) throw conflict("Exactly one approved early-return policy is required.");
        EarlyReturnPolicy policy = applicable.get(0);
        HandoverRecord handover = handovers.findByContractIdAndHandoverTypeOrderByScheduledAtDesc(row.contractId, "RETURN")
                .stream().filter(h -> "COMPLETED".equals(h.getStatus()) && h.getActualAt() != null)
                .findFirst().orElseThrow(() -> conflict("Physical return has not been completed."));
        InspectionReport inspection = inspections.findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(
                row.contractId, "RETURN_INSPECTION").orElseThrow(() -> conflict("Return inspection is missing."));
        if (inspection.getInspectionDate().isBefore(handover.getActualAt()))
            throw conflict("Inspection predates the physical return.");
        BigDecimal deduction = input == null ? null : input.inspectionDeduction();
        if (deduction == null || deduction.signum() < 0 || deduction.scale() > 2) throw bad("A nonnegative finalized deduction is required.");
        if (deduction.signum() > 0 && (input.deductionReason() == null || input.deductionReason().isBlank()))
            throw bad("A deduction reason is required.");
        // The existing DEPOSIT_REFUNDS.review_note column is NVARCHAR(500) on the live SQL Server schema.
        if (deduction.signum() > 0 && input.deductionReason().trim().length() > 500)
            throw bad("Deduction reason must be at most 500 characters.");
        if (deduction.signum() > 0 && (!policy.inspectionDeductionAllowed
                || deduction.compareTo(inspection.getEstimatedRepairCost()) > 0))
            throw conflict("Deduction is not allowed by policy or exceeds the inspection estimate.");
        BigDecimal collected = successfulDeposits(row.contractId).stream().map(p -> p.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal priorRefunds = refunds.completedTotal(row.contractId);
        BigDecimal depositBalance = collected.subtract(priorRefunds).max(BigDecimal.ZERO).min(contract.depositAmount);
        BigDecimal rentCollected = payments.findByContractIdAndStatus(row.contractId, "SUCCESS").stream()
                .filter(p -> !"REFUND".equals(p.transactionType) && p.paymentType != null
                        && Set.of("INITIAL_RENT", "REMAINING_RENT", "MONTHLY_RENT", "RENEWAL").contains(p.paymentType))
                .map(p -> p.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = invoices.findByContractId(row.contractId).stream()
                .filter(i -> !"CANCELLED".equals(i.status) && !"DEPOSIT".equals(i.invoiceType))
                .map(i -> i.totalAmount.subtract(i.paidAmount).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (outstanding.signum() > 0) throw conflict("Outstanding invoices must be settled before refund approval.");
        BigDecimal refund = policy.depositRefundable ? depositBalance.subtract(deduction).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        EarlyReturnSettlement s = new EarlyReturnSettlement();
        s.id = UUID.randomUUID(); s.terminationId = id; s.contractId = row.contractId;
        s.policyId = policy.id; s.policyVersion = policy.version;
        s.handoverId = handover.getHandoverId(); s.inspectionId = inspection.getInspectionId();
        s.depositContractAmount = contract.depositAmount; s.depositCollected = collected;
        s.priorDepositRefunds = priorRefunds; s.rentalCollected = rentCollected;
        s.unusedRentRefund = BigDecimal.ZERO; s.outstandingReceivables = outstanding;
        s.inspectionDeduction = deduction; s.deductionReason = deduction.signum() == 0 ? null : input.deductionReason().trim();
        s.depositRefundAmount = refund; s.remainingPayable = BigDecimal.ZERO;
        s.status = "APPROVED"; s.calculatedAt = access.now(); s.approvedAt = s.calculatedAt; s.approvedBy = access.currentId();
        row.deductionAmount = deduction; row.refundAmount = refund; row.approvedBy = access.currentId();
        row.status = "SETTLEMENT_APPROVED";
        requests.save(row);
        return settlements.saveAndFlush(s);
    }

    @Transactional
    public TerminationRequest reject(UUID id, RejectInput input) {
        TerminationRequest row = managerRequest(id, true);
        if (!"REQUESTED".equals(row.status)) throw conflict("This request has already been decided.");
        if (input == null || input.reason() == null || input.reason().isBlank()) throw bad("A rejection reason is required.");
        row.status = "REJECTED"; row.reason += "\nRejection: " + input.reason().trim();
        row.approvedBy = access.currentId(); row.completedAt = access.now();
        return requests.saveAndFlush(row);
    }

    @Transactional
    public EarlyReturnSettlement refund(UUID id) {
        TerminationRequest row = managerRequest(id, true);
        access.contract(row.contractId, true); // serializes concurrent refund attempts per contract
        EarlyReturnSettlement s = settlements.findByTerminationId(id)
                .orElseThrow(() -> conflict("Approved settlement is missing."));
        if ("REFUNDED".equals(s.status)) return s;
        if (!"SETTLEMENT_APPROVED".equals(row.status) || !"APPROVED".equals(s.status))
            throw conflict("Settlement is not approved.");
        if (!inspectionReady(row.contractId)) throw conflict("Physical return or inspection is missing.");
        if (s.depositRefundAmount.signum() <= 0) throw conflict("Approved deposit refund is zero.");
        List<PaymentTransaction> deposits = successfulDeposits(row.contractId);
        BigDecimal available = deposits.stream().map(p -> p.amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(refunds.completedTotal(row.contractId));
        if (deposits.isEmpty() || s.depositRefundAmount.compareTo(available) > 0)
            throw conflict("Refund exceeds the remaining collected deposit.");
        PaymentTransaction source = deposits.get(0);
        String reference = "UC19-" + s.id;
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.transactionId = UUID.randomUUID(); transaction.invoiceId = source.invoiceId;
        transaction.contractId = row.contractId; transaction.transactionType = "REFUND";
        transaction.paymentType = "DEPOSIT"; transaction.paymentMethod = "INTERNAL";
        transaction.amount = s.depositRefundAmount; transaction.transactionDate = access.now();
        transaction.referenceCode = reference; transaction.status = "SUCCESS";
        transaction.createdBy = access.currentId(); transaction.idempotencyKey = reference;
        transaction.createdAt = transaction.transactionDate; transaction.updatedAt = transaction.transactionDate;
        transaction = payments.saveAndFlush(transaction);
        DepositRefund refund = new DepositRefund();
        refund.id = UUID.randomUUID(); refund.contractId = row.contractId; refund.customerId = row.requestedBy;
        refund.facilityId = access.facilityId(access.contract(row.contractId, false));
        refund.paymentReference = source.transactionId; refund.requestedAmount = s.depositRefundAmount;
        refund.approvedAmount = s.depositRefundAmount; refund.reason = "UC19 early return settlement " + s.id;
        refund.status = "COMPLETED"; refund.reviewerId = s.approvedBy;
        refund.reviewNote = s.deductionReason; refund.refundTransactionReference = reference;
        refund.createdAt = s.approvedAt; refund.updatedAt = access.now(); refund.completedAt = refund.updatedAt;
        refunds.saveAndFlush(refund);
        s.refundId = refund.id; s.refundTransactionId = transaction.transactionId; s.status = "REFUNDED";
        row.status = "REFUNDED"; row.completedAt = access.now(); requests.save(row);
        return settlements.saveAndFlush(s);
    }

    private List<PaymentTransaction> successfulDeposits(UUID contractId) {
        return payments.successful(contractId, "DEPOSIT").stream()
                .filter(p -> !"REFUND".equals(p.transactionType)).toList();
    }
    private boolean inspectionReady(UUID contractId) {
        return handovers.findByContractIdAndHandoverTypeOrderByScheduledAtDesc(contractId, "RETURN").stream()
                .anyMatch(h -> "COMPLETED".equals(h.getStatus()) && h.getActualAt() != null)
                && inspections.findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(contractId, "RETURN_INSPECTION").isPresent();
    }
    private TerminationRequest find(UUID id) {
        TerminationRequest row = requests.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Early return not found."));
        if (!"EARLY_RETURN".equals(row.type)) throw new BusinessException(HttpStatus.NOT_FOUND, "Early return not found.");
        return row;
    }
    private TerminationRequest managerRequest(UUID id, boolean lock) {
        managerRole();
        TerminationRequest row = lock ? requests.lockById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Early return not found.")) : find(id);
        if (!"EARLY_RETURN".equals(row.type)) throw new BusinessException(HttpStatus.NOT_FOUND, "Early return not found.");
        RentalContract contract = access.contract(row.contractId, lock);
        if (!access.facilities().contains(access.facilityId(contract))) throw forbidden();
        return row;
    }
    private void managerRole() { if (!"FACILITY_MANAGER".equals(access.role())) throw forbidden(); }
    private BusinessException forbidden() { return new BusinessException(HttpStatus.FORBIDDEN, "Access denied."); }
    private BusinessException bad(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, message); }
    private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, message); }
}
