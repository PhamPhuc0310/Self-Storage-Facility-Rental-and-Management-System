package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.CompleteReturnInspectionRequest;
import com.safebox.self_storage.dto.CreateReturnScheduleRequest;
import com.safebox.self_storage.dto.response.FacilityReturnTaskItemResponse;
import com.safebox.self_storage.dto.response.ReturnInspectionDetailResponse;
import com.safebox.self_storage.dto.response.ReturnScheduleResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service xử lý các nghiệp vụ của Use Case UC16:
 * "Đặt lịch trả kho và xác nhận tình trạng sau trả" (BR16.1 – BR16.10).
 */
public interface ReturnInspectionService {

    /**
     * Khách hàng đặt lịch hẹn trả kho cho hợp đồng ACTIVE của mình (BR16.1, BR16.2).
     */
    ReturnScheduleResponse scheduleReturnAppointment(UUID customerId, UUID contractId, CreateReturnScheduleRequest request);

    /**
     * Xem thông tin lịch trả kho hiện có của hợp đồng (Khách hàng hoặc Nhân viên/Quản lý).
     */
    ReturnScheduleResponse getReturnSchedule(UUID currentUserId, UUID contractId);

    /**
     * Khách hàng hủy lịch hẹn trả kho đang SCHEDULED trước giờ hẹn (BR12.5).
     */
    ReturnScheduleResponse cancelReturnAppointment(UUID currentUserId, UUID contractId);

    /**
     * Danh sách lịch hẹn trả kho tại cơ sở cho nhân viên và quản lý cơ sở (BR12.6, BR12.7, BR16.3).
     */
    List<FacilityReturnTaskItemResponse> getFacilityReturnAppointments(UUID currentUserId, UUID facilityId, String status);

    /**
     * Xem chi tiết một biên bản trả kho và kiểm tra theo handoverId (BR16.4, BR16.5).
     */
    ReturnInspectionDetailResponse getReturnInspectionDetail(UUID currentUserId, UUID handoverId);

    /**
     * Nhân viên/Quản lý cơ sở thực hiện kiểm tra thực tế, thu hồi khóa/thẻ và hoàn tất nhận lại kho (BR16.3 – BR16.10).
     */
    ReturnInspectionDetailResponse completeReturnHandoverAndInspection(UUID staffOrManagerId, CompleteReturnInspectionRequest request);
}
