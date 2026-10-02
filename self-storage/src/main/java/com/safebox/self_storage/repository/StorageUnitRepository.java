package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.StorageUnit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StorageUnitRepository extends JpaRepository<StorageUnit, UUID> {

    @Query("SELECT COUNT(u) FROM StorageUnit u WHERE u.facilityId = :facilityId AND u.status = 'AVAILABLE'")
    long countAvailableUnitsByFacility(@Param("facilityId") UUID facilityId);
    
    @Query("SELECT COUNT(u) FROM StorageUnit u WHERE u.facilityId = :facilityId AND u.typeId = :typeId AND u.status = 'AVAILABLE'")
    long countAvailableUnitsByFacilityAndType(@Param("facilityId") UUID facilityId, @Param("typeId") Integer typeId);
}
