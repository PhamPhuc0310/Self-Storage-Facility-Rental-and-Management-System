package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thực thể ánh xạ bảng INSPECTION_REPORTS trong cơ sở dữ liệu.
 * Quản lý biên bản kiểm tra tình trạng kho (khi check-in hoặc check-out/trả kho).
 */
@Entity
@Table(name = "INSPECTION_REPORTS")
public class InspectionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "inspection_id", updatable = false, nullable = false)
    private UUID inspectionId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "inspected_by", nullable = false)
    private UUID inspectedBy;

    @Column(name = "inspection_type", nullable = false, length = 30)
    private String inspectionType;

    @Column(name = "inspection_date", nullable = false)
    private LocalDateTime inspectionDate;

    @Column(name = "condition_status", nullable = false, length = 30)
    private String conditionStatus;

    @Column(name = "temperature", precision = 6, scale = 2)
    private BigDecimal temperature;

    @Column(name = "humidity", precision = 5, scale = 2)
    private BigDecimal humidity;

    @Column(name = "damage_description", columnDefinition = "NVARCHAR(MAX)")
    private String damageDescription;

    @Column(name = "estimated_repair_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal estimatedRepairCost;

    @Column(name = "customer_confirmed", nullable = false)
    private boolean customerConfirmed;

    @Column(name = "note", columnDefinition = "NVARCHAR(MAX)")
    private String note;

    public InspectionReport() {
    }

    public UUID getInspectionId() {
        return inspectionId;
    }

    public void setInspectionId(UUID inspectionId) {
        this.inspectionId = inspectionId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public void setUnitId(UUID unitId) {
        this.unitId = unitId;
    }

    public UUID getContractId() {
        return contractId;
    }

    public void setContractId(UUID contractId) {
        this.contractId = contractId;
    }

    public UUID getInspectedBy() {
        return inspectedBy;
    }

    public void setInspectedBy(UUID inspectedBy) {
        this.inspectedBy = inspectedBy;
    }

    public String getInspectionType() {
        return inspectionType;
    }

    public void setInspectionType(String inspectionType) {
        this.inspectionType = inspectionType;
    }

    public LocalDateTime getInspectionDate() {
        return inspectionDate;
    }

    public void setInspectionDate(LocalDateTime inspectionDate) {
        this.inspectionDate = inspectionDate;
    }

    public String getConditionStatus() {
        return conditionStatus;
    }

    public void setConditionStatus(String conditionStatus) {
        this.conditionStatus = conditionStatus;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public BigDecimal getHumidity() {
        return humidity;
    }

    public void setHumidity(BigDecimal humidity) {
        this.humidity = humidity;
    }

    public String getDamageDescription() {
        return damageDescription;
    }

    public void setDamageDescription(String damageDescription) {
        this.damageDescription = damageDescription;
    }

    public BigDecimal getEstimatedRepairCost() {
        return estimatedRepairCost;
    }

    public void setEstimatedRepairCost(BigDecimal estimatedRepairCost) {
        this.estimatedRepairCost = estimatedRepairCost;
    }

    public boolean isCustomerConfirmed() {
        return customerConfirmed;
    }

    public void setCustomerConfirmed(boolean customerConfirmed) {
        this.customerConfirmed = customerConfirmed;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
