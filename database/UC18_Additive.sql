-- =============================================================================
-- Migration Script for UC18: Overdue Tracking & Penalty Fee Management
-- Additive only: preserves all existing data and tables.
-- =============================================================================

USE SelfStorageFacilityDB;
GO

-- 1. Add penalty policy columns to RENTAL_POLICIES (BR18.2)
IF COL_LENGTH('dbo.RENTAL_POLICIES', 'daily_penalty_rate') IS NULL
    ALTER TABLE dbo.RENTAL_POLICIES ADD daily_penalty_rate DECIMAL(5,4) NOT NULL CONSTRAINT DF_RP_daily_penalty_rate DEFAULT 0.05;
GO

IF COL_LENGTH('dbo.RENTAL_POLICIES', 'fixed_penalty_amount') IS NULL
    ALTER TABLE dbo.RENTAL_POLICIES ADD fixed_penalty_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_RP_fixed_penalty_amount DEFAULT 0;
GO

IF COL_LENGTH('dbo.RENTAL_POLICIES', 'penalty_type') IS NULL
    ALTER TABLE dbo.RENTAL_POLICIES ADD penalty_type NVARCHAR(50) NOT NULL CONSTRAINT DF_RP_penalty_type DEFAULT N'PERCENT_DAILY';
GO

-- 2. Table CONTRACT_PENALTIES (BR18.1, BR18.2, BR18.3)
IF OBJECT_ID(N'dbo.CONTRACT_PENALTIES', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.CONTRACT_PENALTIES (
        penalty_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_PENALTIES_id DEFAULT NEWSEQUENTIALID(),
        contract_id UNIQUEIDENTIFIER NOT NULL,
        policy_id UNIQUEIDENTIFIER NULL,
        invoice_id UNIQUEIDENTIFIER NULL,
        period_date DATE NOT NULL,
        overdue_days INT NOT NULL,
        billable_days INT NOT NULL,
        daily_rate DECIMAL(18,2) NOT NULL,
        penalty_rate DECIMAL(5,4) NOT NULL,
        penalty_amount DECIMAL(18,2) NOT NULL,
        paid_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_PENALTIES_paid DEFAULT 0,
        calculation_explanation NVARCHAR(MAX) NOT NULL,
        idempotency_key NVARCHAR(120) NOT NULL,
        status NVARCHAR(30) NOT NULL CONSTRAINT DF_PENALTIES_status DEFAULT N'UNPAID',
        created_at DATETIME2 NOT NULL CONSTRAINT DF_PENALTIES_created DEFAULT SYSDATETIME(),
        updated_at DATETIME2 NULL,

        CONSTRAINT PK_CONTRACT_PENALTIES PRIMARY KEY (penalty_id),
        CONSTRAINT FK_PENALTIES_CONTRACT FOREIGN KEY (contract_id) REFERENCES dbo.RENTAL_CONTRACTS(contract_id),
        CONSTRAINT FK_PENALTIES_POLICY FOREIGN KEY (policy_id) REFERENCES dbo.RENTAL_POLICIES(policy_id),
        CONSTRAINT FK_PENALTIES_INVOICE FOREIGN KEY (invoice_id) REFERENCES dbo.INVOICES(invoice_id),
        CONSTRAINT UQ_PENALTIES_idempotency UNIQUE (idempotency_key)
    );

    CREATE INDEX IX_PENALTIES_contract ON dbo.CONTRACT_PENALTIES(contract_id, status);
    CREATE INDEX IX_PENALTIES_period ON dbo.CONTRACT_PENALTIES(period_date);
END;
GO

-- 3. Table PENALTY_ADJUSTMENTS (BR18.5: Lưu vết điều chỉnh nợ)
IF OBJECT_ID(N'dbo.PENALTY_ADJUSTMENTS', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PENALTY_ADJUSTMENTS (
        adjustment_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_ADJUSTMENTS_id DEFAULT NEWSEQUENTIALID(),
        penalty_id UNIQUEIDENTIFIER NOT NULL,
        contract_id UNIQUEIDENTIFIER NOT NULL,
        original_amount DECIMAL(18,2) NOT NULL,
        adjusted_amount DECIMAL(18,2) NOT NULL,
        discount_amount DECIMAL(18,2) NOT NULL,
        adjustment_type NVARCHAR(30) NOT NULL,
        reason NVARCHAR(MAX) NOT NULL,
        adjusted_by UNIQUEIDENTIFIER NOT NULL,
        adjusted_at DATETIME2 NOT NULL CONSTRAINT DF_ADJUSTMENTS_at DEFAULT SYSDATETIME(),
        note NVARCHAR(MAX) NULL,

        CONSTRAINT PK_PENALTY_ADJUSTMENTS PRIMARY KEY (adjustment_id),
        CONSTRAINT FK_ADJUSTMENTS_PENALTY FOREIGN KEY (penalty_id) REFERENCES dbo.CONTRACT_PENALTIES(penalty_id),
        CONSTRAINT FK_ADJUSTMENTS_CONTRACT FOREIGN KEY (contract_id) REFERENCES dbo.RENTAL_CONTRACTS(contract_id),
        CONSTRAINT FK_ADJUSTMENTS_USER FOREIGN KEY (adjusted_by) REFERENCES dbo.USERS(user_id)
    );

    CREATE INDEX IX_ADJUSTMENTS_penalty ON dbo.PENALTY_ADJUSTMENTS(penalty_id);
    CREATE INDEX IX_ADJUSTMENTS_contract ON dbo.PENALTY_ADJUSTMENTS(contract_id);
END;
GO

-- 4. Ensure existing RENTAL_POLICIES have default penalty rate if null
UPDATE dbo.RENTAL_POLICIES 
SET daily_penalty_rate = 0.05, 
    fixed_penalty_amount = 0, 
    penalty_type = N'PERCENT_DAILY'
WHERE daily_penalty_rate IS NULL OR daily_penalty_rate = 0;
GO
