package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.HandoverRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HandoverRecordRepository extends JpaRepository<HandoverRecord, UUID> {

    List<HandoverRecord> findByContractIdOrderByScheduledAtDesc(UUID contractId);

    List<HandoverRecord> findByContractId(UUID contractId);

    java.util.Optional<HandoverRecord> findFirstByContractIdAndHandoverTypeAndStatus(
            UUID contractId, String handoverType, String status);

    List<HandoverRecord> findByContractIdAndHandoverTypeOrderByScheduledAtDesc(
            UUID contractId, String handoverType);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM HandoverRecord h, StorageUnit u " +
            "WHERE h.unitId = u.unitId " +
            "AND (:facilityId IS NULL OR u.facilityId = :facilityId) " +
            "AND h.handoverType = 'RETURN' " +
            "AND (:status IS NULL OR h.status = :status) " +
            "ORDER BY h.scheduledAt DESC")
    List<HandoverRecord> findReturnHandoverRecords(
            @org.springframework.data.repository.query.Param("facilityId") UUID facilityId,
            @org.springframework.data.repository.query.Param("status") String status);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM HandoverRecord h, StorageUnit u " +
            "WHERE h.unitId = u.unitId " +
            "AND (:facilityId IS NULL OR u.facilityId = :facilityId) " +
            "AND h.handoverType = 'RETURN' " +
            "AND (:status IS NULL OR h.status = :status) " +
            "AND h.staffId = :staffId " +
            "ORDER BY h.scheduledAt DESC")
    List<HandoverRecord> findReturnHandoverRecordsForStaff(
            @org.springframework.data.repository.query.Param("facilityId") UUID facilityId,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("staffId") UUID staffId);
}
