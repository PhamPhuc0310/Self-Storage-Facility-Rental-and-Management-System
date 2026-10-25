package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.overdue.AdjustPenaltyRequest;
import com.safebox.self_storage.dto.overdue.OverdueContractResponse;
import com.safebox.self_storage.dto.overdue.PayPenaltyRequest;
import com.safebox.self_storage.dto.overdue.PenaltyDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service xử lý các nghiệp vụ của UC18: Theo dõi quá hạn và xử lý phí phát sinh.
 * - BR18.1: Chỉ đánh dấu quá hạn khi quá thời điểm kết thúc và chưa có gia hạn hợp lệ / đã hoàn tất trả kho.
 * - BR18.2: Công thức phí quá hạn phải có cấu hình và ngày hiệu lực rõ ràng, không tự đặt tỷ lệ cố định.
 * - BR18.3: Mỗi kỳ phí chỉ phát sinh một lần (Idempotent), có log và giải thích cách tính.
 * - BR18.4: Manager chỉ xem/xử lý hợp đồng thuộc cơ sở phụ trách; CUSTOMER chỉ xem của mình.
 * - BR18.5: Không tự động xóa khoản nợ khi chỉnh ngày hợp đồng; phải có nghiệp vụ điều chỉnh có lưu vết.
 */
public interface OverdueService {

    /**
     * BR18.4: Quản lý cơ sở xem danh sách hợp đồng quá hạn tại cơ sở phụ trách.
     */
    Page<OverdueContractResponse> getManagerOverdueContracts(
            UUID managerUserId, UUID facilityId, String status, String search, Pageable pageable);

    /**
     * BR18.4: Khách hàng xem danh sách các hợp đồng quá hạn của chính mình.
     */
    Page<OverdueContractResponse> getCustomerOverdueContracts(
            UUID customerId, Pageable pageable);

    /**
     * Xem chi tiết một hợp đồng quá hạn, bao gồm lịch sử phí phạt và lưu vết điều chỉnh nợ.
     */
    OverdueContractResponse getOverdueContractDetail(UUID currentUserId, UUID contractId);

    /**
     * BR18.1, BR18.2, BR18.3: Tính phí phạt quá hạn cho một hợp đồng theo cấu hình chính sách (Idempotent).
     */
    PenaltyDetailResponse calculateOverdueFeeForContract(UUID currentUserId, UUID contractId, LocalDate targetDate);

    /**
     * Quản lý kích hoạt quét và tính phí quá hạn hàng loạt cho cơ sở phụ trách.
     */
    int batchCalculateOverdueFeesForFacility(UUID managerUserId, UUID facilityId, LocalDate targetDate);

    /**
     * BR18.5: Quản lý điều chỉnh hoặc miễn giảm công nợ phí phạt (có lưu vết kiểm toán, không xóa nợ gốc).
     */
    PenaltyDetailResponse adjustPenaltyDebt(UUID managerUserId, UUID penaltyId, AdjustPenaltyRequest request);

    /**
     * Khách hàng hoặc Nhân viên thanh toán phí phạt quá hạn.
     */
    PenaltyDetailResponse payPenaltyDebt(UUID currentUserId, UUID penaltyId, PayPenaltyRequest request);

    /**
     * Quản lý cơ sở xác nhận / duyệt thanh toán phí phạt khi khách hàng thanh toán trực tuyến.
     */
    PenaltyDetailResponse approvePenaltyPayment(UUID managerUserId, UUID penaltyId, boolean approved, String note);
}

