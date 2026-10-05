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
 * - UC08: Xác nhận hoặc từ chối yêu cầu đặt kho (Approve or Reject Reservation Request)
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

    /**
     * UC08: Lấy danh sách toàn bộ các yêu cầu đặt kho trên hệ thống (dành cho Ban quản lý / Nhân viên).
     * Hỗ trợ lọc theo trạng thái, cơ sở cụ thể và từ khóa tìm kiếm.
     *
     * @param status     Trạng thái cần lọc
     * @param facilityId Mã cơ sở cần lọc
     * @param search     Từ khóa tìm kiếm (mã yêu cầu, họ tên khách hàng, số điện thoại)
     * @return Danh sách yêu cầu đặt kho phù hợp
     */
    List<ReservationResponse> getAllReservations(String status, UUID facilityId, String search);

    /**
     * UC08: Xem chi tiết một yêu cầu đặt kho theo mã định danh (dành cho Ban quản lý).
     *
     * @param reservationId Mã UUID của yêu cầu đặt kho
     * @return DTO chứa thông tin chi tiết của yêu cầu
     */
    ReservationResponse getReservationDetail(UUID reservationId);

    /**
     * UC08: Xác nhận (phê duyệt) yêu cầu đặt kho từ khách hàng.
     * Kiểm tra yêu cầu phải ở trạng thái PENDING, kiểm tra cơ sở còn kho trống hay không,
     * và chuyển trạng thái yêu cầu sang CONFIRMED.
     *
     * @param reservationId Mã UUID của yêu cầu cần xác nhận
     * @return DTO chứa thông tin yêu cầu sau khi đã được xác nhận
     */
    ReservationResponse approveReservation(UUID reservationId);

    /**
     * UC08: Từ chối yêu cầu đặt kho của khách hàng.
     * Kiểm tra yêu cầu phải ở trạng thái PENDING, ghi nhận lý do từ chối,
     * cập nhật ngày giờ và chuyển trạng thái sang REJECTED.
     *
     * @param reservationId Mã UUID của yêu cầu bị từ chối
     * @param request       DTO chứa lý do từ chối của ban quản lý
     * @return DTO chứa thông tin yêu cầu sau khi đã bị từ chối
     */
    ReservationResponse rejectReservation(UUID reservationId, RejectReservationRequest request);
}
