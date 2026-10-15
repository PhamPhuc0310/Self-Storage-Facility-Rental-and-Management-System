package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.InspectionReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InspectionReportRepository extends JpaRepository<InspectionReport, UUID> {

    List<InspectionReport> findByContractIdOrderByInspectionDateDesc(UUID contractId);

    List<InspectionReport> findByUnitIdOrderByInspectionDateDesc(UUID unitId);

    java.util.Optional<InspectionReport> findFirstByContractIdAndInspectionTypeOrderByInspectionDateDesc(
            UUID contractId, String inspectionType);

    java.util.Optional<InspectionReport> findFirstByUnitIdAndInspectionTypeOrderByInspectionDateDesc(
            UUID unitId, String inspectionType);
}
