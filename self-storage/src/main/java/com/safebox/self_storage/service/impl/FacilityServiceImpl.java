package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.response.FacilityDetailResponse;
import com.safebox.self_storage.dto.response.FacilityUnitTypeDetailDto;
import com.safebox.self_storage.entity.Facility;
import com.safebox.self_storage.entity.PricingPolicy;
import com.safebox.self_storage.entity.StorageUnitType;
import com.safebox.self_storage.repository.FacilityRepository;
import com.safebox.self_storage.repository.PricingPolicyRepository;
import com.safebox.self_storage.repository.StorageUnitRepository;
import com.safebox.self_storage.repository.StorageUnitTypeRepository;
import com.safebox.self_storage.service.FacilityService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class FacilityServiceImpl implements FacilityService {

    private final FacilityRepository facilityRepository;
    private final PricingPolicyRepository pricingPolicyRepository;
    private final StorageUnitRepository storageUnitRepository;
    private final StorageUnitTypeRepository storageUnitTypeRepository;

    public FacilityServiceImpl(FacilityRepository facilityRepository,
                               PricingPolicyRepository pricingPolicyRepository,
                               StorageUnitRepository storageUnitRepository,
                               StorageUnitTypeRepository storageUnitTypeRepository) {
        this.facilityRepository = facilityRepository;
        this.pricingPolicyRepository = pricingPolicyRepository;
        this.storageUnitRepository = storageUnitRepository;
        this.storageUnitTypeRepository = storageUnitTypeRepository;
    }

    @Override
    public FacilityDetailResponse getFacilityDetail(UUID facilityId) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        List<StorageUnitType> activeTypes = storageUnitTypeRepository.findAll().stream()
                .filter(t -> "ACTIVE".equalsIgnoreCase(t.getStatus()))
                .collect(Collectors.toList());

        List<FacilityUnitTypeDetailDto> unitTypes = activeTypes.stream().map(type -> {
            PricingPolicy policy = pricingPolicyRepository.findAll().stream()
                    .filter(p -> p.getFacilityId().equals(facilityId) && p.getTypeId().equals(type.getTypeId()) && "ACTIVE".equalsIgnoreCase(p.getStatus()))
                    .findFirst().orElse(null);
            
            long availableUnits = storageUnitRepository.findAll().stream()
                    .filter(u -> u.getFacilityId().equals(facilityId) && u.getTypeId().equals(type.getTypeId()) && "AVAILABLE".equalsIgnoreCase(u.getStatus()))
                    .count();

            BigDecimal area = type.getWidth().multiply(type.getLength());

            return new FacilityUnitTypeDetailDto(
                    type.getTypeId(),
                    type.getTypeName(),
                    type.getStorageMode(),
                    type.getSizeName(),
                    type.getWidth(),
                    type.getLength(),
                    type.getHeight(),
                    area,
                    type.getFeatures(),
                    policy != null ? policy.getMonthlyPrice() : BigDecimal.ZERO,
                    policy != null ? policy.getDepositAmount() : BigDecimal.ZERO,
                    availableUnits
            );
        }).collect(Collectors.toList());

        long totalAvailable = unitTypes.stream().mapToLong(FacilityUnitTypeDetailDto::availableUnits).sum();

        return new FacilityDetailResponse(
                facility.getFacilityId(),
                facility.getName(),
                facility.getAddress(),
                facility.getLatitude(),
                facility.getLongitude(),
                facility.getPhone(),
                facility.getOpeningTime() != null ? facility.getOpeningTime().toString() : null,
                facility.getClosingTime() != null ? facility.getClosingTime().toString() : null,
                totalAvailable,
                unitTypes
        );
    }
}
