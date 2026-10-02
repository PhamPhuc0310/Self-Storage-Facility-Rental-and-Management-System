package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.response.EstimatedPriceResponse;
import com.safebox.self_storage.entity.PricingPolicy;
import com.safebox.self_storage.entity.StorageUnitType;
import com.safebox.self_storage.repository.PricingPolicyRepository;
import com.safebox.self_storage.repository.StorageUnitTypeRepository;
import com.safebox.self_storage.service.PricingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PricingServiceImpl implements PricingService {

    private final PricingPolicyRepository pricingPolicyRepository;
    private final StorageUnitTypeRepository storageUnitTypeRepository;

    public PricingServiceImpl(PricingPolicyRepository pricingPolicyRepository,
                              StorageUnitTypeRepository storageUnitTypeRepository) {
        this.pricingPolicyRepository = pricingPolicyRepository;
        this.storageUnitTypeRepository = storageUnitTypeRepository;
    }

    @Override
    public EstimatedPriceResponse estimatePrice(UUID facilityId, Integer typeId, int months) {
        // BR04.3: Thời gian thuê phải lớn hơn 0 tháng
        if (months <= 0) {
            throw new IllegalArgumentException("Thời gian thuê phải lớn hơn 0 tháng");
        }
        
        // BR04.1: Loại kho phải tồn tại và đang hoạt động
        StorageUnitType type = storageUnitTypeRepository.findById(typeId)
            .orElseThrow(() -> new RuntimeException("Loại kho không tồn tại"));
            
        if (!"ACTIVE".equals(type.getStatus())) {
            throw new RuntimeException("Loại kho không hoạt động");
        }
        
        // Lấy bảng giá hiện tại (BR04.2)
        List<PricingPolicy> policies = pricingPolicyRepository.findByFacilityIdAndStatus(facilityId, "ACTIVE");
        PricingPolicy policy = CurrentPricing.forType(policies, typeId, LocalDate.now())
            .orElseThrow(() -> new NoSuchElementException("Cơ sở chưa có bảng giá đang hiệu lực cho loại kho này"));
            
        BigDecimal monthlyPrice = policy.getMonthlyPrice();
        
        // BR04.4, BR04.5: Giá dự kiến = Giá thuê 1 tháng x Số tháng (dùng BigDecimal)
        BigDecimal estimatedTotal = monthlyPrice.multiply(BigDecimal.valueOf(months));
        
        // BR04.6: Giá dự kiến không được nhỏ hơn 0
        if (estimatedTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Giá dự kiến không hợp lệ");
        }
        
        // BR04.7: Đây chỉ là giá dự kiến, thông báo hoặc flag nếu cần (ở đây trả về DTO)
        return new EstimatedPriceResponse(monthlyPrice, months, estimatedTotal);
    }
}
