package com.safebox.self_storage.dto.support;

/**
 * DTO gửi nội dung phản hồi trong ticket hỗ trợ (UC21 - BR21.4, BR21.5).
 */
public class CreateTicketReplyRequest {

    private String content;

    public CreateTicketReplyRequest() {
    }

    public CreateTicketReplyRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
