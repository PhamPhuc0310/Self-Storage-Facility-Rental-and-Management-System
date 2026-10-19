package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.dto.RejectReservationRequest;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationApprovalServiceTest {
    @Mock Sprint2Access access;
    @Mock ReservationRepository reservations;
    @Mock RentalContractRepository contracts;
    @Mock InvoiceRepository invoices;
    @Mock StorageUnitRepository units;
    @Mock StorageUnitTypeRepository types;
    @Mock FacilityRepository facilities;
    @Mock UserRepository users;
    ReservationApprovalService service;
    UUID reservationId = UUID.randomUUID(), facilityId = UUID.randomUUID(), customerId = UUID.randomUUID(), managerId = UUID.randomUUID();
    Reservation reservation;

    @BeforeEach void setUp() {
        service = new ReservationApprovalService(access,reservations,contracts,invoices,units,types,facilities,users);
        reservation = new Reservation();
        reservation.setReservationId(reservationId); reservation.setFacilityId(facilityId);
        reservation.setCustomerId(customerId); reservation.setTypeId(3);
        reservation.setStartDate(LocalDate.of(2026,11,1)); reservation.setEndDate(LocalDate.of(2027,1,1));
        reservation.setQuotedMonthlyPrice(new BigDecimal("4500000.00"));
        reservation.setEstimatedDepositAmount(new BigDecimal("2000000.00"));
        reservation.setPricingVersion("v2");
        reservation.setStatus("PENDING");
    }
    void managerAccess() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(reservations.lockById(reservationId)).thenReturn(Optional.of(reservation));
        when(access.facilities()).thenReturn(List.of(facilityId));
    }
    void validPending() {
        managerAccess();
        Role role = new Role(); role.setRoleName("CUSTOMER");
        User customer = new User(); customer.setStatus("ACTIVE"); customer.setRole(role);
        when(users.findById(customerId)).thenReturn(Optional.of(customer));
        Facility facility = new Facility(); facility.setStatus("ACTIVE");
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));
        StorageUnitType type = new StorageUnitType(); type.setStatus("ACTIVE");
        when(types.findById(3)).thenReturn(Optional.of(type));
        when(access.today()).thenReturn(LocalDate.of(2026,10,11));
        when(units.lockFirstAvailable(facilityId,3)).thenReturn(Optional.empty());
    }
    StorageUnit availableUnit() {
        StorageUnit unit = new StorageUnit(); unit.setUnitId(UUID.randomUUID());
        unit.setFacilityId(facilityId); unit.setTypeId(3); unit.setStatus("AVAILABLE");
        when(units.lockFirstAvailable(facilityId,3)).thenReturn(Optional.of(unit));
        when(access.now()).thenReturn(LocalDateTime.of(2026,10,11,10,0));
        when(access.currentId()).thenReturn(managerId);
        return unit;
    }
    void generatedContractId() {
        when(contracts.saveAndFlush(any())).thenAnswer(call -> {
            RentalContract original = call.getArgument(0);
            assertNull(original.contractId);
            RentalContract saved = new RentalContract();
            saved.contractId = UUID.randomUUID();
            saved.contractNumber = original.contractNumber;
            saved.reservationId = original.reservationId;
            saved.customerId = original.customerId;
            saved.unitId = original.unitId;
            saved.startDate = original.startDate;
            saved.endDate = original.endDate;
            saved.monthlyRentalFee = original.monthlyRentalFee;
            saved.depositAmount = original.depositAmount;
            saved.billingCycle = original.billingCycle;
            saved.termsVersion = original.termsVersion;
            saved.status = original.status;
            return saved;
        });
    }
    @Test void pendingUsesOnlyAssignedFacilities() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of(facilityId));
        assertTrue(service.pending().isEmpty());
        verify(reservations).findByStatusAndFacilityIdInOrderByCreatedAtDesc("PENDING", List.of(facilityId));
        verify(reservations, never()).findByStatusOrderByCreatedAtDesc(anyString());
    }
    @Test void managerWithoutAssignmentSeesNoPendingReservations() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(access.facilities()).thenReturn(List.of());
        assertTrue(service.pending().isEmpty());
        verifyNoInteractions(reservations);
    }
    @Test void assignedManagerRejectsPendingWithoutCreatingContract() {
        managerAccess();
        when(access.now()).thenReturn(LocalDateTime.of(2026,10,11,12,0));
        service.reject(reservationId, new RejectReservationRequest("  Không phù hợp  "));
        assertEquals("REJECTED", reservation.getStatus());
        assertEquals("Không phù hợp", reservation.getCancellationReason());
        assertNotNull(reservation.getCancelledAt());
        verify(reservations).saveAndFlush(reservation);
        verifyNoInteractions(invoices,units);
    }
    @Test void managerCannotRejectForeignFacility() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(reservations.lockById(reservationId)).thenReturn(Optional.of(reservation));
        when(access.facilities()).thenReturn(List.of(UUID.randomUUID()));
        assertEquals(403,assertThrows(BusinessException.class,() -> service.reject(reservationId,null)).status().value());
        verify(reservations,never()).saveAndFlush(any());
    }
    @Test void confirmationCreatesContractAndTwoUnpaidInvoicesFromSnapshot() {
        managerAccess();
        Role role = new Role(); role.setRoleName("CUSTOMER");
        User customer = new User(); customer.setStatus("ACTIVE"); customer.setRole(role);
        when(users.findById(customerId)).thenReturn(Optional.of(customer));
        Facility facility = new Facility(); facility.setStatus("ACTIVE");
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));
        StorageUnitType type = new StorageUnitType(); type.setStatus("ACTIVE");
        when(types.findById(3)).thenReturn(Optional.of(type));
        when(access.today()).thenReturn(LocalDate.of(2026,10,11));
        when(access.now()).thenReturn(LocalDateTime.of(2026,10,11,10,0));
        when(access.currentId()).thenReturn(managerId);
        StorageUnit unit = new StorageUnit(); unit.setUnitId(UUID.randomUUID()); unit.setFacilityId(facilityId); unit.setTypeId(3); unit.setStatus("AVAILABLE");
        when(units.lockFirstAvailable(facilityId,3)).thenReturn(Optional.of(unit));
        generatedContractId();
        RentalContract contract = service.approve(reservationId);
        assertEquals(reservationId,contract.reservationId);
        assertEquals(customerId,contract.customerId);
        assertEquals(unit.getUnitId(),contract.unitId);
        assertEquals(new BigDecimal("4500000.00"),contract.monthlyRentalFee);
        assertEquals("v2", contract.termsVersion);
        assertEquals(new BigDecimal("3300000"), contract.depositAmount);
        assertEquals("CONFIRMED",contract.status);
        assertEquals("CONFIRMED",reservation.getStatus());
        assertEquals("RESERVED",unit.getStatus());
        org.mockito.ArgumentCaptor<Invoice> captured = org.mockito.ArgumentCaptor.forClass(Invoice.class);
        verify(invoices,times(2)).save(captured.capture());
        Map<String,Invoice> byType = new HashMap<>();
        captured.getAllValues().forEach(i -> byType.put(i.invoiceType,i));
        assertEquals(Set.of("DEPOSIT","REMAINING_RENT"),byType.keySet());
        assertEquals(new BigDecimal("3300000"),byType.get("DEPOSIT").totalAmount);
        assertEquals(new BigDecimal("6600000"),byType.get("REMAINING_RENT").totalAmount);
        captured.getAllValues().forEach(i -> { assertEquals(contract.contractId,i.contractId); assertEquals("UNPAID",i.status); assertEquals(BigDecimal.ZERO,i.paidAmount); });
    }
    @Test void repeatedConfirmationReturnsExistingContractWithoutNewInvoices() {
        managerAccess(); reservation.setStatus("CONFIRMED");
        RentalContract existing = new RentalContract(); existing.contractId = UUID.randomUUID();
        when(contracts.findByReservationId(reservationId)).thenReturn(Optional.of(existing));
        assertSame(existing,service.approve(reservationId));
        verifyNoInteractions(invoices,units);
    }
    @Test void oldPendingReservationKeepsItsQuotedDepositAndInvoiceType() {
        reservation.setPricingVersion("v1");
        reservation.setQuotedMonthlyPrice(null);
        reservation.setEstimatedRentalAmount(new BigDecimal("9000000.00"));
        managerAccess();
        Role role = new Role(); role.setRoleName("CUSTOMER");
        User customer = new User(); customer.setStatus("ACTIVE"); customer.setRole(role);
        when(users.findById(customerId)).thenReturn(Optional.of(customer));
        Facility facility = new Facility(); facility.setStatus("ACTIVE");
        when(facilities.findById(facilityId)).thenReturn(Optional.of(facility));
        StorageUnitType type = new StorageUnitType(); type.setStatus("ACTIVE");
        when(types.findById(3)).thenReturn(Optional.of(type));
        when(access.today()).thenReturn(LocalDate.of(2026,10,11));
        when(access.now()).thenReturn(LocalDateTime.of(2026,10,11,10,0));
        StorageUnit unit = new StorageUnit(); unit.setUnitId(UUID.randomUUID());
        when(units.lockFirstAvailable(facilityId,3)).thenReturn(Optional.of(unit));
        generatedContractId();
        RentalContract contract = service.approve(reservationId);
        assertEquals("v1", contract.termsVersion);
        assertEquals(new BigDecimal("4500000.00"), contract.monthlyRentalFee);
        assertEquals(new BigDecimal("2000000.00"), contract.depositAmount);
        org.mockito.ArgumentCaptor<Invoice> saved = org.mockito.ArgumentCaptor.forClass(Invoice.class);
        verify(invoices,times(2)).save(saved.capture());
        assertEquals(Set.of("DEPOSIT","INITIAL_RENT"), saved.getAllValues().stream().map(i -> i.invoiceType).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void cancelledReservationCannotBeConfirmed() {
        managerAccess(); reservation.setStatus("CANCELLED");
        assertEquals(409,assertThrows(BusinessException.class,() -> service.approve(reservationId)).status().value());
        verifyNoInteractions(invoices,contracts,units);
    }
    @Test void managerCannotConfirmForeignFacility() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        when(reservations.lockById(reservationId)).thenReturn(Optional.of(reservation));
        when(access.facilities()).thenReturn(List.of(UUID.randomUUID()));
        assertEquals(403,assertThrows(BusinessException.class,() -> service.approve(reservationId)).status().value());
        verifyNoInteractions(invoices,contracts,units);
    }
    @Test void customerCannotConfirm() {
        when(access.role()).thenReturn("CUSTOMER");
        assertEquals(403,assertThrows(BusinessException.class,() -> service.approve(reservationId)).status().value());
        verifyNoInteractions(reservations);
    }
    @Test void missingReservationReturnsNotFound() {
        when(access.role()).thenReturn("FACILITY_MANAGER");
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.approve(reservationId)).status().value());
        verifyNoInteractions(units, contracts, invoices);
    }
    @Test void noAvailableUnitReturnsConflictWithoutWrites() {
        validPending();
        BusinessException error = assertThrows(BusinessException.class, () -> service.approve(reservationId));
        assertEquals(409, error.status().value());
        assertEquals("Không còn ô kho phù hợp.", error.getMessage());
        assertEquals("PENDING", reservation.getStatus());
        verify(units, never()).save(any());
        verify(contracts, never()).saveAndFlush(any());
        verifyNoInteractions(invoices);
    }
    @Test void contractPersistenceFailureStopsInvoicesAndStatusChange() {
        validPending(); availableUnit();
        when(contracts.saveAndFlush(any())).thenThrow(new IllegalStateException("contract persistence failed"));
        assertThrows(IllegalStateException.class, () -> service.approve(reservationId));
        assertEquals("PENDING", reservation.getStatus());
        verifyNoInteractions(invoices);
        verify(reservations, never()).saveAndFlush(any());
    }
    @Test void invoicePersistenceFailureStopsStatusChange() {
        validPending(); availableUnit(); generatedContractId();
        when(invoices.save(any())).thenThrow(new IllegalStateException("invoice persistence failed"));
        assertThrows(IllegalStateException.class, () -> service.approve(reservationId));
        assertEquals("PENDING", reservation.getStatus());
        verify(reservations, never()).saveAndFlush(any());
    }
}
