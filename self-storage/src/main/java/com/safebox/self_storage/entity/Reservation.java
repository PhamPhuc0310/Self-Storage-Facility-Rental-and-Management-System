package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "RESERVATIONS")
public class Reservation {
    @Id @Column(name = "reservation_id") private UUID reservationId;
    @Column(name = "facility_id", nullable = false) private UUID facilityId;
    @Column(name = "type_id", nullable = false) private Integer typeId;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(name = "status", nullable = false) private String status;
}
