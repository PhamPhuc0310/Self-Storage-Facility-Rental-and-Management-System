package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.response.AvailabilityResponse;
import com.safebox.self_storage.entity.Facility;
import com.safebox.self_storage.entity.StorageUnitType;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.FacilityRepository;
import com.safebox.self_storage.repository.ReservationRepository;
import com.safebox.self_storage.repository.StorageUnitRepository;
import com.safebox.self_storage.repository.StorageUnitTypeRepository;
import com.safebox.self_storage.service.AvailabilityService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AvailabilityServiceImpl implements AvailabilityService {
    private final FacilityRepository facilities;
    private final StorageUnitTypeRepository types;
    private final StorageUnitRepository units;
    private final ReservationRepository reservations;
    public AvailabilityServiceImpl(FacilityRepository facilities, StorageUnitTypeRepository types,
            StorageUnitRepository units, ReservationRepository reservations) {
        this.facilities = facilities; this.types = types; this.units = units; this.reservations = reservations;
    }
    public AvailabilityResponse check(UUID facilityId, Integer typeId, LocalDate startDate, LocalDate endDate) {
        Facility facility = facilities.findById(facilityId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở."));
        StorageUnitType type = types.findById(typeId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy loại kho."));
        if (!"ACTIVE".equals(facility.getStatus()) || !"ACTIVE".equals(type.getStatus()))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cơ sở hoặc loại kho không hoạt động.");
        if (startDate.isBefore(LocalDate.now()))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu không được nhỏ hơn ngày hiện tại.");
        if (!endDate.isAfter(startDate))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu.");
        long capacity = units.countAvailableUnitsByFacilityAndType(facilityId, typeId);
        long occupied = reservations.countConfirmedOverlapping(facilityId, typeId, startDate, endDate);
        long remaining = Math.max(0, capacity - occupied);
        return new AvailabilityResponse(facilityId, typeId, startDate, endDate, capacity, occupied, remaining, remaining > 0);
    }
}
