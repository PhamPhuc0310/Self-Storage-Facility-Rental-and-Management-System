package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.manager.MeasurementSubmissionRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.impl.UnitActivationService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnitActivationServiceTest {
    private final Sprint2Access access=mock(Sprint2Access.class);
    private final FacilityRepository facilities=mock(FacilityRepository.class);
    private final StorageUnitRepository units=mock(StorageUnitRepository.class);
    private final StorageUnitTypeRepository types=mock(StorageUnitTypeRepository.class);
    private final UserFacilityAssignmentRepository assignments=mock(UserFacilityAssignmentRepository.class);
    private final UnitMeasurementSubmissionRepository submissions=mock(UnitMeasurementSubmissionRepository.class);
    private final PricingPolicyRepository prices=mock(PricingPolicyRepository.class);
    private final RentalContractRepository contracts=mock(RentalContractRepository.class);
    private final ReservationRepository reservations=mock(ReservationRepository.class);
    private final ActivityLogRepository logs=mock(ActivityLogRepository.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final UnitActivationService service=new UnitActivationService(access,facilities,units,types,assignments,submissions,prices,contracts,reservations,logs,jdbc);
    private final UUID actor=UUID.randomUUID(), facilityId=UUID.randomUUID(), unitId=UUID.randomUUID();
    private final LocalDate today=LocalDate.of(2026,10,29);
    private StorageUnit unit;
    private Facility facility;
    @BeforeEach void setup() {
        when(access.currentId()).thenReturn(actor);when(access.today()).thenReturn(today);
        when(access.now()).thenReturn(LocalDateTime.of(2026,10,29,9,0));
        when(assignments.isUserAssignedToFacility(actor,facilityId,today)).thenReturn(true);
        facility=new Facility();facility.setFacilityId(facilityId);facility.setStatus("ACTIVE");
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));
        unit=new StorageUnit();unit.setUnitId(unitId);unit.setFacilityId(facilityId);unit.setUnitNumber("PH45-DEMO-01");unit.setTypeId(1);unit.setStatus("INACTIVE");
        when(units.lockById(unitId)).thenReturn(Optional.of(unit));
        when(units.save(any())).thenAnswer(a->a.getArgument(0));
        when(submissions.save(any())).thenAnswer(a->a.getArgument(0));
        StorageUnitType type=new StorageUnitType();type.setTypeId(1);type.setStatus("ACTIVE");when(types.findById(1)).thenReturn(Optional.of(type));
    }
    private MeasurementSubmissionRequest measurement(String source) {
        return new MeasurementSubmissionRequest(new BigDecimal("2"),new BigDecimal("3"),new BigDecimal("4"),"Kho khô sạch; dữ liệu mô phỏng",source);
    }
    @Test void staffSubmissionIsScopedAndDoesNotActivateOrVerify() {
        when(access.role()).thenReturn("FACILITY_STAFF");when(jdbc.queryForObject("SELECT DB_NAME()",String.class)).thenReturn("SelfStorageFacilityDB_Test");
        var submitted=service.submit(facilityId,unitId,measurement("SYNTHETIC_DEMO"));
        assertEquals("SUBMITTED",submitted.status);assertFalse(unit.isDimensionsVerified());assertEquals("INACTIVE",unit.getStatus());
        assertEquals(403,assertThrows(BusinessException.class,()->service.submit(UUID.randomUUID(),unitId,measurement("PHYSICAL_INSPECTION"))).status().value());
        when(submissions.existsByUnitIdAndStatus(unitId,"SUBMITTED")).thenReturn(true);
        assertEquals(409,assertThrows(BusinessException.class,()->service.submit(facilityId,unitId,measurement("SYNTHETIC_DEMO"))).status().value());
    }
    @Test void syntheticOnlyAllowedOnTestDemoInventory() {
        when(access.role()).thenReturn("FACILITY_STAFF");when(jdbc.queryForObject("SELECT DB_NAME()",String.class)).thenReturn("SelfStorageFacilityDB");
        assertEquals(403,assertThrows(BusinessException.class,()->service.submit(facilityId,unitId,measurement("SYNTHETIC_DEMO"))).status().value());
        verify(submissions,never()).save(any());
    }
    @Test void managerDecisionRecordsReasonAndVerifiedCubicVolume() {
        UnitMeasurementSubmission s=new UnitMeasurementSubmission();s.submissionId=UUID.randomUUID();s.facilityId=facilityId;s.unitId=unitId;
        s.status="SUBMITTED";s.source="SYNTHETIC_DEMO";s.lengthM=new BigDecimal("2");s.widthM=new BigDecimal("3");s.heightM=new BigDecimal("4");
        when(submissions.findById(s.submissionId)).thenReturn(Optional.of(s));
        when(access.role()).thenReturn("FACILITY_STAFF");
        assertEquals(403,assertThrows(BusinessException.class,()->service.decide(facilityId,s.submissionId,true,"Đã kiểm tra")).status().value());
        when(access.role()).thenReturn("FACILITY_MANAGER");
        var decided=service.decide(facilityId,s.submissionId,true,"Chấp nhận số đo mô phỏng cho demo");
        assertEquals("APPROVED",decided.status);assertEquals(actor,decided.reviewedBy);assertNotNull(decided.reviewedAt);
        assertEquals(0,unit.getVolumeM3().compareTo(new BigDecimal("24")));
        assertEquals("SYNTHETIC_DEMO",unit.getMeasurementSource());assertEquals("INACTIVE",unit.getStatus());
        assertEquals(409,assertThrows(BusinessException.class,()->service.decide(facilityId,s.submissionId,false,"Không hợp lệ")).status().value());
    }
    @Test void rejectionLeavesUnitUnverifiedAndInactive() {
        UnitMeasurementSubmission s=new UnitMeasurementSubmission();s.submissionId=UUID.randomUUID();s.facilityId=facilityId;s.unitId=unitId;s.status="SUBMITTED";
        when(submissions.findById(s.submissionId)).thenReturn(Optional.of(s));when(access.role()).thenReturn("FACILITY_MANAGER");
        assertEquals("REJECTED",service.decide(facilityId,s.submissionId,false,"Thiếu bằng chứng").status);
        assertFalse(unit.isDimensionsVerified());verify(units,never()).save(any());
    }
    @Test void activationChecksDimensionsReadinessMaintenancePricingAndBookings() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        var missing=service.check(facilityId,unitId);
        assertFalse(missing.eligible());assertTrue(missing.reasons().stream().anyMatch(x->x.contains("kích thước")));
        assertTrue(missing.reasons().stream().anyMatch(x->x.contains("giá thuê")));
        unit.setLengthM(BigDecimal.valueOf(2));unit.setWidthM(BigDecimal.valueOf(3));unit.setHeightM(BigDecimal.valueOf(4));
        unit.setDimensionsVerified(true);unit.setReadinessVerified(true);
        PricingPolicy p=new PricingPolicy();p.setTypeId(1);p.setStatus("ACTIVE");p.setMonthlyPrice(BigDecimal.valueOf(900000));p.setEffectiveFrom(today.minusDays(1));
        when(prices.findByFacilityIdAndStatus(facilityId,"ACTIVE")).thenReturn(List.of(p));
        assertTrue(service.check(facilityId,unitId).eligible());
        unit.setStatus("MAINTENANCE");assertFalse(service.check(facilityId,unitId).eligible());unit.setStatus("INACTIVE");
        when(reservations.countActivePendingOverlapping(eq(facilityId),eq(1),any(),any(),any())).thenReturn(1L);
        assertTrue(service.check(facilityId,unitId).reasons().stream().anyMatch(x->x.contains("giữ chỗ")));
        assertEquals(409,assertThrows(BusinessException.class,()->service.activate(facilityId,unitId)).status().value());
        verify(units,never()).save(any());
    }
    @Test void onlyScopedManagerCanActivateAndAudit() {
        unit.setLengthM(BigDecimal.valueOf(2));unit.setWidthM(BigDecimal.valueOf(3));unit.setHeightM(BigDecimal.valueOf(4));
        unit.setDimensionsVerified(true);unit.setReadinessVerified(true);
        PricingPolicy p=new PricingPolicy();p.setTypeId(1);p.setStatus("ACTIVE");p.setMonthlyPrice(BigDecimal.valueOf(900000));p.setEffectiveFrom(today);
        when(prices.findByFacilityIdAndStatus(facilityId,"ACTIVE")).thenReturn(List.of(p));
        when(access.role()).thenReturn("SYSTEM_ADMIN");
        assertEquals(403,assertThrows(BusinessException.class,()->service.activate(facilityId,unitId)).status().value());
        when(access.role()).thenReturn("FACILITY_MANAGER");
        assertEquals("AVAILABLE",service.activate(facilityId,unitId).getStatus());verify(logs).save(any(ActivityLog.class));
    }
    @Test void openOccupancyBlocksActivationEvenWhenReadinessIsVerified() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        unit.setLengthM(BigDecimal.valueOf(2));unit.setWidthM(BigDecimal.valueOf(3));unit.setHeightM(BigDecimal.valueOf(4));
        unit.setDimensionsVerified(true);unit.setReadinessVerified(true);
        PricingPolicy p=new PricingPolicy();p.setTypeId(1);p.setStatus("ACTIVE");p.setMonthlyPrice(BigDecimal.valueOf(900000));p.setEffectiveFrom(today);
        when(prices.findByFacilityIdAndStatus(facilityId,"ACTIVE")).thenReturn(List.of(p));
        when(contracts.existsByUnitIdAndStatusIn(eq(unitId),anyCollection())).thenReturn(true);
        assertFalse(service.check(facilityId,unitId).eligible());
        assertThrows(BusinessException.class,()->service.activate(facilityId,unitId));
        verify(units,never()).save(any());
    }
    @Test void zeroOrNegativeEffectivePriceNeverActivates() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        unit.setLengthM(BigDecimal.valueOf(2));unit.setWidthM(BigDecimal.valueOf(3));unit.setHeightM(BigDecimal.valueOf(4));
        unit.setDimensionsVerified(true);unit.setReadinessVerified(true);
        PricingPolicy p=new PricingPolicy();p.setTypeId(1);p.setStatus("ACTIVE");p.setMonthlyPrice(BigDecimal.ZERO);p.setEffectiveFrom(today);
        when(prices.findByFacilityIdAndStatus(facilityId,"ACTIVE")).thenReturn(List.of(p));
        assertTrue(service.check(facilityId,unitId).reasons().stream().anyMatch(x->x.contains("giá thuê")));
        assertEquals(409,assertThrows(BusinessException.class,()->service.activate(facilityId,unitId)).status().value());
    }
}
