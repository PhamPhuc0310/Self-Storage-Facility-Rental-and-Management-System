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
        when(storageUnitRepository.countAvailableUnitsByFacilityAndType(facilityId, typeId)).thenReturn(5L);
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
        assertEquals(BigDecimal.valueOf(6000000), response.estimatedRentalAmount());
        assertEquals(BigDecimal.valueOf(2000000), response.estimatedDepositAmount());
        assertEquals(3, response.rentalMonths());
        verify(reservationRepository, times(1)).save(any(Reservation.class));
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
        when(storageUnitRepository.countAvailableUnitsByFacilityAndType(facilityId, typeId)).thenReturn(0L);

        CreateReservationRequest request = new CreateReservationRequest(
                facilityId, typeId, LocalDate.now().plusDays(1), 1, null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reservationService.createReservation(customerId, request));
        assertTrue(ex.getMessage().contains("không còn"));
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

        when(reservationRepository.findByReservationIdAndCustomerId(reservationId, customerId))
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

        when(reservationRepository.findByReservationIdAndCustomerId(reservationId, customerId))
                .thenReturn(Optional.of(r));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reservationService.cancelMyReservation(customerId, reservationId, new CancelReservationRequest("test")));
        assertTrue(ex.getMessage().contains("đã được hủy"));
    }

    // =========================================================================
    // UC08: Xác nhận hoặc từ chối yêu cầu đặt kho
    // =========================================================================
    @Test
    void approveReservation_success() {
        UUID reservationId = UUID.randomUUID();
        Reservation r = new Reservation();
        r.setReservationId(reservationId);
        r.setCustomerId(customerId);
        r.setFacilityId(facilityId);
        r.setTypeId(typeId);
        r.setPricingId(pricingId);
        r.setStartDate(LocalDate.now());
        r.setEndDate(LocalDate.now().plusMonths(2));
        r.setStatus("PENDING");
        r.setCreatedAt(LocalDateTime.now());

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        when(storageUnitRepository.countAvailableUnitsByFacilityAndType(facilityId, typeId)).thenReturn(3L);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse response = reservationService.approveReservation(reservationId);

        assertEquals("CONFIRMED", response.status());
        assertEquals("Đã xác nhận", response.statusLabel());
    }

    @Test
    void approveReservation_notPending_throwsException() {
        UUID reservationId = UUID.randomUUID();
        Reservation r = new Reservation();
        r.setReservationId(reservationId);
        r.setStatus("CONFIRMED");

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reservationService.approveReservation(reservationId));
        assertTrue(ex.getMessage().contains("Chỉ có thể xác nhận"));
    }

    @Test
    void approveReservation_noAvailableUnits_throwsException() {
        UUID reservationId = UUID.randomUUID();
        Reservation r = new Reservation();
        r.setReservationId(reservationId);
        r.setFacilityId(facilityId);
        r.setTypeId(typeId);
        r.setStatus("PENDING");

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        when(storageUnitRepository.countAvailableUnitsByFacilityAndType(facilityId, typeId)).thenReturn(0L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reservationService.approveReservation(reservationId));
        assertTrue(ex.getMessage().contains("không còn ô kho trống"));
    }

    @Test
    void rejectReservation_success() {
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

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse response = reservationService.rejectReservation(
                reservationId, new RejectReservationRequest("Không đáp ứng điều kiện lưu trữ")
        );

        assertEquals("REJECTED", response.status());
        assertEquals("Bị từ chối", response.statusLabel());
        assertEquals("Không đáp ứng điều kiện lưu trữ", response.cancellationReason());
        assertNotNull(response.cancelledAt());
    }

    @Test
    void rejectReservation_notPending_throwsException() {
        UUID reservationId = UUID.randomUUID();
        Reservation r = new Reservation();
        r.setReservationId(reservationId);
        r.setStatus("CANCELLED");

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reservationService.rejectReservation(reservationId, new RejectReservationRequest("test")));
        assertTrue(ex.getMessage().contains("Chỉ có thể từ chối"));
    }
}
