package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.Sprint2Access;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageDiscoveryServiceTest {
    private final FacilityRepository facilities=mock(FacilityRepository.class);
    private final StorageUnitRepository units=mock(StorageUnitRepository.class);
    private final StorageUnitTypeRepository types=mock(StorageUnitTypeRepository.class);
    private final PricingPolicyRepository prices=mock(PricingPolicyRepository.class);
    private final RentalContractRepository contracts=mock(RentalContractRepository.class);
    private final ReservationRepository reservations=mock(ReservationRepository.class);
    private final StorageDiscoveryService service=new StorageDiscoveryService(facilities,units,types,prices,contracts,reservations);
    private final UUID facilityId=UUID.randomUUID();
    private final LocalDate start=LocalDate.now(Sprint2Access.ZONE).plusDays(1);

    private StorageUnit unit(String status,boolean verified) {
        StorageUnit u=new StorageUnit();u.setUnitId(UUID.randomUUID());u.setFacilityId(facilityId);
        u.setTypeId(7);u.setUnitNumber("A1");u.setStatus(status);
        u.setLengthM(new BigDecimal("2.000"));u.setWidthM(new BigDecimal("3.000"));u.setHeightM(new BigDecimal("2.500"));
        u.setReadinessVerified(verified);u.setDimensionsVerified(verified);return u;
    }
    private void setup(StorageUnit unit,boolean priced,long held) {
        Facility f=new Facility();f.setFacilityId(facilityId);f.setName("Kho Q7");f.setAddress("Quận 7");f.setStatus("ACTIVE");
        StorageUnitType t=new StorageUnitType();t.setTypeId(7);t.setTypeName("Kho thường");t.setStatus("ACTIVE");
        when(facilities.searchActiveFacilities(isNull(),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(f)));
        when(units.findByFacilityIdOrderByUnitNumberAsc(facilityId)).thenReturn(List.of(unit));
        when(types.findAll()).thenReturn(List.of(t));
        when(reservations.countActivePendingOverlapping(eq(facilityId),eq(7),any(),any(),any(LocalDateTime.class))).thenReturn(held);
        when(contracts.findOverlappingUnitIds(anyCollection(),any(),any())).thenReturn(Set.of());
        if(priced){PricingPolicy p=new PricingPolicy();p.setPricingId(UUID.randomUUID());p.setFacilityId(facilityId);
            p.setTypeId(7);p.setStatus("ACTIVE");p.setMonthlyPrice(new BigDecimal("500000.00"));p.setEffectiveFrom(start);
            when(prices.findByFacilityIdAndStatus(facilityId,"ACTIVE")).thenReturn(List.of(p));}
        else when(prices.findByFacilityIdAndStatus(facilityId,"ACTIVE")).thenReturn(List.of());
    }
    private StorageDiscoveryService.Filters filters(){return new StorageDiscoveryService.Filters(null,null,null,null,null,null,null,start,1);}

    @Test void verifiedVolumeAndEffectivePriceAreBookable() {
        setup(unit("AVAILABLE",true),true,0);
        var card=service.search(filters()).get(0);
        assertEquals(new BigDecimal("15.000000000"),card.volumeM3());
        assertEquals(new BigDecimal("500000.00"),card.monthlyPrice());
        assertTrue(card.bookable());assertEquals(1,card.availableCount());
    }
    @Test void unpricedMaintenanceAndHeldUnitsAreNotBookable() {
        setup(unit("AVAILABLE",true),false,0);
        assertFalse(service.search(filters()).get(0).bookable());
        reset(units,prices,reservations,contracts,types,facilities);
        setup(unit("MAINTENANCE",true),true,0);
        assertEquals("Đang bảo trì",service.search(filters()).get(0).availabilityLabel());
        reset(units,prices,reservations,contracts,types,facilities);
        setup(unit("AVAILABLE",true),true,1);
        assertEquals("Đang giữ chỗ",service.search(filters()).get(0).availabilityLabel());
    }
    @Test void legacyAvailableUnitWithoutMeasurementsRemainsBookable() {
        StorageUnit legacy=unit("AVAILABLE",false);
        legacy.setLengthM(null);legacy.setWidthM(null);legacy.setHeightM(null);
        setup(legacy,true,0);
        var card=service.search(filters()).get(0);
        assertTrue(card.bookable());
        assertEquals(1,card.availableCount());
        assertNull(card.volumeM3());
        assertEquals("Còn trống",card.availabilityLabel());
    }
    @Test void occupiedReservedInactiveAndContractConflictsRemainBlocked() {
        for (String status : List.of("OCCUPIED", "RESERVED", "INACTIVE")) {
            setup(unit(status,false),true,0);
            assertFalse(service.search(filters()).get(0).bookable(),status);
        }
        StorageUnit conflicting=unit("AVAILABLE",false);
        setup(conflicting,true,0);
        when(contracts.findOverlappingUnitIds(anyCollection(),any(),any())).thenReturn(Set.of(conflicting.getUnitId()));
        assertFalse(service.search(filters()).get(0).bookable());
    }
    @Test void filtersCannotFabricateVolumeAndRejectInvalidRange(){
        setup(unit("AVAILABLE",false),true,0);
        var filtered=new StorageDiscoveryService.Filters(null,null,new BigDecimal("1"),null,null,null,null,start,1);
        assertTrue(service.search(filtered).isEmpty());
        var invalid=new StorageDiscoveryService.Filters(null,null,new BigDecimal("2"),new BigDecimal("1"),null,null,null,start,1);
        assertThrows(RuntimeException.class,()->service.search(invalid));
    }
}
