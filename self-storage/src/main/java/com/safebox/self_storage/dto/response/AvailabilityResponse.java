package com.safebox.self_storage.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record AvailabilityResponse(UUID facilityId, Integer typeId, LocalDate startDate, LocalDate endDate,
        long totalAvailableUnits, long confirmedOverlappingReservations, long remainingSlots, boolean available) {}
