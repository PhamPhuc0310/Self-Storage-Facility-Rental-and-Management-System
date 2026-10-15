package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ACTIVITY_LOGS")
public class ActivityLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "log_id")
    private UUID id;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "action_type", nullable = false) private String actionType;
    @Column(name = "entity_type") private String entityType;
    @Column(name = "entity_id") private UUID entityId;
    @Column(name = "description") private String description;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    public ActivityLog() {}
    public ActivityLog(UUID userId, String actionType, String entityType, UUID entityId, String description, LocalDateTime createdAt) {
        this.userId = userId; this.actionType = actionType; this.entityType = entityType; this.entityId = entityId;
        this.description = description; this.createdAt = createdAt;
    }
}
