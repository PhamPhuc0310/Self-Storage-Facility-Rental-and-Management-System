package com.safebox.self_storage.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="SUPPORT_REQUEST_MESSAGES")
public class SupportRequestMessage {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="message_id") public UUID messageId;
 @Column(name="request_id",nullable=false) public UUID requestId;
 @Column(name="author_id",nullable=false) public UUID authorId;
 @Column(nullable=false,columnDefinition="NVARCHAR(MAX)") public String message;
 @Column(name="created_at",nullable=false) public LocalDateTime createdAt;
}
