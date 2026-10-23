package com.safebox.self_storage.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO yêu cầu hoàn tất kiểm tra và nhận lại kho của nhân viên/quản lý cơ sở (UC16 - BR16.3-BR16.8 & UC20 - BR20.1-BR20.5).
 */
public class CompleteReturnInspectionRequest {

    private UUID handoverId;
    private boolean leftoverItems;
    private String cleanlinessStatus;
    private boolean accessReturned;
    private String accessMethod;
    private String accessIdentifier;
    private String conditionStatus;
    private String damageDescription;
    private BigDecimal estimatedRepairCost;
    private BigDecimal temperature;
    private BigDecimal humidity;
    private boolean customerConfirmed;
    private String customerSignature;
    private String staffSignature;
    private String note;
    private String unitNextStatus;
    private String evidenceUrls;

    public CompleteReturnInspectionRequest() {
    }

    public UUID getHandoverId() {
        return handoverId;
    }

    public void setHandoverId(UUID handoverId) {
        this.handoverId = handoverId;
    }

    public boolean isLeftoverItems() {
        return leftoverItems;
    }

    public void setLeftoverItems(boolean leftoverItems) {
        this.leftoverItems = leftoverItems;
    }

    public String getCleanlinessStatus() {
        return cleanlinessStatus;
    }

    public void setCleanlinessStatus(String cleanlinessStatus) {
        this.cleanlinessStatus = cleanlinessStatus;
    }

    public boolean isAccessReturned() {
        return accessReturned;
    }

    public void setAccessReturned(boolean accessReturned) {
        this.accessReturned = accessReturned;
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

    public String getConditionStatus() {
        return conditionStatus;
    }

    public void setConditionStatus(String conditionStatus) {
        this.conditionStatus = conditionStatus;
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

    public boolean isCustomerConfirmed() {
        return customerConfirmed;
    }

    public void setCustomerConfirmed(boolean customerConfirmed) {
        this.customerConfirmed = customerConfirmed;
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

    public String getUnitNextStatus() {
        return unitNextStatus;
    }

    public void setUnitNextStatus(String unitNextStatus) {
        this.unitNextStatus = unitNextStatus;
    }

    public String getEvidenceUrls() {
        return evidenceUrls;
    }

    public void setEvidenceUrls(String evidenceUrls) {
        this.evidenceUrls = evidenceUrls;
    }
}
