package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.RentalContract;
import com.safebox.self_storage.entity.StorageUnit;
import com.safebox.self_storage.repository.RentalContractRepository;
import com.safebox.self_storage.repository.StorageUnitRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:booking_inventory;MODE=MSSQLServer;INIT=CREATE SCHEMA IF NOT EXISTS dbo",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.properties.hibernate.default_schema=dbo",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BookingInventoryRegressionTest {
    @Autowired StorageUnitRepository units;
    @Autowired RentalContractRepository contracts;
    final UUID facility = UUID.randomUUID();
    final LocalDate start = LocalDate.of(2027, 1, 1), end = start.plusMonths(1);

    StorageUnit unit(String status) {
        StorageUnit u = new StorageUnit();
        u.setFacilityId(facility); u.setTypeId(1); u.setUnitNumber(UUID.randomUUID().toString()); u.setStatus(status);
        return units.saveAndFlush(u);
    }
    void contract(StorageUnit u, String status, LocalDate from, LocalDate to) {
        RentalContract c = new RentalContract();
        c.contractNumber = UUID.randomUUID().toString(); c.reservationId = UUID.randomUUID();
        c.customerId = UUID.randomUUID(); c.unitId = u.getUnitId(); c.status = status;
        c.startDate = from; c.endDate = to; c.monthlyRentalFee = BigDecimal.TEN;
        c.depositAmount = BigDecimal.TEN; c.billingCycle = "MONTHLY"; c.termsVersion = "v2";
        c.createdAt = LocalDateTime.now(); contracts.saveAndFlush(c);
    }
    @Test void historicalAvailableInventoryIsBookableWithoutFabricatedVolume() {
        StorageUnit legacy = unit("AVAILABLE");
        unit("MAINTENANCE"); unit("OCCUPIED"); unit("RESERVED"); unit("INACTIVE");
        assertNull(legacy.getVolumeM3());
        assertEquals(1, units.countReadyAvailable(facility, 1));
        assertEquals(1, units.countAvailableUnitsByFacilityAndType(facility, 1));
        assertEquals(1, units.countAvailableUnitsByFacility(facility));
        assertEquals(java.util.List.of(1), units.findAvailableTypeIdsByFacility(facility));
        assertEquals(legacy.getUnitId(), units.lockFirstAvailable(facility, 1, start, end).orElseThrow().getUnitId());
    }
    @Test void activeAndOverlappingContractsStillBlockAllocation() {
        StorageUnit active = unit("AVAILABLE"), overlapping = unit("AVAILABLE");
        contract(active, "ACTIVE", start.minusMonths(3), start.minusMonths(2));
        contract(overlapping, "CONFIRMED", start.plusDays(1), end.plusDays(1));
        assertEquals(1, units.countReadyAvailable(facility, 1));
        assertEquals(1, units.countContractedAvailable(facility, 1, start, end));
        assertTrue(units.lockFirstAvailable(facility, 1, start, end).isEmpty());
        assertEquals(overlapping.getUnitId(), units.lockFirstAvailable(facility, 1, end.plusDays(1), end.plusMonths(1)).orElseThrow().getUnitId());
    }
}
