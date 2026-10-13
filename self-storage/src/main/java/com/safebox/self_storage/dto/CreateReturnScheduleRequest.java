package com.safebox.self_storage.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO yêu cầu đặt lịch trả kho của khách hàng (UC16 - BR16.1, BR16.2).
 */
public class CreateReturnScheduleRequest {

    private UUID contractId;
    private LocalDateTime scheduledAt;
    private String note;
    private boolean checklistEmptyConfirmed;
    private boolean checklistAssetsConfirmed;

    public CreateReturnScheduleRequest() {
    }

    public CreateReturnScheduleRequest(UUID contractId, LocalDateTime scheduledAt, String note,
                                       boolean checklistEmptyConfirmed, boolean checklistAssetsConfirmed) {
        this.contractId = contractId;
        this.scheduledAt = scheduledAt;
        this.note = note;
        this.checklistEmptyConfirmed = checklistEmptyConfirmed;
        this.checklistAssetsConfirmed = checklistAssetsConfirmed;
    }

    public UUID getContractId() {
        return contractId;
    }

    public void setContractId(UUID contractId) {
        this.contractId = contractId;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isChecklistEmptyConfirmed() {
        return checklistEmptyConfirmed;
    }

    public void setChecklistEmptyConfirmed(boolean checklistEmptyConfirmed) {
        this.checklistEmptyConfirmed = checklistEmptyConfirmed;
    }

    public boolean isChecklistAssetsConfirmed() {
        return checklistAssetsConfirmed;
    }

    public void setChecklistAssetsConfirmed(boolean checklistAssetsConfirmed) {
        this.checklistAssetsConfirmed = checklistAssetsConfirmed;
    }
}
