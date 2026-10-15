package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.payment.InvoiceResponse;
import com.safebox.self_storage.dto.payment.PayInvoiceRequest;
import com.safebox.self_storage.dto.payment.PayInvoiceResponse;

import java.util.List;
import java.util.UUID;

/**
 * Interface nghiệp vụ xử lý hóa đơn và thanh toán (Invoices & Payments):
 * - Thanh toán tiền đặt cọc và tiền thuê ban đầu
 * - Tra cứu hóa đơn và lịch sử giao dịch của hợp đồng
 */
public interface InvoicePaymentService {

    /**
     * Lấy hoặc tự động khởi tạo hóa đơn thanh toán ban đầu (tiền cọc + tiền thuê) cho hợp đồng.
     *
     * @param customerId UUID của khách hàng từ JWT
     * @param contractId UUID của hợp đồng thuê
     * @return DTO thông tin hóa đơn ban đầu
     */
    InvoiceResponse getOrCreateInitialInvoice(UUID customerId, UUID contractId);

    /**
     * Lấy danh sách tất cả hóa đơn thuộc một hợp đồng của khách hàng.
     *
     * @param customerId UUID của khách hàng từ JWT
     * @param contractId UUID của hợp đồng
     * @return Danh sách hóa đơn
     */
    List<InvoiceResponse> getContractInvoices(UUID customerId, UUID contractId);

    /**
     * Lấy chi tiết một hóa đơn theo mã hóa đơn.
     *
     * @param customerId UUID của khách hàng từ JWT
     * @param invoiceId  UUID của hóa đơn
     * @return DTO chi tiết hóa đơn và các giao dịch thanh toán
     */
    InvoiceResponse getInvoiceDetail(UUID customerId, UUID invoiceId);

    /**
     * Khách hàng thực hiện thanh toán tiền đặt cọc và tiền thuê ban đầu cho hợp đồng.
     *
     * @param customerId UUID của khách hàng từ JWT
     * @param contractId UUID của hợp đồng thuê
     * @param request    DTO chứa phương thức thanh toán và mã tham chiếu
     * @return Kết quả thanh toán và biên lai
     */
    PayInvoiceResponse payInitialInvoice(UUID customerId, UUID contractId, PayInvoiceRequest request);

    /**
     * Thanh toán cho một hóa đơn cụ thể của khách hàng.
     *
     * @param customerId UUID của khách hàng từ JWT
     * @param invoiceId  UUID của hóa đơn
     * @param request    DTO chứa phương thức thanh toán
     * @return Kết quả thanh toán và biên lai
     */
    PayInvoiceResponse payInvoice(UUID customerId, UUID invoiceId, PayInvoiceRequest request);
}
