package com.safebox.self_storage.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name = "SUPPORT_REQUESTS")
public class SupportRequest {
 @Id @Column(name="request_id") public UUID requestId;
 @Column(name="contract_id",nullable=false) public UUID contractId;
 @Column(name="requested_by",nullable=false) public UUID requestedBy;
 @Column(name="assigned_staff_id") public UUID assignedStaffId;
 @Column(name="request_type",nullable=false) public String requestType;
 @Column(nullable=false) public String title;
 @Column(nullable=false,columnDefinition="NVARCHAR(MAX)") public String description;
 @Column(nullable=false) public String priority;
 @Column(nullable=false) public String status;
 @Column(name="created_at",nullable=false) public LocalDateTime createdAt;
 @Column(name="resolved_at") public LocalDateTime resolvedAt;
 @Column(name="resolution_note",columnDefinition="NVARCHAR(MAX)") public String resolutionNote;
}
