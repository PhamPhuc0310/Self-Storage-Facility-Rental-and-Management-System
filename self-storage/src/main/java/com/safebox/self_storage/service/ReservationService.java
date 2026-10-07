package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.CreateReservationRequest;
import com.safebox.self_storage.dto.response.ReservationResponse;

import java.util.UUID;

/**
 * Service định nghĩa các nghiệp vụ xử lý yêu cầu đặt kho (Reservation):
 * - UC05: Tạo yêu cầu đặt kho (Create Storage Reservation Request)
 */
public interface ReservationService {

    /**
     * UC05: Tạo mới một yêu cầu đặt kho cho khách hàng.
     *
     * @param customerId ID tài khoản khách hàng thực hiện yêu cầu
     * @param request    Thông tin chi tiết yêu cầu đặt kho
     * @return DTO kết quả đặt kho đã được khởi tạo
     */
    ReservationResponse createReservation(UUID customerId, CreateReservationRequest request);
}
