package com.safebox.self_storage.dto.support;

import java.util.UUID;

/**
 * DTO yêu cầu gửi ticket hỗ trợ từ phía khách hàng (UC21 - BR21.1 - BR21.4).
 */
public class CreateSupportTicketRequest {

    private UUID contractId;
    private String issueType; // ACCESS_ISSUE, PAYMENT, UNIT_CONDITION, GOODS, OTHER
    private String title;
    private String description;
    private String priority;  // LOW, NORMAL, HIGH, URGENT

    public CreateSupportTicketRequest() {
    }

    public CreateSupportTicketRequest(UUID contractId, String issueType, String title, String description, String priority) {
        this.contractId = contractId;
        this.issueType = issueType;
        this.title = title;
        this.description = description;
        this.priority = priority;
    }

    public UUID getContractId() {
        return contractId;
    }

    public void setContractId(UUID contractId) {
        this.contractId = contractId;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }
}
