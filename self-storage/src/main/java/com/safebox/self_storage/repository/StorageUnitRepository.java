package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.StorageUnit;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface StorageUnitRepository extends JpaRepository<StorageUnit, UUID> {
    boolean existsByFacilityId(UUID facilityId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from StorageUnit u where u.unitId = :id")
    Optional<StorageUnit> lockById(@Param("id") UUID id);
    @Query(value = "SELECT TOP (1) u.* FROM dbo.STORAGE_UNITS u WITH (UPDLOCK, HOLDLOCK) WHERE u.facility_id = :facilityId AND u.type_id = :typeId AND u.status = 'AVAILABLE' AND NOT EXISTS (SELECT 1 FROM dbo.RENTAL_CONTRACTS c WHERE c.unit_id=u.unit_id AND (c.status IN ('ACTIVE','OVERDUE') OR (c.status NOT IN ('CANCELLED','TERMINATED','RETURNED','COMPLETED','CLOSED') AND c.start_date<:endDate AND c.end_date>:startDate))) ORDER BY u.unit_number", nativeQuery = true)
    Optional<StorageUnit> lockFirstAvailable(@Param("facilityId") UUID facilityId, @Param("typeId") Integer typeId,
            @Param("startDate") java.time.LocalDate startDate,@Param("endDate") java.time.LocalDate endDate);

    @Query("SELECT DISTINCT u.typeId FROM StorageUnit u WHERE u.facilityId = :facilityId AND u.status = 'AVAILABLE' AND NOT EXISTS (SELECT c FROM RentalContract c WHERE c.unitId=u.unitId AND c.status IN ('ACTIVE','OVERDUE')) ORDER BY u.typeId")
    List<Integer> findAvailableTypeIdsByFacility(@Param("facilityId") UUID facilityId);

    @Query("SELECT COUNT(u) FROM StorageUnit u WHERE u.facilityId = :facilityId AND u.status = 'AVAILABLE' AND NOT EXISTS (SELECT c FROM RentalContract c WHERE c.unitId=u.unitId AND c.status IN ('ACTIVE','OVERDUE'))")
    long countAvailableUnitsByFacility(@Param("facilityId") UUID facilityId);
    
    @Query("SELECT COUNT(u) FROM StorageUnit u WHERE u.facilityId = :facilityId AND u.typeId = :typeId AND u.status = 'AVAILABLE' AND NOT EXISTS (SELECT c FROM RentalContract c WHERE c.unitId=u.unitId AND c.status IN ('ACTIVE','OVERDUE'))")
    long countAvailableUnitsByFacilityAndType(@Param("facilityId") UUID facilityId, @Param("typeId") Integer typeId);

    @Query("SELECT COUNT(u) FROM StorageUnit u WHERE u.facilityId=:facilityId AND u.typeId=:typeId "
            + "AND u.status='AVAILABLE' "
            + "AND NOT EXISTS (SELECT c FROM RentalContract c WHERE c.unitId=u.unitId AND c.status IN ('ACTIVE','OVERDUE'))")
    long countReadyAvailable(@Param("facilityId") UUID facilityId,@Param("typeId") Integer typeId);

    @Query("SELECT COUNT(DISTINCT u.unitId) FROM StorageUnit u, RentalContract c WHERE "
            + "u.facilityId=:facilityId AND u.typeId=:typeId AND u.status='AVAILABLE' "
            + "AND NOT EXISTS (SELECT openC FROM RentalContract openC WHERE openC.unitId=u.unitId AND openC.status IN ('ACTIVE','OVERDUE')) "
            + "AND u.unitId=c.unitId AND c.status NOT IN ('CANCELLED','TERMINATED','RETURNED','COMPLETED','CLOSED') "
            + "AND c.startDate<:endDate AND c.endDate>:startDate")
    long countContractedAvailable(@Param("facilityId") UUID facilityId,@Param("typeId") Integer typeId,
            @Param("startDate") java.time.LocalDate startDate,@Param("endDate") java.time.LocalDate endDate);

    boolean existsByFacilityIdAndTypeId(UUID facilityId, Integer typeId);
    boolean existsByTypeIdAndFacilityIdNot(Integer typeId, UUID facilityId);
    boolean existsByFacilityIdAndUnitNumberIgnoreCase(UUID facilityId, String unitNumber);
    Optional<StorageUnit> findByUnitIdAndFacilityId(UUID unitId, UUID facilityId);
    List<StorageUnit> findByFacilityIdOrderByUnitNumberAsc(UUID facilityId);
    List<StorageUnit> findByFacilityIdAndTypeIdAndStatusOrderByUnitNumberAsc(UUID facilityId, Integer typeId, String status);
}
