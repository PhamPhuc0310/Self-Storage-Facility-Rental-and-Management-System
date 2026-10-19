package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.PaymentRequest;
import com.safebox.self_storage.entity.Invoice;
import com.safebox.self_storage.entity.PaymentTransaction;
import com.safebox.self_storage.entity.RentalContract;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.DepositRefundRepository;
import com.safebox.self_storage.repository.InvoiceRepository;
import com.safebox.self_storage.repository.PaymentTransactionRepository;
import com.safebox.self_storage.repository.PickupAppointmentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_probe;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaymentPersistenceTest {
    @Autowired InvoiceRepository invoices;
    @Autowired PaymentTransactionRepository transactions;

    @Test void newV2DepositPersistsAndSummaryReflectsIt() {
        UUID id = UUID.randomUUID(), customerId = UUID.randomUUID();
        RentalContract contract = new RentalContract();
        contract.contractId = id; contract.customerId = customerId;
        contract.status = "CONFIRMED"; contract.termsVersion = "v2";
        contract.startDate = LocalDate.of(2026, 10, 19);
        contract.endDate = LocalDate.of(2027, 1, 19);
        contract.monthlyRentalFee = new BigDecimal("2160000");
        contract.depositAmount = new BigDecimal("2376000");
        Invoice deposit = new Invoice();
        deposit.invoiceNumber = "INV-TEST-" + UUID.randomUUID().toString().substring(0, 8);
        deposit.contractId = id; deposit.invoiceType = "DEPOSIT";
        deposit.description = "Tiền cọc";
        deposit.subtotal = contract.depositAmount;
        deposit.totalAmount = contract.depositAmount;
        deposit.penaltyAmount = BigDecimal.ZERO;
        deposit.discountAmount = BigDecimal.ZERO;
        deposit.paidAmount = BigDecimal.ZERO;
        deposit.issuedAt = LocalDateTime.of(2026, 10, 19, 8, 0);
        deposit.dueDate = deposit.issuedAt;
        deposit.status = "UNPAID";
        invoices.saveAndFlush(deposit);

        Sprint2Access access = mock(Sprint2Access.class);
        PickupAppointmentRepository appointments = mock(PickupAppointmentRepository.class);
        DepositRefundRepository refunds = mock(DepositRefundRepository.class);
        when(access.contract(id, true)).thenReturn(contract);
        when(access.contract(id, false)).thenReturn(contract);
        when(access.role()).thenReturn("CUSTOMER");
        when(access.currentId()).thenReturn(customerId);
        when(access.today()).thenReturn(contract.startDate);
        when(access.now()).thenReturn(deposit.issuedAt);
        PaymentService service = new PaymentService(access, transactions, invoices, appointments, refunds);

        PaymentTransaction payment = service.pay(id, new PaymentRequest(
                "DEPOSIT", contract.depositAmount, "BANK_TRANSFER", "isolated-first-deposit"));
        transactions.flush();
        assertEquals(1, transactions.findByContractId(id).size(),
                "One request must persist exactly one payment transaction");
        assertEquals("SUCCESS", payment.status);
        assertEquals(id, payment.contractId);
        assertEquals(deposit.invoiceId, payment.invoiceId);
        assertTrue(payment.referenceCode.endsWith(payment.transactionId.toString().replace("-", "").toUpperCase()));
        assertEquals("PAID", invoices.findById(deposit.invoiceId).orElseThrow().status);
        assertEquals(0, service.summary(id).deposit().remainingAmount().signum());

        PaymentTransaction retry = service.pay(id, new PaymentRequest(
                "DEPOSIT", contract.depositAmount, "BANK_TRANSFER", "isolated-first-deposit"));
        assertEquals(payment.transactionId, retry.transactionId);
        assertThrows(BusinessException.class, () -> service.pay(id, new PaymentRequest(
                "DEPOSIT", contract.depositAmount, "BANK_TRANSFER", "different-request")));
        assertEquals(1, transactions.findByContractId(id).size());
    }
}
