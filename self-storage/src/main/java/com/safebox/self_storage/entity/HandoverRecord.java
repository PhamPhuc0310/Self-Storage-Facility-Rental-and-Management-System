package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thực thể ánh xạ bảng HANDOVER_RECORDS trong cơ sở dữ liệu.
 * Quản lý biên bản bàn giao nhận kho hoặc trả kho giữa nhân viên và khách hàng.
 */
@Entity
@Table(name = "HANDOVER_RECORDS")
public class HandoverRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "handover_id", updatable = false, nullable = false)
    private UUID handoverId;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "handover_type", nullable = false, length = 20)
    private String handoverType;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "actual_at")
    private LocalDateTime actualAt;

    @Column(name = "access_method", length = 30)
    private String accessMethod;

    @Column(name = "access_identifier", length = 255)
    private String accessIdentifier;

    @Column(name = "customer_signature", columnDefinition = "NVARCHAR(MAX)")
    private String customerSignature;

    @Column(name = "staff_signature", columnDefinition = "NVARCHAR(MAX)")
    private String staffSignature;

    @Column(name = "note", columnDefinition = "NVARCHAR(MAX)")
    private String note;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    public HandoverRecord() {
    }

    public UUID getHandoverId() {
        return handoverId;
    }

    public void setHandoverId(UUID handoverId) {
        this.handoverId = handoverId;
    }

    public UUID getContractId() {
        return contractId;
    }

    public void setContractId(UUID contractId) {
        this.contractId = contractId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public void setUnitId(UUID unitId) {
        this.unitId = unitId;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public void setStaffId(UUID staffId) {
        this.staffId = staffId;
    }

    public String getHandoverType() {
        return handoverType;
    }

    public void setHandoverType(String handoverType) {
        this.handoverType = handoverType;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public LocalDateTime getActualAt() {
        return actualAt;
    }

    public void setActualAt(LocalDateTime actualAt) {
        this.actualAt = actualAt;
    }

    public String getAccessMethod() {
        return accessMethod;
    }

    public void setAccessMethod(String accessMethod) {
        this.accessMethod = accessMethod;
    }

    public String getAccessIdentifier() {
        return accessIdentifier;
    }

    public void setAccessIdentifier(String accessIdentifier) {
        this.accessIdentifier = accessIdentifier;
    }

    public String getCustomerSignature() {
        return customerSignature;
    }

    public void setCustomerSignature(String customerSignature) {
        this.customerSignature = customerSignature;
    }

    public String getStaffSignature() {
        return staffSignature;
    }

    public void setStaffSignature(String staffSignature) {
        this.staffSignature = staffSignature;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
