package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.HandoverRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HandoverServiceTest {
    @Mock Sprint2Access access;
    @Mock PickupAppointmentRepository appointments;
    @Mock HandoverRecordRepository handovers;
    @Mock InspectionReportRepository inspections;
    @Mock RentalContractRepository contracts;
    @Mock StorageUnitRepository units;
    @Mock PaymentService payments;
    HandoverService service;
    UUID contractId = UUID.randomUUID(), unitId = UUID.randomUUID(), facilityId = UUID.randomUUID(), staffId = UUID.randomUUID();
    RentalContract contract;
    StorageUnit unit;

    @BeforeEach void setup() {
        service = new HandoverService(access, appointments, handovers, inspections, contracts, units, payments);
        contract = new RentalContract(); contract.contractId = contractId; contract.unitId = unitId;
        contract.startDate = LocalDate.of(2026, 10, 1); contract.endDate = LocalDate.of(2026, 11, 1);
        contract.status = "CONFIRMED";
        unit = new StorageUnit(); unit.setUnitId(unitId); unit.setFacilityId(facilityId); unit.setStatus("RESERVED");
    }

    @Test void reservedApprovalUnitBecomesOccupiedOnlyAfterHandover() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 9, 0);
        PickupAppointment appointment = new PickupAppointment(); appointment.appointmentTime = now;
        appointment.facilityId = facilityId; appointment.assignedStaffId = staffId; appointment.status = "SCHEDULED";
        when(access.contract(contractId, true)).thenReturn(contract);
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(staffId);
        when(access.facilityId(contract)).thenReturn(facilityId);
        when(access.now()).thenReturn(now);
        when(appointments.findFirstByContractIdAndStatus(contractId, "SCHEDULED")).thenReturn(Optional.of(appointment));
        PaymentService.Component paid = new PaymentService.Component(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, "PAID");
        when(payments.summary(contractId)).thenReturn(new PaymentService.Summary(contractId, paid, paid, true));
        when(units.lockById(unitId)).thenReturn(Optional.of(unit));
        when(handovers.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        HandoverRecord record = service.complete(contractId, new HandoverRequest("GOOD", "PIN", "1234",
                "Customer", "Staff", null, null, null, BigDecimal.ZERO));

        assertEquals("COMPLETED", record.getStatus());
        assertEquals("ACTIVE", contract.status);
        assertEquals("OCCUPIED", unit.getStatus());
        assertEquals("COMPLETED", appointment.status);
        verify(inspections).save(any());
    }

    @Test void availableUnitCannotBeHandedOverAsIfReserved() {
        unit.setStatus("AVAILABLE");
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 9, 0);
        PickupAppointment appointment = new PickupAppointment(); appointment.appointmentTime = now;
        appointment.facilityId = facilityId; appointment.assignedStaffId = staffId;
        when(access.contract(contractId, true)).thenReturn(contract);
        when(access.role()).thenReturn("FACILITY_STAFF");
        when(access.currentId()).thenReturn(staffId);
        when(access.facilityId(contract)).thenReturn(facilityId);
        when(access.now()).thenReturn(now);
        when(appointments.findFirstByContractIdAndStatus(contractId, "SCHEDULED")).thenReturn(Optional.of(appointment));
        PaymentService.Component paid = new PaymentService.Component(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, "PAID");
        when(payments.summary(contractId)).thenReturn(new PaymentService.Summary(contractId, paid, paid, true));
        when(units.lockById(unitId)).thenReturn(Optional.of(unit));

        BusinessException error = assertThrows(BusinessException.class, () -> service.complete(contractId,
                new HandoverRequest("GOOD", "PIN", "1234", "Customer", "Staff", null, null, null, BigDecimal.ZERO)));
        assertEquals(409, error.status().value());
        verify(handovers, never()).saveAndFlush(any());
    }
}
