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
import java.time.LocalDate;
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
            List<FacilityUnitTypeDto> unitTypes = new ArrayList<>();
            for (Integer typeId : storageUnitRepository.findAvailableTypeIdsByFacility(facility.getFacilityId())) {
                Optional<StorageUnitType> typeOpt = storageUnitTypeRepository.findById(typeId);
                if (typeOpt.isPresent() && "ACTIVE".equals(typeOpt.get().getStatus())) {
                    StorageUnitType type = typeOpt.get();
                    BigDecimal area = type.getWidth().multiply(type.getLength());
                    BigDecimal monthlyPrice = CurrentPricing.forType(pricingPolicies, typeId, LocalDate.now())
                        .map(PricingPolicy::getMonthlyPrice).orElse(null);
                    unitTypes.add(new FacilityUnitTypeDto(typeId, type.getSizeName(),
                        type.getStorageMode(), area, monthlyPrice));
                }
            }
            BigDecimal startingPrice = unitTypes.stream().map(FacilityUnitTypeDto::monthlyPrice)
                .filter(p -> p != null).min(BigDecimal::compareTo).orElse(null);
            
            return new FacilityListResponse(
                facility.getFacilityId(),
                facility.getName(),
                facility.getAddress(),
                facility.getImagePath(),
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
        for (Integer typeId : storageUnitRepository.findAvailableTypeIdsByFacility(facilityId)) {
            long available = storageUnitRepository.countAvailableUnitsByFacilityAndType(facilityId, typeId);
            Optional<StorageUnitType> typeOpt = storageUnitTypeRepository.findById(typeId);
            if (typeOpt.isPresent() && "ACTIVE".equals(typeOpt.get().getStatus())) {
                StorageUnitType type = typeOpt.get();
                BigDecimal area = type.getWidth().multiply(type.getLength());
                Optional<PricingPolicy> policy = CurrentPricing.forType(pricingPolicies, typeId, LocalDate.now());
                
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
                    type.getMinTemperature(),
                    type.getMaxTemperature(),
                    type.getDemoIntro(),
                    type.getDemoGoods(),
                    type.getDemoConditions(),
                    type.getImagePath(),
                    policy.map(PricingPolicy::getMonthlyPrice).orElse(null),
                    policy.map(PricingPolicy::getDepositAmount).orElse(null),
                    available
                ));
            }
        }
        
        return new FacilityDetailResponse(
            facility.getFacilityId(),
            facility.getName(),
            facility.getAddress(),
            facility.getImagePath(),
            facility.getDemoIntro(),
            facility.getDemoSafety(),
            facility.getDemoAccess(),
            facility.getDemoTerms(),
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
