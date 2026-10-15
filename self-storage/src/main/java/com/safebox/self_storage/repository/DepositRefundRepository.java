package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.DepositRefund;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DepositRefundRepository extends JpaRepository<DepositRefund, UUID> {
    List<DepositRefund> findByContractIdOrderByCreatedAtDesc(UUID contractId);
    boolean existsByContractIdAndStatusIn(UUID contractId, Collection<String> statuses);
    boolean existsByRefundTransactionReference(String transactionReference);
    @Query("select coalesce(sum(r.approvedAmount),0) from DepositRefund r where r.contractId = :id and r.status = 'COMPLETED'")
    BigDecimal completedTotal(@Param("id") UUID contractId);
    @Query("select r.contractId, coalesce(sum(r.approvedAmount),0) from DepositRefund r where r.contractId in :ids and r.status = 'COMPLETED' group by r.contractId")
    List<Object[]> completedTotals(@Param("ids") Collection<UUID> contractIds);
    @Query("select r from DepositRefund r where r.facilityId in :facilities and r.status in :statuses order by r.createdAt desc")
    List<DepositRefund> forManager(@Param("facilities") Collection<UUID> facilities, @Param("statuses") Collection<String> statuses);
}
