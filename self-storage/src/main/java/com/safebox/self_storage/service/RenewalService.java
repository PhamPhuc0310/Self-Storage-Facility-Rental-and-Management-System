package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.renewal.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Interface định nghĩa các nghiệp vụ Use Case UC17: Yêu cầu gia hạn hợp đồng thuê kho
 * (Contract Renewal Management)
 *
 * Các quy tắc nghiệp vụ (Business Rules):
 * - BR17.1: Chỉ CUSTOMER sở hữu hợp đồng ACTIVE mới yêu cầu gia hạn.
 * - BR17.2: Ngày bắt đầu kỳ gia hạn nối tiếp ngày kết thúc hiện tại; không ghi đè lịch sử kỳ cũ.
 * - BR17.3: Phải kiểm tra khả dụng/điều kiện cơ sở và các yêu cầu trả kho đang mở trước khi duyệt.
 * - BR17.4: Giá, VAT, ưu đãi (nếu có) được chốt thành snapshot của lần gia hạn.
 * - BR17.5: Chỉ gia hạn hiệu lực sau khi khoản thanh toán được xác nhận; retry không tạo gia hạn trùng.
 */
public interface RenewalService {

    /**
     * Tính toán báo giá xem trước cho kỳ gia hạn dự kiến (BR17.2, BR17.4).
     *
     * @param customerId UUID khách hàng đăng nhập
     * @param contractId UUID hợp đồng thuê
     * @param months     Số tháng gia hạn dự kiến
     * @return Báo giá chi tiết xem trước
     */
    RenewalPreviewResponse previewRenewal(UUID customerId, UUID contractId, Integer months);

    /**
     * Khách hàng gửi yêu cầu gia hạn hợp đồng thuê (BR17.1, BR17.2, BR17.4).
     *
     * @param customerId UUID khách hàng đăng nhập
     * @param contractId UUID hợp đồng thuê
     * @param request    Thông tin yêu cầu gia hạn (số tháng, ghi chú, mã ưu đãi)
     * @return Thông tin yêu cầu gia hạn đã khởi tạo
     */
    RenewalResponse requestRenewal(UUID customerId, UUID contractId, CreateRenewalRequest request);

    /**
     * Quản lý cơ sở xét duyệt hoặc từ chối yêu cầu gia hạn hợp đồng (BR17.3).
     *
     * @param managerUserId UUID quản lý cơ sở đăng nhập
     * @param renewalId     UUID yêu cầu gia hạn
     * @param request       Hành động (APPROVE / REJECT) và lý do nếu từ chối
     * @return Thông tin yêu cầu gia hạn sau khi xử lý
     */
    RenewalResponse reviewRenewal(UUID managerUserId, UUID renewalId, ReviewRenewalRequest request);

    /**
     * Khách hàng thanh toán phí gia hạn hợp đồng (BR17.5).
     * Khi thanh toán thành công, kỳ hạn hợp đồng mới chính thức có hiệu lực (cập nhật end_date).
     * Áp dụng Idempotency để retry không tạo giao dịch hoặc gia hạn trùng.
     *
     * @param customerId UUID khách hàng đăng nhập
     * @param renewalId  UUID yêu cầu gia hạn
     * @param request    Phương thức thanh toán, số tiền, idempotency key
     * @return Kết quả giao dịch thanh toán và ngày kết thúc mới của hợp đồng
     */
    RenewalPaymentResponse payRenewal(UUID customerId, UUID renewalId, PayRenewalRequest request);

    /**
     * Khách hàng tự hủy yêu cầu gia hạn khi còn ở trạng thái PENDING hoặc APPROVED chưa thanh toán.
     *
     * @param customerId UUID khách hàng đăng nhập
     * @param renewalId  UUID yêu cầu gia hạn
     * @return Thông tin yêu cầu gia hạn sau khi hủy
     */
    RenewalResponse cancelRenewal(UUID customerId, UUID renewalId);

    /**
     * Xem thông tin chi tiết của một yêu cầu gia hạn.
     *
     * @param currentUserId UUID người dùng đăng nhập
     * @param renewalId     UUID yêu cầu gia hạn
     * @return Chi tiết yêu cầu gia hạn
     */
    RenewalResponse getRenewalDetail(UUID currentUserId, UUID renewalId);

    /**
     * Lấy toàn bộ lịch sử các lần gia hạn của một hợp đồng (BR17.2: không ghi đè lịch sử).
     *
     * @param currentUserId UUID người dùng đăng nhập
     * @param contractId    UUID hợp đồng thuê
     * @return Danh sách các yêu cầu gia hạn của hợp đồng theo thứ tự thời gian
     */
    List<RenewalResponse> getContractRenewals(UUID currentUserId, UUID contractId);

    /**
     * Khách hàng lấy danh sách yêu cầu gia hạn của mình có phân trang và lọc theo trạng thái.
     *
     * @param customerId UUID khách hàng đăng nhập
     * @param status     Bộ lọc trạng thái (PENDING, APPROVED, COMPLETED, v.v.)
     * @param pageable   Thông tin phân trang
     * @return Trang danh sách yêu cầu gia hạn
     */
    Page<RenewalResponse> getCustomerRenewals(UUID customerId, String status, Pageable pageable);

    /**
     * Quản lý cơ sở lấy danh sách yêu cầu gia hạn tại cơ sở phụ trách.
     *
     * @param managerUserId UUID quản lý cơ sở đăng nhập
     * @param facilityId    UUID cơ sở (tùy chọn)
     * @param status        Bộ lọc trạng thái
     * @param pageable      Thông tin phân trang
     * @return Trang danh sách yêu cầu gia hạn cần xử lý
     */
    Page<RenewalResponse> getFacilityRenewals(UUID managerUserId, UUID facilityId, String status, Pageable pageable);
}
