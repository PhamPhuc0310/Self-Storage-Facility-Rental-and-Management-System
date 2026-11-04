package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.CancelReservationRequest;
import com.safebox.self_storage.dto.CreateReservationRequest;
import com.safebox.self_storage.dto.RejectReservationRequest;
import com.safebox.self_storage.dto.response.ReservationResponse;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.impl.ReservationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private UserRepository userRepository;
    @Mock private FacilityRepository facilityRepository;
    @Mock private StorageUnitTypeRepository storageUnitTypeRepository;
    @Mock private StorageUnitRepository storageUnitRepository;
    @Mock private PricingPolicyRepository pricingPolicyRepository;
    @Mock private RentalPolicyRepository rentalPolicyRepository;
    @Mock private RentalContractRepository rentalContractRepository;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    private UUID customerId;
    private UUID facilityId;
    private Integer typeId;
    private UUID pricingId;
    private User customer;
    private Facility facility;
    private StorageUnitType unitType;
    private PricingPolicy pricingPolicy;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        facilityId = UUID.randomUUID();
        typeId = 1;
        pricingId = UUID.randomUUID();

        customer = new User();
        customer.setUserId(customerId);
        customer.setFullName("Nguyen Van A");
        customer.setEmail("vana@safebox.vn");
        customer.setPhone("0901234567");
        customer.setStatus("ACTIVE");
        customer.setEmailVerified(true);

        facility = new Facility();
        facility.setFacilityId(facilityId);
        facility.setName("SafeBox Thu Duc");
        facility.setAddress("123 Vo Van Ngan");
        facility.setStatus("ACTIVE");
        lenient().when(facilityRepository.lockById(facilityId)).thenReturn(Optional.of(facility));

        unitType = new StorageUnitType();
        unitType.setTypeId(typeId);
        unitType.setTypeName("Kho Mini 10m2");
        unitType.setStorageMode("STANDARD");
        unitType.setSizeName("10 m²");
        unitType.setWidth(BigDecimal.valueOf(2.5));
        unitType.setLength(BigDecimal.valueOf(4.0));
        unitType.setStatus("ACTIVE");

        pricingPolicy = new PricingPolicy();
        pricingPolicy.setPricingId(pricingId);
        pricingPolicy.setFacilityId(facilityId);
        pricingPolicy.setTypeId(typeId);
        pricingPolicy.setMonthlyPrice(BigDecimal.valueOf(2000000));
        pricingPolicy.setDepositAmount(BigDecimal.valueOf(2000000));
        pricingPolicy.setStatus("ACTIVE");
        pricingPolicy.setEffectiveFrom(LocalDate.now().minusMonths(1));
    }

    // =========================================================================
    // UC05: Tạo yêu cầu đặt kho
    // =========================================================================
    @Test
    void createReservation_success() {
        CreateReservationRequest request = new CreateReservationRequest(
                facilityId, typeId, LocalDate.now().plusDays(2), 3, null
        );

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(typeId)).thenReturn(Optional.of(unitType));
        when(storageUnitRepository.countReadyAvailable(facilityId, typeId)).thenReturn(5L);
        when(pricingPolicyRepository.findByFacilityIdAndStatus(facilityId, "ACTIVE")).thenReturn(List.of(pricingPolicy));
        when(rentalPolicyRepository.findFirstByFacilityIdAndStatusOrderByEffectiveFromDesc(facilityId, "ACTIVE"))
                .thenReturn(Optional.empty());

        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            r.setReservationId(UUID.randomUUID());
            return r;
        });

        ReservationResponse response = reservationService.createReservation(customerId, request);

        assertNotNull(response);
        assertNotNull(response.reservationId());
        assertEquals("PENDING", response.status());
        assertEquals("Chờ xác nhận", response.statusLabel());
        assertEquals(BigDecimal.valueOf(6600000), response.estimatedRentalAmount());
        assertEquals(BigDecimal.valueOf(2200000), response.estimatedDepositAmount());
        assertEquals(3, response.rentalMonths());
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }

    @Test
    void createReservation_retryReturnsExistingPendingRequest() {
        LocalDate start = LocalDate.now(com.safebox.self_storage.service.Sprint2Access.ZONE).plusDays(2);
        CreateReservationRequest request = new CreateReservationRequest(facilityId, typeId, start, 1, null);
        Reservation previous = new Reservation();
        previous.setReservationId(UUID.randomUUID());
        previous.setCustomerId(customerId);
        previous.setFacilityId(facilityId);
        previous.setTypeId(typeId);
        previous.setPricingId(pricingId);
        previous.setStartDate(start);
        previous.setEndDate(start.plusMonths(1));
        previous.setStatus("PENDING");
        previous.setHoldExpiresAt(LocalDateTime.now(com.safebox.self_storage.service.Sprint2Access.ZONE).plusHours(1));
        previous.setCreatedAt(LocalDateTime.now());
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(typeId)).thenReturn(Optional.of(unitType));
        when(reservationRepository.findActiveExactRequest(customerId, facilityId, typeId, start, start.plusMonths(1)))
                .thenReturn(List.of(previous));
        when(pricingPolicyRepository.findById(pricingId)).thenReturn(Optional.of(pricingPolicy));

        ReservationResponse response = reservationService.createReservation(customerId, request);
        assertEquals(previous.getReservationId(), response.reservationId());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_customerInactive_throwsException() {
        customer.setStatus("LOCKED");
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));

        CreateReservationRequest request = new CreateReservationRequest(
                facilityId, typeId, LocalDate.now().plusDays(2), 1, null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reservationService.createReservation(customerId, request));
        assertTrue(ex.getMessage().contains("khóa"));
    }

    @Test
    void createReservation_emailNotVerified_throwsException() {
        customer.setEmailVerified(false);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));

        CreateReservationRequest request = new CreateReservationRequest(
                facilityId, typeId, LocalDate.now().plusDays(2), 1, null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reservationService.createReservation(customerId, request));
        assertTrue(ex.getMessage().contains("xác thực email"));
    }

    @Test
    void createReservation_startDateInPast_throwsException() {
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(typeId)).thenReturn(Optional.of(unitType));

        CreateReservationRequest request = new CreateReservationRequest(
                facilityId, typeId, LocalDate.now().minusDays(1), 1, null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                reservationService.createReservation(customerId, request));
        assertTrue(ex.getMessage().contains("quá khứ"));
    }

    @Test
    void createReservation_noAvailableUnits_throwsException() {
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(typeId)).thenReturn(Optional.of(unitType));
        when(storageUnitRepository.countReadyAvailable(facilityId, typeId)).thenReturn(0L);

        CreateReservationRequest request = new CreateReservationRequest(
                facilityId, typeId, LocalDate.now().plusDays(1), 1, null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reservationService.createReservation(customerId, request));
        assertTrue(ex.getMessage().contains("không còn"));
    }

    @Test
    void createReservation_contractConflictsAndPendingHoldsConsumeCapacity() {
        LocalDate start = LocalDate.now(Sprint2Access.ZONE).plusDays(2);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(typeId)).thenReturn(Optional.of(unitType));
        when(storageUnitRepository.countReadyAvailable(facilityId, typeId)).thenReturn(1L);
        when(storageUnitRepository.countContractedAvailable(facilityId, typeId, start, start.plusMonths(1)))
                .thenReturn(1L, 0L);
        when(reservationRepository.countActivePendingOverlapping(eq(facilityId), eq(typeId), eq(start), eq(start.plusMonths(1)), any()))
                .thenReturn(0L, 1L);
        CreateReservationRequest request = new CreateReservationRequest(facilityId, typeId, start, 1, null);
        assertThrows(IllegalStateException.class, () -> reservationService.createReservation(customerId, request));
        assertThrows(IllegalStateException.class, () -> reservationService.createReservation(customerId, request));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_unpricedUnitRemainsUnbookable() {
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(typeId)).thenReturn(Optional.of(unitType));
        when(storageUnitRepository.countReadyAvailable(facilityId,typeId)).thenReturn(1L);
        when(pricingPolicyRepository.findByFacilityIdAndStatus(facilityId,"ACTIVE")).thenReturn(List.of());
        CreateReservationRequest request=new CreateReservationRequest(facilityId,typeId,LocalDate.now().plusDays(2),1,null);
        assertThrows(NoSuchElementException.class,()->reservationService.createReservation(customerId,request));
        verify(reservationRepository,never()).save(any());
    }

    @Test
    void createReservation_usesPriceEffectiveOnRentalStartDate() {
        LocalDate start=LocalDate.now().plusDays(3);
        PricingPolicy future=new PricingPolicy();future.setPricingId(UUID.randomUUID());future.setFacilityId(facilityId);
        future.setTypeId(typeId);future.setStatus("ACTIVE");future.setMonthlyPrice(new BigDecimal("3000000"));
        future.setEffectiveFrom(start.plusDays(1));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(typeId)).thenReturn(Optional.of(unitType));
        when(storageUnitRepository.countReadyAvailable(facilityId,typeId)).thenReturn(1L);
        when(pricingPolicyRepository.findByFacilityIdAndStatus(facilityId,"ACTIVE")).thenReturn(List.of(future));
        CreateReservationRequest request=new CreateReservationRequest(facilityId,typeId,start,1,null);
        assertThrows(NoSuchElementException.class,()->reservationService.createReservation(customerId,request));
        verify(reservationRepository,never()).save(any());
    }

    // =========================================================================
    // UC06: Xem và hủy yêu cầu của tôi
    // =========================================================================
    @Test
    void getMyReservations_filtersAndReturnsList() {
        Reservation r = new Reservation();
        r.setReservationId(UUID.randomUUID());
        r.setCustomerId(customerId);
        r.setFacilityId(facilityId);
        r.setTypeId(typeId);
        r.setPricingId(pricingId);
        r.setStartDate(LocalDate.now());
        r.setEndDate(LocalDate.now().plusMonths(1));
        r.setStatus("PENDING");
        r.setEstimatedRentalAmount(BigDecimal.valueOf(2000000));
        r.setEstimatedDepositAmount(BigDecimal.valueOf(2000000));
        r.setCreatedAt(LocalDateTime.now());

        when(reservationRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, "PENDING"))
                .thenReturn(List.of(r));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(facilityRepository.findById(facilityId)).thenReturn(Optional.of(facility));
        when(storageUnitTypeRepository.findById(typeId)).thenReturn(Optional.of(unitType));
        when(pricingPolicyRepository.findById(pricingId)).thenReturn(Optional.of(pricingPolicy));

        List<ReservationResponse> responses = reservationService.getMyReservations(customerId, "PENDING", null);
        assertEquals(1, responses.size());
        assertEquals("PENDING", responses.get(0).status());
    }

    @Test
    void cancelMyReservation_success() {
        UUID reservationId = UUID.randomUUID();
        Reservation r = new Reservation();
        r.setReservationId(reservationId);
        r.setCustomerId(customerId);
        r.setFacilityId(facilityId);
        r.setTypeId(typeId);
        r.setPricingId(pricingId);
        r.setStartDate(LocalDate.now());
        r.setEndDate(LocalDate.now().plusMonths(1));
        r.setStatus("PENDING");
        r.setCreatedAt(LocalDateTime.now());

        when(reservationRepository.lockById(reservationId))
                .thenReturn(Optional.of(r));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse response = reservationService.cancelMyReservation(
                customerId, reservationId, new CancelReservationRequest("Đổi kế hoạch cá nhân")
        );

        assertEquals("CANCELLED", response.status());
        assertEquals("Đổi kế hoạch cá nhân", response.cancellationReason());
        assertNotNull(response.cancelledAt());
    }

    @Test
    void cancelMyReservation_alreadyCancelled_throwsException() {
        UUID reservationId = UUID.randomUUID();
        Reservation r = new Reservation();
        r.setReservationId(reservationId);
        r.setCustomerId(customerId);
        r.setStatus("CANCELLED");

        when(reservationRepository.lockById(reservationId))
                .thenReturn(Optional.of(r));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reservationService.cancelMyReservation(customerId, reservationId, new CancelReservationRequest("test")));
        assertTrue(ex.getMessage().contains("đã được hủy"));
    }
}
