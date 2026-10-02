package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.response.FacilityDetailResponse;
import com.safebox.self_storage.dto.response.FacilityListResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FacilityService {
    Page<FacilityListResponse> getActiveFacilities(String search, Pageable pageable);
    FacilityDetailResponse getFacilityDetail(UUID facilityId);
}
