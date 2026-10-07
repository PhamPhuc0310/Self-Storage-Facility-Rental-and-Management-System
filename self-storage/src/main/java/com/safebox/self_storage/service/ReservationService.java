package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.CancelReservationRequest;
import com.safebox.self_storage.dto.CreateReservationRequest;
import com.safebox.self_storage.dto.RejectReservationRequest;
import com.safebox.self_storage.dto.response.ReservationResponse;

import java.util.List;
import java.util.UUID;

/**
 * Interface định nghĩa các nghiệp vụ cốt lõi về đặt kho (Reservation):
 * - UC05: Tạo yêu cầu đặt kho (Create Storage Reservation Request)
 * - UC06: Xem và hủy yêu cầu đặt kho của tôi (View and Cancel My Reservation Requests)
 */
public interface ReservationService {

    /**
     * UC05: Tạo mới một yêu cầu đặt kho cho khách hàng.
     * Kiểm tra trạng thái tài khoản khách hàng, tính hợp lệ của cơ sở, loại kho,
     * số lượng kho còn trống, ngày thuê, áp dụng bảng giá hiện hành để tính tiền thuê và tiền cọc dự kiến,
     * thiết lập thời hạn giữ chỗ (hold_expires_at) và lưu trạng thái PENDING.
     *
     * @param customerId Mã UUID của khách hàng đặt kho
     * @param request    Thông tin chi tiết về cơ sở, loại kho, ngày bắt đầu và thời gian thuê
     * @return DTO chứa thông tin đầy đủ của yêu cầu vừa tạo
     */
    ReservationResponse createReservation(UUID customerId, CreateReservationRequest request);

    /**
     * UC06: Lấy danh sách yêu cầu đặt kho của khách hàng hiện tại.
     * Hỗ trợ lọc theo trạng thái và tìm kiếm theo từ khóa.
     *
     * @param customerId Mã UUID của khách hàng
     * @param status     Trạng thái cần lọc (PENDING, CONFIRMED, REJECTED, CANCELLED hoặc ALL/null)
     * @param search     Từ khóa tìm kiếm (mã yêu cầu, tên cơ sở, loại kho)
     * @return Danh sách yêu cầu đặt kho thỏa điều kiện
     */
    List<ReservationResponse> getMyReservations(UUID customerId, String status, String search);

    /**
     * UC06: Xem chi tiết một yêu cầu đặt kho cụ thể của khách hàng hiện tại.
     * Xác thực tính sở hữu (yêu cầu phải thuộc về đúng khách hàng).
     *
     * @param customerId    Mã UUID của khách hàng
     * @param reservationId Mã UUID của yêu cầu đặt kho
     * @return DTO chứa thông tin chi tiết yêu cầu
     */
    ReservationResponse getMyReservationDetail(UUID customerId, UUID reservationId);

    /**
     * UC06: Khách hàng hủy yêu cầu đặt kho của chính mình.
     * Chỉ cho phép hủy khi yêu cầu đang ở trạng thái hợp lệ (chờ duyệt PENDING hoặc đã xác nhận CONFIRMED).
     * Cập nhật trạng thái thành CANCELLED, lưu ngày giờ hủy và lý do hủy.
     *
     * @param customerId    Mã UUID của khách hàng
     * @param reservationId Mã UUID của yêu cầu cần hủy
     * @param request       DTO chứa lý do hủy từ khách hàng
     * @return DTO chứa thông tin yêu cầu sau khi hủy
     */
    ReservationResponse cancelMyReservation(UUID customerId, UUID reservationId, CancelReservationRequest request);
}
