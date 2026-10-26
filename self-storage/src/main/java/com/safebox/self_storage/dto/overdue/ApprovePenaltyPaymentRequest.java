package com.safebox.self_storage.dto.overdue;

public class ApprovePenaltyPaymentRequest {

    private boolean approved = true;
    private String note;

    public ApprovePenaltyPaymentRequest() {}

    public ApprovePenaltyPaymentRequest(boolean approved, String note) {
        this.approved = approved;
        this.note = note;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
