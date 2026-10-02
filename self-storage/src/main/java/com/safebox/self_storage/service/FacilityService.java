package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.response.FacilityDetailResponse;
import java.util.UUID;

public interface FacilityService {
    FacilityDetailResponse getFacilityDetail(UUID facilityId);
}
