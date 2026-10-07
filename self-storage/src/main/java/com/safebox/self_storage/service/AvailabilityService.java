package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.response.AvailabilityResponse;
import java.time.LocalDate;
import java.util.UUID;

public interface AvailabilityService {
    AvailabilityResponse check(UUID facilityId, Integer typeId, LocalDate startDate, LocalDate endDate);
}
