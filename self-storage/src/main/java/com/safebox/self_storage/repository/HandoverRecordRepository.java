package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.HandoverRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HandoverRecordRepository extends JpaRepository<HandoverRecord, UUID> {

    List<HandoverRecord> findByContractIdOrderByScheduledAtDesc(UUID contractId);

    List<HandoverRecord> findByContractId(UUID contractId);
}
