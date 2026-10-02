package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.response.EstimatedPriceResponse;
import java.util.UUID;

public interface PricingService {
    EstimatedPriceResponse estimatePrice(UUID facilityId, Integer typeId, int months);
}
