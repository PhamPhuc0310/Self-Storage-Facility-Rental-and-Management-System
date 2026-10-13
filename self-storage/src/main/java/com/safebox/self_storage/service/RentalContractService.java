package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.response.RentalContractDetailResponse;
import com.safebox.self_storage.dto.response.RentalContractListItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Interface định nghĩa các nghiệp vụ xem và quản lý kho đang thuê (UC15):
 * - BR15.1: Khách hàng chỉ xem kho/hợp đồng của mình. Quản lý cơ sở xem kho tại cơ sở được phân công.
 * - BR15.2: Hỗ trợ một khách thuê nhiều kho, mỗi dòng gắn một hợp đồng và mã kho vật lý.
 * - BR15.3: Cung cấp đầy đủ thông tin cơ sở, địa chỉ, mã kho, loại kho, kích thước, thời gian thuê, trạng thái, thanh toán.
 * - BR15.4: Lọc theo nhóm trạng thái (Tất cả, Chờ bàn giao, Đang thuê, Đã kết thúc) và phân trang.
 * - BR15.5: Xem chi tiết hợp đồng, các khoản đã thu / còn thiếu, biên bản bàn giao và lịch sử thanh toán.
 * - BR15.6: Tính số ngày còn lại theo Asia/Ho_Chi_Minh; hiển thị quá hạn.
 * - BR15.7: Đảm bảo giao diện chỉ đọc cho khách, không sửa trực tiếp thông tin nhạy cảm.
 * - BR15.8: Tình trạng thanh toán tính từ giao dịch thành công.
 * - BR15.9: Kiểm tra quyền sở hữu nghiêm ngặt theo ID.
 * - BR15.10: Chuyển tiếp sang đặt lịch trả kho UC16.
 */
public interface RentalContractService {

    /**
     * Lấy danh sách hợp đồng thuê kho của khách hàng đang đăng nhập với bộ lọc và phân trang.
     *
     * @param customerId  Mã UUID của khách hàng
     * @param filterGroup Nhóm trạng thái (ALL, PENDING_HANDOVER, ACTIVE, ENDED)
     * @param search      Từ khóa tìm kiếm (mã hợp đồng, mã kho, ...)
     * @param pageable    Thông tin phân trang
     * @return Trang danh sách hợp đồng thuê kho
     */
    Page<RentalContractListItemResponse> getCustomerRentals(UUID customerId, String filterGroup, String search, Pageable pageable);

    /**
     * Lấy thông tin chi tiết một hợp đồng thuê kho thuộc về khách hàng.
     *
     * @param customerId Mã UUID của khách hàng
     * @param contractId Mã UUID của hợp đồng thuê
     * @return DTO chi tiết hợp đồng và các khoản thu, biên bản liên quan
     */
    RentalContractDetailResponse getCustomerRentalDetail(UUID customerId, UUID contractId);

    /**
     * Lấy danh sách hợp đồng thuê kho tại cơ sở cho Quản lý cơ sở (FACILITY_MANAGER).
     *
     * @param managerUserId Mã UUID của quản lý đang đăng nhập
     * @param facilityId    Mã cơ sở (tùy chọn, nếu null sẽ lấy cơ sở đang phụ trách)
     * @param filterGroup   Nhóm trạng thái
     * @param search        Từ khóa tìm kiếm
     * @param pageable      Thông tin phân trang
     * @return Trang danh sách hợp đồng thuê kho tại cơ sở
     */
    Page<RentalContractListItemResponse> getFacilityRentals(UUID managerUserId, UUID facilityId, String filterGroup, String search, Pageable pageable);

    /**
     * Lấy chi tiết hợp đồng thuê kho tại cơ sở cho Quản lý cơ sở.
     *
     * @param managerUserId Mã UUID của quản lý đang đăng nhập
     * @param contractId    Mã UUID của hợp đồng
     * @return DTO chi tiết hợp đồng
     */
    RentalContractDetailResponse getFacilityRentalDetail(UUID managerUserId, UUID contractId);
}
