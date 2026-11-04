package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.manager.StorageUnitRequest;
import com.safebox.self_storage.dto.manager.DimensionVerificationRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.impl.ManagerCatalogServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManagementPhase1UnitTest {
    private final FacilityRepository facilities=mock(FacilityRepository.class);
    private final StorageUnitTypeRepository types=mock(StorageUnitTypeRepository.class);
    private final StorageUnitRepository units=mock(StorageUnitRepository.class);
    private final PricingPolicyRepository pricing=mock(PricingPolicyRepository.class);
    private final UserFacilityAssignmentRepository assignments=mock(UserFacilityAssignmentRepository.class);
    private final RentalContractRepository contracts=mock(RentalContractRepository.class);
    private final ActivityLogRepository activityLogs=mock(ActivityLogRepository.class);
    private final ManagerCatalogServiceImpl service=new ManagerCatalogServiceImpl(facilities,types,units,pricing,assignments,contracts,activityLogs);
    private final UUID manager=UUID.randomUUID(), facilityId=UUID.randomUUID();
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(manager,null,List.of(new SimpleGrantedAuthority("ROLE_FACILITY_MANAGER"))));
        Facility f=new Facility(); f.setFacilityId(facilityId); f.setStatus("ACTIVE");
        when(facilities.findById(facilityId)).thenReturn(Optional.of(f));
        when(assignments.findActiveFacilityIds(eq(manager),any(LocalDate.class))).thenReturn(List.of(facilityId));
        StorageUnitType type=new StorageUnitType(); type.setTypeId(1); type.setStatus("ACTIVE");
        when(types.findById(1)).thenReturn(Optional.of(type));
        when(units.save(any(StorageUnit.class))).thenAnswer(i->i.getArgument(0));
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    private StorageUnitRequest request(String status, BigDecimal length, boolean ready){return new StorageUnitRequest("A-1",1,"1","A",status,length,new BigDecimal("2"),new BigDecimal("3"),false,ready);}
    @Test void firstUnitUsesGlobalActiveTypeWithoutPriceOrPriorAssociation(){
        StorageUnitType active=new StorageUnitType();active.setTypeId(1);active.setTypeName("Kho tiêu chuẩn");active.setStatus("ACTIVE");
        StorageUnitType inactive=new StorageUnitType();inactive.setTypeId(2);inactive.setTypeName("Loại ngừng sử dụng");inactive.setStatus("INACTIVE");
        when(types.findAll()).thenReturn(List.of(active,inactive));
        var choices=service.unitTypes(manager,facilityId);
        assertEquals(List.of(1),choices.stream().map(x->x.typeId()).toList());
        assertNull(choices.get(0).monthlyPrice());
        var created=service.createUnit(manager,facilityId,request("INACTIVE",new BigDecimal("4"),false));
        assertEquals(1,created.typeId());
        assertEquals("INACTIVE",created.status());
        assertNull(created.volumeM3());
        verify(types,never()).save(any());
        verify(pricing,never()).save(any());
    }
    @Test void globalTypeVisibilityAndCreationKeepFacilityRoleAndTypeValidation(){
        UUID elsewhere=UUID.randomUUID();
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,
            assertThrows(BusinessException.class,()->service.unitTypes(manager,elsewhere)).status());
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,
            assertThrows(BusinessException.class,()->service.createUnit(manager,elsewhere,request("INACTIVE",BigDecimal.ONE,false))).status());
        StorageUnitType inactive=new StorageUnitType();inactive.setTypeId(2);inactive.setStatus("INACTIVE");
        when(types.findById(2)).thenReturn(Optional.of(inactive));
        var inactiveRequest=new StorageUnitRequest("A-2",2,"1","A","INACTIVE",BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,true,false);
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST,
            assertThrows(BusinessException.class,()->service.createUnit(manager,facilityId,inactiveRequest)).status());
        verify(units,never()).save(any());
    }
    @Test void duplicateFirstUnitNumberIsRejected(){
        when(units.existsByFacilityIdAndUnitNumberIgnoreCase(facilityId,"A-1")).thenReturn(true);
        assertEquals(org.springframework.http.HttpStatus.CONFLICT,
            assertThrows(BusinessException.class,()->service.createUnit(manager,facilityId,request("INACTIVE",BigDecimal.ONE,false))).status());
        verify(units,never()).save(any());
    }
    @Test void managerCannotCreateOutsideAssignedFacility(){
        assertThrows(BusinessException.class,()->service.createUnit(manager,UUID.randomUUID(),request("INACTIVE",BigDecimal.ONE,false)));
        verify(units,never()).save(any());
    }
    @Test void invalidDimensionsAndUnverifiedReadyAreRejected(){
        assertThrows(BusinessException.class,()->service.createUnit(manager,facilityId,request("INACTIVE",BigDecimal.ZERO,false)));
        assertThrows(BusinessException.class,()->service.createUnit(manager,facilityId,request("AVAILABLE",BigDecimal.ONE,false)));
        verify(units,never()).save(any());
    }
    @Test void maintenanceUnitRetainsCalculatedCubicVolume(){
        var unit=service.createUnit(manager,facilityId,request("MAINTENANCE",new BigDecimal("4"),false));
        assertNull(unit.volumeM3());
        assertEquals("MAINTENANCE",unit.status());
    }
    @Test void unverifiedMeasurementsDoNotExposeVolumeOrAvailability(){
        var r=new StorageUnitRequest("A-2",1,"1","A","MAINTENANCE",BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,false,false);
        assertNull(service.createUnit(manager,facilityId,r).volumeM3());
        var available=new StorageUnitRequest("A-3",1,"1","A","AVAILABLE",BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,false,true);
        assertThrows(BusinessException.class,()->service.createUnit(manager,facilityId,available));
    }
    @Test void readyUnitRequiresEffectivePositivePrice(){
        assertThrows(BusinessException.class,()->service.createUnit(manager,facilityId,request("AVAILABLE",BigDecimal.ONE,true)));
        PricingPolicy policy=new PricingPolicy();policy.setTypeId(1);policy.setStatus("ACTIVE");policy.setEffectiveFrom(LocalDate.now(Sprint2Access.ZONE));policy.setMonthlyPrice(new BigDecimal("100000"));
        when(pricing.findByFacilityId(facilityId)).thenReturn(List.of(policy));
        assertEquals(400,assertThrows(BusinessException.class,()->service.createUnit(manager,facilityId,request("AVAILABLE",BigDecimal.ONE,true))).status().value());
    }
    @Test void historicalContractBlocksPhysicalIdentityChange(){
        StorageUnit unit=new StorageUnit();unit.setUnitId(UUID.randomUUID());unit.setFacilityId(facilityId);unit.setUnitNumber("A-1");unit.setTypeId(1);unit.setFloor("1");unit.setZone("A");unit.setLengthM(BigDecimal.ONE);unit.setWidthM(new BigDecimal("2"));unit.setHeightM(new BigDecimal("3"));unit.setStatus("INACTIVE");
        when(units.findByUnitIdAndFacilityId(unit.getUnitId(),facilityId)).thenReturn(Optional.of(unit));
        when(contracts.existsByUnitId(unit.getUnitId())).thenReturn(true);
        assertThrows(BusinessException.class,()->service.updateUnit(manager,facilityId,unit.getUnitId(),request("INACTIVE",new BigDecimal("4"),false)));
        verify(units,never()).save(any());
    }
    @Test void facilityManagerPriceMutationIsForbidden(){
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,
            assertThrows(BusinessException.class,()->service.createUnitType(manager,facilityId,null)).status());
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,
            assertThrows(BusinessException.class,()->service.updateUnitType(manager,facilityId,1,null)).status());
    }
    @Test void measuredLegacyUnitCanGainVolumeWithoutChangingContractOrStatus(){
        StorageUnit unit=new StorageUnit();unit.setUnitId(UUID.randomUUID());unit.setFacilityId(facilityId);
        unit.setTypeId(1);unit.setUnitNumber("A-1");unit.setStatus("RESERVED");
        when(units.lockById(unit.getUnitId())).thenReturn(Optional.of(unit));
        when(contracts.existsByUnitId(unit.getUnitId())).thenReturn(true);
        var result=service.verifyDimensions(manager,facilityId,unit.getUnitId(),
                new DimensionVerificationRequest(new BigDecimal("2.000"),new BigDecimal("3.000"),new BigDecimal("4.000"),true));
        assertEquals("RESERVED",result.status());
        assertEquals(0,result.volumeM3().compareTo(new BigDecimal("24")));
        assertTrue(result.dimensionsVerified());assertFalse(result.readinessVerified());
        verify(units).save(unit);verify(activityLogs).save(any(ActivityLog.class));
        verifyNoInteractions(contracts);
    }
    @Test void legacyMeasurementRequiresScopePhysicalConfirmationAndNoOverwrite(){
        UUID unitId=UUID.randomUUID();
        assertEquals(403,assertThrows(BusinessException.class,()->service.verifyDimensions(manager,UUID.randomUUID(),unitId,
                new DimensionVerificationRequest(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,true))).status().value());
        assertEquals(400,assertThrows(BusinessException.class,()->service.verifyDimensions(manager,facilityId,unitId,
                new DimensionVerificationRequest(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,false))).status().value());
        StorageUnit unit=new StorageUnit();unit.setUnitId(unitId);unit.setFacilityId(facilityId);unit.setDimensionsVerified(true);
        when(units.lockById(unitId)).thenReturn(Optional.of(unit));
        assertEquals(409,assertThrows(BusinessException.class,()->service.verifyDimensions(manager,facilityId,unitId,
                new DimensionVerificationRequest(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,true))).status().value());
        verify(units,never()).save(any());verifyNoInteractions(activityLogs);
    }
}
