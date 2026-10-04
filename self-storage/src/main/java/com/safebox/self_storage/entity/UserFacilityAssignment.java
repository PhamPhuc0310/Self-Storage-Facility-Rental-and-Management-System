package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "USER_FACILITY_ASSIGNMENTS")
public class UserFacilityAssignment {
    @Id @Column(name = "assignment_id") private UUID assignmentId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "facility_id", nullable = false) private UUID facilityId;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "status", nullable = false) private String status;
}
