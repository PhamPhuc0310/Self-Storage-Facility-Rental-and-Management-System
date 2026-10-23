-- Sprint 3 - UC21 Migration Script
-- UC21: Gửi và theo dõi yêu cầu hỗ trợ (SUPPORT_TICKETS & SUPPORT_TICKET_REPLIES)

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'SUPPORT_TICKETS')
BEGIN
    CREATE TABLE SUPPORT_TICKETS (
        ticket_id       UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
        ticket_number   NVARCHAR(50) NOT NULL UNIQUE,
        contract_id     UNIQUEIDENTIFIER NOT NULL,
        customer_id     UNIQUEIDENTIFIER NOT NULL,
        facility_id     UNIQUEIDENTIFIER NOT NULL,
        issue_type      NVARCHAR(30) NOT NULL,
        title           NVARCHAR(200) NOT NULL,
        description     NVARCHAR(MAX) NOT NULL,
        status          NVARCHAR(20) NOT NULL DEFAULT 'OPEN',
        priority        NVARCHAR(20) NOT NULL DEFAULT 'NORMAL',
        assigned_to     UNIQUEIDENTIFIER NULL,
        resolved_at     DATETIME2 NULL,
        resolution_note NVARCHAR(MAX) NULL,
        created_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
        updated_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
        CONSTRAINT FK_Ticket_Contract FOREIGN KEY (contract_id) REFERENCES RENTAL_CONTRACTS(contract_id),
        CONSTRAINT FK_Ticket_Customer FOREIGN KEY (customer_id) REFERENCES USERS(user_id)
    );

    CREATE INDEX IX_SUPPORT_TICKETS_customer_id ON SUPPORT_TICKETS(customer_id);
    CREATE INDEX IX_SUPPORT_TICKETS_contract_id ON SUPPORT_TICKETS(contract_id);
    CREATE INDEX IX_SUPPORT_TICKETS_facility_id ON SUPPORT_TICKETS(facility_id);
    CREATE INDEX IX_SUPPORT_TICKETS_status ON SUPPORT_TICKETS(status);
END
GO

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'SUPPORT_TICKET_REPLIES')
BEGIN
    CREATE TABLE SUPPORT_TICKET_REPLIES (
        reply_id        UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
        ticket_id       UNIQUEIDENTIFIER NOT NULL,
        user_id         UNIQUEIDENTIFIER NOT NULL,
        user_role       NVARCHAR(30) NOT NULL,
        user_full_name  NVARCHAR(100) NULL,
        content         NVARCHAR(MAX) NOT NULL,
        created_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
        CONSTRAINT FK_TicketReply_Ticket FOREIGN KEY (ticket_id) REFERENCES SUPPORT_TICKETS(ticket_id) ON DELETE CASCADE,
        CONSTRAINT FK_TicketReply_User FOREIGN KEY (user_id) REFERENCES USERS(user_id)
    );

    CREATE INDEX IX_SUPPORT_TICKET_REPLIES_ticket_id ON SUPPORT_TICKET_REPLIES(ticket_id);
END
GO
