package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.UnitMeasurementSubmission;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitMeasurementSubmissionRepository extends JpaRepository<UnitMeasurementSubmission,UUID> {
    boolean existsByUnitIdAndStatus(UUID unitId,String status);
    List<UnitMeasurementSubmission> findByFacilityIdOrderBySubmittedAtDesc(UUID facilityId);
}
