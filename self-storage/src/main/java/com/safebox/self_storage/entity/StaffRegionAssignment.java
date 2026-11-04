package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "STAFF_REGION_ASSIGNMENTS")
public class StaffRegionAssignment {
    @Id @Column(name = "assignment_id") private UUID assignmentId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "region_code", nullable = false, length = 30) private String regionCode;
    @Column(name = "status", nullable = false) private String status;
    @Column(name = "assigned_by", nullable = false) private UUID assignedBy;
    @Column(name = "assigned_at", nullable = false) private LocalDateTime assignedAt;
    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID v) { assignmentId = v; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { userId = v; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String v) { regionCode = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public UUID getAssignedBy() { return assignedBy; }
    public void setAssignedBy(UUID v) { assignedBy = v; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime v) { assignedAt = v; }
}
