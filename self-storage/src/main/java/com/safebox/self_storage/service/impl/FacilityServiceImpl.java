package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.dto.response.FacilityDetailResponse;
import com.safebox.self_storage.dto.response.FacilityListResponse;
import com.safebox.self_storage.dto.response.FacilityUnitTypeDetailDto;
import com.safebox.self_storage.dto.response.FacilityUnitTypeDto;
import com.safebox.self_storage.entity.Facility;
import com.safebox.self_storage.entity.PricingPolicy;
import com.safebox.self_storage.entity.StorageUnitType;
import com.safebox.self_storage.repository.FacilityRepository;
import com.safebox.self_storage.repository.PricingPolicyRepository;
import com.safebox.self_storage.repository.StorageUnitRepository;
import com.safebox.self_storage.repository.StorageUnitTypeRepository;
import com.safebox.self_storage.service.FacilityService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class FacilityServiceImpl implements FacilityService {

    private final FacilityRepository facilityRepository;
    private final PricingPolicyRepository pricingPolicyRepository;
    private final StorageUnitTypeRepository storageUnitTypeRepository;
    private final StorageUnitRepository storageUnitRepository;

    public FacilityServiceImpl(FacilityRepository facilityRepository,
                               PricingPolicyRepository pricingPolicyRepository,
                               StorageUnitTypeRepository storageUnitTypeRepository,
                               StorageUnitRepository storageUnitRepository) {
        this.facilityRepository = facilityRepository;
        this.pricingPolicyRepository = pricingPolicyRepository;
        this.storageUnitTypeRepository = storageUnitTypeRepository;
        this.storageUnitRepository = storageUnitRepository;
    }

    @Override
    public Page<FacilityListResponse> getActiveFacilities(String search, Pageable pageable) {
        Page<Facility> facilities = facilityRepository.searchActiveFacilities(search, pageable);
        
        return facilities.map(facility -> {
            long availableUnitsCount = storageUnitRepository.countAvailableUnitsByFacility(facility.getFacilityId());
            
            List<PricingPolicy> pricingPolicies = pricingPolicyRepository.findByFacilityIdAndStatus(facility.getFacilityId(), "ACTIVE");
            BigDecimal startingPrice = pricingPolicies.stream()
                .map(PricingPolicy::getMonthlyPrice)
                .min(BigDecimal::compareTo)
                .orElse(null);
                
            List<FacilityUnitTypeDto> unitTypes = new ArrayList<>();
            for (PricingPolicy policy : pricingPolicies) {
                long typeAvailable = storageUnitRepository.countAvailableUnitsByFacilityAndType(facility.getFacilityId(), policy.getTypeId());
                if (typeAvailable > 0) {
                    Optional<StorageUnitType> typeOpt = storageUnitTypeRepository.findById(policy.getTypeId());
                    if (typeOpt.isPresent()) {
                        StorageUnitType type = typeOpt.get();
                        BigDecimal area = type.getWidth().multiply(type.getLength());
                        unitTypes.add(new FacilityUnitTypeDto(
                            type.getTypeId(),
                            type.getSizeName(),
                            type.getStorageMode(),
                            area,
                            policy.getMonthlyPrice()
                        ));
                    }
                }
            }
            
            return new FacilityListResponse(
                facility.getFacilityId(),
                facility.getName(),
                facility.getAddress(),
                availableUnitsCount,
                startingPrice,
                unitTypes
            );
        });
    }

    @Override
    public FacilityDetailResponse getFacilityDetail(UUID facilityId) {
        Facility facility = facilityRepository.findById(facilityId)
            .orElseThrow(() -> new RuntimeException("Facility not found"));
            
        if (!"ACTIVE".equals(facility.getStatus())) {
            throw new RuntimeException("Facility is not active");
        }
        
        long totalAvailableUnits = storageUnitRepository.countAvailableUnitsByFacility(facilityId);
        List<PricingPolicy> pricingPolicies = pricingPolicyRepository.findByFacilityIdAndStatus(facilityId, "ACTIVE");
        
        List<FacilityUnitTypeDetailDto> unitTypes = new ArrayList<>();
        for (PricingPolicy policy : pricingPolicies) {
            long available = storageUnitRepository.countAvailableUnitsByFacilityAndType(facilityId, policy.getTypeId());
            Optional<StorageUnitType> typeOpt = storageUnitTypeRepository.findById(policy.getTypeId());
            if (typeOpt.isPresent()) {
                StorageUnitType type = typeOpt.get();
                BigDecimal area = type.getWidth().multiply(type.getLength());
                
                unitTypes.add(new FacilityUnitTypeDetailDto(
                    type.getTypeId(),
                    type.getTypeName(),
                    type.getStorageMode(),
                    type.getSizeName(),
                    type.getWidth(),
                    type.getLength(),
                    type.getHeight(),
                    area,
                    type.getFeatures(),
                    policy.getMonthlyPrice(),
                    policy.getDepositAmount(),
                    available
                ));
            }
        }
        
        return new FacilityDetailResponse(
            facility.getFacilityId(),
            facility.getName(),
            facility.getAddress(),
            facility.getLatitude(),
            facility.getLongitude(),
            facility.getPhone(),
            facility.getOpeningTime() != null ? facility.getOpeningTime().toString() : null,
            facility.getClosingTime() != null ? facility.getClosingTime().toString() : null,
            totalAvailableUnits,
            unitTypes
        );
    }
}
