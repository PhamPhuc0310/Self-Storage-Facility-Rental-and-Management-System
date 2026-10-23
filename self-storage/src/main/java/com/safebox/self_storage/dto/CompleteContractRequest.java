package com.safebox.self_storage.dto;

import java.util.UUID;

/**
 * DTO yêu cầu hoàn tất trả kho và đóng hợp đồng (UC20 - BR20.1-BR20.5).
 */
public class CompleteContractRequest {

    private String notes;
    private String unitNextStatus; // AVAILABLE hoặc MAINTENANCE (mặc định AVAILABLE nếu kho tốt)

    public CompleteContractRequest() {
    }

    public CompleteContractRequest(String notes, String unitNextStatus) {
        this.notes = notes;
        this.unitNextStatus = unitNextStatus;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getUnitNextStatus() {
        return unitNextStatus;
    }

    public void setUnitNextStatus(String unitNextStatus) {
        this.unitNextStatus = unitNextStatus;
    }
}
