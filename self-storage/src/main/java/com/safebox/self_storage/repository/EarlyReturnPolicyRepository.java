package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.EarlyReturnPolicy;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EarlyReturnPolicyRepository extends JpaRepository<EarlyReturnPolicy, UUID> {
    @Query("select p from EarlyReturnPolicy p where p.facilityId = :facility and p.status = 'ACTIVE' "
            + "and p.effectiveFrom <= :date and (p.effectiveTo is null or p.effectiveTo >= :date) "
            + "order by p.effectiveFrom desc")
    List<EarlyReturnPolicy> applicable(@Param("facility") UUID facility, @Param("date") LocalDate date);
}
