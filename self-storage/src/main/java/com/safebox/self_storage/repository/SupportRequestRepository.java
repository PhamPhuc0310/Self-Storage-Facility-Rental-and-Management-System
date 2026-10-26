package com.safebox.self_storage.repository;
import com.safebox.self_storage.entity.SupportRequest;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface SupportRequestRepository extends JpaRepository<SupportRequest,UUID> {
 List<SupportRequest> findByRequestedByOrderByCreatedAtDesc(UUID userId);
 List<SupportRequest> findByAssignedStaffIdOrderByCreatedAtDesc(UUID staffId);
 @Query("select s from SupportRequest s join RentalContract c on s.contractId=c.contractId join StorageUnit u on c.unitId=u.unitId where u.facilityId in :ids order by s.createdAt desc") List<SupportRequest> findForFacilities(@Param("ids") Collection<UUID> ids);
 @Query("select count(s) from SupportRequest s join RentalContract c on s.contractId=c.contractId join StorageUnit u on c.unitId=u.unitId where s.assignedStaffId=:staffId and u.facilityId=:facilityId and s.status in :statuses") long countOpenWork(@Param("staffId") UUID staffId,@Param("facilityId") UUID facilityId,@Param("statuses") Collection<String> statuses);
}
