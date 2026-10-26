package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "USER_FACILITY_ASSIGNMENTS")
public class UserFacilityAssignment {
    @Id
    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status", nullable = false)
    private String status;

    public UserFacilityAssignment() {}

    public UserFacilityAssignment(UUID assignmentId, UUID userId, UUID facilityId, UUID assignedBy, LocalDate startDate, LocalDate endDate, String status) {
        this.assignmentId = assignmentId;
        this.userId = userId;
        this.facilityId = facilityId;
        this.assignedBy = assignedBy;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getAssignedBy() { return assignedBy; }
    public void setAssignedBy(UUID assignedBy) { this.assignedBy = assignedBy; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
