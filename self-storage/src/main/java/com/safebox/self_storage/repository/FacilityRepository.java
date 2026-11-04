package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.Facility;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface FacilityRepository extends JpaRepository<Facility, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Facility f where f.facilityId = :id")
    java.util.Optional<Facility> lockById(@Param("id") UUID id);
    boolean existsByFacilityCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCaseAndAddressIgnoreCase(String name, String address);
    java.util.Optional<Facility> findFirstByNameIgnoreCaseAndAddressIgnoreCase(String name, String address);
    java.util.List<Facility> findByRegionCodeIgnoreCase(String regionCode);

    @Query("SELECT f FROM Facility f WHERE f.status = 'ACTIVE' AND " +
           "(:search IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(f.address) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Facility> searchActiveFacilities(@Param("search") String search, Pageable pageable);
}
