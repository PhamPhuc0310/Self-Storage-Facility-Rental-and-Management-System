package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.payment.InvoiceResponse;
import com.safebox.self_storage.dto.payment.PayInvoiceRequest;
import com.safebox.self_storage.dto.payment.PayInvoiceResponse;
import com.safebox.self_storage.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller xử lý các nghiệp vụ hóa đơn và thanh toán dành cho Khách hàng (ROLE_CUSTOMER):
 * - Thanh toán tiền đặt cọc và tiền thuê ban đầu của hợp đồng
 * - Tra cứu hóa đơn và lịch sử giao dịch
 */
@RestController
@RequestMapping("/api")
public class CustomerPaymentController {

    private final PaymentService paymentService;

    public CustomerPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Lấy hoặc tự động khởi tạo hóa đơn thanh toán tiền cọc và tiền thuê ban đầu của hợp đồng.
     *
     * Endpoint: GET /api/contracts/my/{contractId}/initial-invoice
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param customerId UUID của khách hàng từ JWT token
     * @param contractId UUID của hợp đồng
     * @return Hóa đơn thanh toán ban đầu (cọc + tiền thuê)
     */
    @GetMapping("/contracts/my/{contractId}/initial-invoice")
    public ResponseEntity<InvoiceResponse> getInitialInvoice(
            @AuthenticationPrincipal UUID customerId,
            @PathVariable UUID contractId) {
        return ResponseEntity.ok(paymentService.getOrCreateInitialInvoice(customerId, contractId));
    }

    /**
     * Lấy danh sách hóa đơn thuộc hợp đồng của khách hàng.
     *
     * Endpoint: GET /api/contracts/my/{contractId}/invoices
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param customerId UUID của khách hàng từ JWT token
     * @param contractId UUID của hợp đồng
     * @return Danh sách hóa đơn
     */
    @GetMapping("/contracts/my/{contractId}/invoices")
    public ResponseEntity<List<InvoiceResponse>> getContractInvoices(
            @AuthenticationPrincipal UUID customerId,
            @PathVariable UUID contractId) {
        return ResponseEntity.ok(paymentService.getContractInvoices(customerId, contractId));
    }

    /**
     * Khách hàng thanh toán tiền đặt cọc và tiền thuê ban đầu cho hợp đồng.
     * Hỗ trợ các phương thức: BANK_TRANSFER, VNPAY, MOMO, CASH, CREDIT_CARD.
     *
     * Endpoint: POST /api/contracts/my/{contractId}/pay-initial
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param customerId UUID của khách hàng từ JWT token
     * @param contractId UUID của hợp đồng
     * @param request    DTO chứa phương thức thanh toán và mã tham chiếu
     * @return Kết quả thanh toán và biên lai giao dịch
     */
    @PostMapping("/contracts/my/{contractId}/pay-initial")
    public ResponseEntity<PayInvoiceResponse> payInitialInvoice(
            @AuthenticationPrincipal UUID customerId,
            @PathVariable UUID contractId,
            @Valid @RequestBody PayInvoiceRequest request) {
        return ResponseEntity.ok(paymentService.payInitialInvoice(customerId, contractId, request));
    }

    /**
     * Xem chi tiết hóa đơn theo mã hóa đơn.
     *
     * Endpoint: GET /api/invoices/my/{invoiceId}
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param customerId UUID của khách hàng từ JWT token
     * @param invoiceId  UUID của hóa đơn
     * @return Chi tiết hóa đơn và lịch sử giao dịch
     */
    @GetMapping("/invoices/my/{invoiceId}")
    public ResponseEntity<InvoiceResponse> getInvoiceDetail(
            @AuthenticationPrincipal UUID customerId,
            @PathVariable UUID invoiceId) {
        return ResponseEntity.ok(paymentService.getInvoiceDetail(customerId, invoiceId));
    }

    /**
     * Thanh toán cho một hóa đơn cụ thể.
     *
     * Endpoint: POST /api/invoices/my/{invoiceId}/pay
     * Quyền truy cập: Khách hàng (ROLE_CUSTOMER)
     *
     * @param customerId UUID của khách hàng từ JWT token
     * @param invoiceId  UUID của hóa đơn
     * @param request    DTO chứa phương thức thanh toán
     * @return Kết quả thanh toán
     */
    @PostMapping("/invoices/my/{invoiceId}/pay")
    public ResponseEntity<PayInvoiceResponse> payInvoice(
            @AuthenticationPrincipal UUID customerId,
            @PathVariable UUID invoiceId,
            @Valid @RequestBody PayInvoiceRequest request) {
        return ResponseEntity.ok(paymentService.payInvoice(customerId, invoiceId, request));
    }
}
