package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.Reservation;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.facilityId = :facilityId AND r.typeId = :typeId "
            + "AND r.status = 'CONFIRMED' AND r.startDate < :endDate AND r.endDate > :startDate")
    long countConfirmedOverlapping(@Param("facilityId") UUID facilityId, @Param("typeId") Integer typeId,
                                   @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
