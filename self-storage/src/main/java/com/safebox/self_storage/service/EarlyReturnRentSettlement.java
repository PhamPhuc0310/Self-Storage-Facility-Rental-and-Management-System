package com.safebox.self_storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Customer-initiated, versioned rent refund. Called inside the locked settlement transaction. */
@Service
public class EarlyReturnRentSettlement {
    private final InvoiceRepository invoices;
    private final PaymentTransactionRepository payments;
    private final RenewalRepository renewals;
    private final DepositRefundRepository refunds;
    private final Sprint2Access access;
    private final ObjectMapper json;
    public EarlyReturnRentSettlement(InvoiceRepository invoices, PaymentTransactionRepository payments,
            RenewalRepository renewals, DepositRefundRepository refunds, Sprint2Access access, ObjectMapper json) {
        this.invoices=invoices;this.payments=payments;this.renewals=renewals;this.refunds=refunds;this.access=access;this.json=json;
    }
    public record RentLine(UUID invoiceId, LocalDate start, LocalDate end, BigDecimal confirmedRent,
            long totalDays, long unusedDays, BigDecimal unusedRent, BigDecimal rentalRefund, BigDecimal retainedRent) {}
    public record DebtLine(UUID invoiceId, BigDecimal totalBefore, BigDecimal paidBefore, BigDecimal offset) {}
    public record RefundLine(UUID invoiceId, String paymentType, BigDecimal amount) {}
    public record Calculation(LocalDate returnDate, boolean estimated, BigDecimal percent,
            BigDecimal eligiblePrepaidRent, BigDecimal unusedEligibleRent, BigDecimal rentalRefund,
            BigDecimal retainedRent, BigDecimal depositBalance, BigDecimal documentedDeductions,
            BigDecimal outstandingDebt, BigDecimal liabilityOffset, BigDecimal netRefund, BigDecimal remainingDebt,
            List<RentLine> rentPeriods, List<DebtLine> debts, List<RefundLine> refundLines) {}
    public record Confirmation(String reference, String paymentMethod, BigDecimal amount) {}
    private static final Set<String> RENT = Set.of("INITIAL_RENT","REMAINING_RENT","MONTHLY_RENT","RENEWAL");
    private static BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}
    private static BigDecimal sum(List<BigDecimal> values){return values.stream().reduce(zero(),BigDecimal::add);}
    private BusinessException conflict(String text){return new BusinessException(HttpStatus.CONFLICT,text);}
    public void requireNoPendingRenewal(UUID contract) {
        if(!renewals.findByContractIdAndStatusIn(contract,List.of("PENDING","APPROVED")).isEmpty())
            throw conflict("Cần xử lý yêu cầu gia hạn đang chờ trước khi trả kho trước hạn; không tự bù trừ tiền gia hạn chưa có hiệu lực.");
    }
    public static RentLine prorate(UUID invoice, LocalDate start, LocalDate end, BigDecimal paid,
            LocalDate returned, BigDecimal percent) {
        if(start==null||end==null||end.isBefore(start)||paid==null||paid.signum()<0
                ||percent==null||percent.compareTo(new BigDecimal("80"))!=0)
            throw new IllegalArgumentException("Kỳ thuê hoặc chính sách hoàn tiền không hợp lệ.");
        // Calendar end dates are inclusive; the completed handover day is a used day.
        long total=ChronoUnit.DAYS.between(start,end)+1;
        LocalDate firstUnused=returned.plusDays(1).isBefore(start)?start:returned.plusDays(1);
        long unused=Math.max(0,ChronoUnit.DAYS.between(firstUnused,end)+1);
        BigDecimal eligible=paid.multiply(BigDecimal.valueOf(unused)).divide(BigDecimal.valueOf(total),12,RoundingMode.HALF_UP);
        BigDecimal rental=eligible.multiply(percent).movePointLeft(2).setScale(2,RoundingMode.HALF_UP);
        BigDecimal rounded=eligible.setScale(2,RoundingMode.HALF_UP);
        return new RentLine(invoice,start,end,paid,total,unused,rounded,rental,rounded.subtract(rental));
    }
    public Calculation calculate(RentalContract c, LocalDate returned, boolean estimated, EarlyReturnPolicy policy,
            BigDecimal documentedDeductions, LocalDateTime confirmedBefore) {
        requireNoPendingRenewal(c.contractId);
        List<Invoice> all=invoices.findByContractId(c.contractId).stream()
                .filter(i->!"CANCELLED".equals(i.status)).sorted(Comparator.comparing(i->i.invoiceId)).toList();
        List<PaymentTransaction> successful=payments.findByContractIdAndStatus(c.contractId,"SUCCESS");
        List<Renewal> completed=renewals.findByContractIdAndStatus(c.contractId,"COMPLETED");
        LocalDate originalEnd=completed.stream().map(Renewal::getOldEndDate).min(LocalDate::compareTo).orElse(c.endDate);
        List<RentLine> periods=new ArrayList<>(); List<RefundLine> deposits=new ArrayList<>();
        BigDecimal deposit=zero();
        for(Invoice i:all) {
            BigDecimal confirmed=successful.stream().filter(p->i.invoiceId.equals(p.invoiceId)
                    && p.confirmedBy!=null&&p.confirmedAt!=null && p.paymentType!=null
                    && (!RENT.contains(i.invoiceType)||"REFUND".equals(p.transactionType)||!p.confirmedAt.isAfter(confirmedBefore))
                    && (RENT.contains(i.invoiceType)?RENT.contains(p.paymentType):"DEPOSIT".equals(p.paymentType)))
                    .map(p->"REFUND".equals(p.transactionType)?p.amount.negate():p.amount).reduce(zero(),BigDecimal::add)
                    .max(zero()).min(i.paidAmount).min(i.totalAmount);
            if("DEPOSIT".equals(i.invoiceType)) {
                BigDecimal available=confirmed.min(c.depositAmount.subtract(deposit).max(zero()));
                deposits.add(new RefundLine(i.invoiceId,"DEPOSIT",available));deposit=deposit.add(available);continue;
            }
            if(!RENT.contains(i.invoiceType)||confirmed.signum()==0)continue;
            if(!Set.of("PAID","PARTIALLY_PAID").contains(i.status))continue;
            LocalDate start=c.startDate,end=originalEnd;
            if("RENEWAL".equals(i.invoiceType)) {
                Renewal renewal=completed.stream().filter(r->r.getRenewalId().equals(i.renewalId)).findFirst()
                        .orElseThrow(()->conflict("Hóa đơn gia hạn chưa có kỳ thuê đã xác nhận."));
                start=renewal.getOldEndDate().plusDays(1);end=renewal.getNewEndDate();
            } else if("MONTHLY_RENT".equals(i.invoiceType)||!"v2".equals(c.termsVersion)) {
                throw conflict("Cần đối soát kỳ tiền thuê của hóa đơn cũ; không tự suy đoán số ngày được hoàn.");
            }
            periods.add(prorate(i.invoiceId,start,end,confirmed,returned,policy.rentalRefundPercent));
        }
        // Existing completed deposit refunds without a transaction confirmation are also authoritative.
        BigDecimal loggedDepositRefund=successful.stream().filter(p->"DEPOSIT".equals(p.paymentType)&&"REFUND".equals(p.transactionType)
                &&p.confirmedAt!=null&&p.confirmedBy!=null).map(p->p.amount).reduce(zero(),BigDecimal::add);
        BigDecimal unloggedRefund=refunds.completedTotal(c.contractId).subtract(loggedDepositRefund).max(zero());
        deposit=deposit.subtract(unloggedRefund).max(zero());
        BigDecimal depositRefund=policy.depositRefundable?deposit:zero();
        BigDecimal rent=sum(periods.stream().map(RentLine::rentalRefund).toList());
        BigDecimal debt=sum(all.stream().filter(i->!"DEPOSIT".equals(i.invoiceType))
                .map(i->i.totalAmount.subtract(i.paidAmount).max(zero())).toList());
        BigDecimal offset=depositRefund.add(rent).min(debt),leftOffset=offset;
        List<DebtLine> debts=new ArrayList<>();
        for(Invoice i:all)if(!"DEPOSIT".equals(i.invoiceType)) {
            BigDecimal allocation=i.totalAmount.subtract(i.paidAmount).max(zero()).min(leftOffset);
            if(allocation.signum()>0)debts.add(new DebtLine(i.invoiceId,i.totalAmount,i.paidAmount,allocation));
            leftOffset=leftOffset.subtract(allocation);
        }
        BigDecimal depositNet=depositRefund.subtract(offset).max(zero());
        BigDecimal rentOffset=offset.subtract(depositRefund).max(zero());
        List<RefundLine> lines=new ArrayList<>();BigDecimal depositLeft=depositNet;
        for(RefundLine d:deposits){BigDecimal amount=d.amount.min(depositLeft);if(amount.signum()>0)lines.add(new RefundLine(d.invoiceId,d.paymentType,amount));depositLeft=depositLeft.subtract(amount);}
        for(RentLine p:periods){BigDecimal used=p.rentalRefund.min(rentOffset);BigDecimal amount=p.rentalRefund.subtract(used);rentOffset=rentOffset.subtract(used);if(amount.signum()>0)lines.add(new RefundLine(p.invoiceId,"RENT_REFUND",amount));}
        return new Calculation(returned,estimated,policy.rentalRefundPercent,
                sum(periods.stream().map(RentLine::confirmedRent).toList()),sum(periods.stream().map(RentLine::unusedRent).toList()),
                rent,sum(periods.stream().map(RentLine::retainedRent).toList()),deposit,documentedDeductions,
                debt,offset,sum(lines.stream().map(RefundLine::amount).toList()),debt.subtract(offset),periods,debts,lines);
    }
    public String snapshot(Calculation c){try{return json.writeValueAsString(c);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    public Calculation read(EarlyReturnSettlement s){try{return json.readValue(s.calculationSnapshot,Calculation.class);}catch(JsonProcessingException e){throw new IllegalStateException("Invalid settlement snapshot",e);}}
    public void addInspectionCharge(RentalContract c,UUID settlement,BigDecimal amount,String reason) {
        if(amount.signum()==0)return;
        if(invoices.findByContractId(c.contractId).stream().anyMatch(i->!"CANCELLED".equals(i.status)
                && !"DEPOSIT".equals(i.invoiceType)&&!RENT.contains(i.invoiceType)))
            throw conflict("Đã có hóa đơn phí khác; cần đối soát trên hóa đơn, không khấu trừ kiểm tra thêm lần nữa.");
        Invoice i=new Invoice();i.invoiceNumber="INV-ER-"+settlement;i.contractId=c.contractId;i.invoiceType="OTHER";
        i.description="Quyết toán trả kho "+settlement+": "+reason;i.subtotal=amount;i.totalAmount=amount;
        i.penaltyAmount=zero();i.discountAmount=zero();i.paidAmount=zero();i.status="UNPAID";
        i.issuedAt=access.now();i.dueDate=i.issuedAt;invoices.saveAndFlush(i);
    }
    public EarlyReturnSettlement complete(RentalContract c,TerminationRequest row,EarlyReturnSettlement s,Confirmation input) {
        Calculation calc=read(s);
        if(input==null||input.reference()==null||!input.reference().matches("[A-Za-z0-9_-]{4,60}")
                ||input.paymentMethod()==null||!Set.of("CASH","BANK_TRANSFER").contains(input.paymentMethod())||input.amount()==null
                ||input.amount().compareTo(calc.netRefund)!=0)
            throw new BusinessException(HttpStatus.BAD_REQUEST,"Cần mã chứng từ, phương thức và đúng số tiền đã hoàn; quyết toán không tự chuyển tiền.");
        if(s.refundConfirmedAt!=null) {
            if(!input.reference().equals(s.refundConfirmationReference)||!input.paymentMethod().equals(s.refundConfirmationMethod))throw conflict("Chứng từ khác lần quyết toán đã hoàn tất.");
            return s;
        }
        if(!"APPROVED".equals(s.status))throw conflict("Quyết toán chưa được duyệt.");
        if(refunds.completedTotal(c.contractId).compareTo(s.priorDepositRefunds)!=0)
            throw conflict("Số dư cọc đã thay đổi sau duyệt; cần đối soát lại.");
        for(DebtLine d:calc.debts) {
            Invoice i=invoices.findById(d.invoiceId).orElseThrow();
            if(i.totalAmount.compareTo(d.totalBefore)!=0||i.paidAmount.compareTo(d.paidBefore)!=0||"CANCELLED".equals(i.status))
                throw conflict("Hóa đơn đã thay đổi sau duyệt; cần đối soát lại quyết toán.");
        }
        // Offsets settle existing liabilities without inventing cash collection transactions.
        for(DebtLine d:calc.debts) {
            Invoice i=invoices.findById(d.invoiceId).orElseThrow();i.paidAmount=i.paidAmount.add(d.offset);
            i.status=i.paidAmount.compareTo(i.totalAmount)>=0?"PAID":"PARTIALLY_PAID";invoices.save(i);
        }
        int n=0;
        for(RefundLine line:calc.refundLines) {
            String reference=input.reference()+"-"+(++n);
            if(payments.findByReferenceCode(reference).isPresent())throw conflict("Chứng từ hoàn tiền đã được sử dụng.");
            PaymentTransaction p=new PaymentTransaction();p.invoiceId=line.invoiceId;p.contractId=c.contractId;
            p.transactionType="REFUND";p.paymentType="RENT_REFUND".equals(line.paymentType)?"REMAINING_RENT":"DEPOSIT";
            p.amount=line.amount;p.paymentMethod=input.paymentMethod();p.referenceCode=reference;
            p.idempotencyKey="ER80-"+s.id+"-"+n;p.status="SUCCESS";p.transactionDate=access.now();
            p.createdAt=p.transactionDate;p.updatedAt=p.transactionDate;p.createdBy=access.currentId();
            p.confirmedBy=access.currentId();p.confirmedAt=p.transactionDate;p=payments.saveAndFlush(p);
            if("DEPOSIT".equals(p.paymentType)) {
                DepositRefund r=new DepositRefund();r.id=UUID.randomUUID();r.contractId=c.contractId;r.customerId=c.customerId;
                r.facilityId=access.facilityId(c);r.requestedAmount=p.amount;r.approvedAmount=p.amount;
                r.paymentReference=payments.findByInvoiceId(line.invoiceId).stream().filter(x->!"REFUND".equals(x.transactionType)
                        &&"SUCCESS".equals(x.status)&&x.confirmedAt!=null).map(x->x.transactionId).findFirst().orElseThrow();
                r.reason="Quyết toán "+s.id;r.status="COMPLETED";r.reviewerId=s.approvedBy;
                r.refundTransactionReference=reference;r.createdAt=s.approvedAt;r.updatedAt=p.transactionDate;r.completedAt=p.transactionDate;
                refunds.saveAndFlush(r);s.refundId=r.id;s.refundTransactionId=p.transactionId;
            }
        }
        s.refundConfirmationReference=input.reference();s.refundConfirmationMethod=input.paymentMethod();s.refundConfirmedBy=access.currentId();s.refundConfirmedAt=access.now();
        s.status="COMPLETED";row.status="REFUNDED";row.completedAt=s.refundConfirmedAt;return s;
    }
}
