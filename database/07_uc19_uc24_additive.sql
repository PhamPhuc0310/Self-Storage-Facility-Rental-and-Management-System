-- Apply after SelfStorageFacilityDB.sql and UC11_UC12_Additive.sql.
-- No policy rows are inserted: an authorized business owner must approve a versioned policy.
-- Required SQL Server SET options for filtered indexes, including when invoked through sqlcmd.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
GO
IF OBJECT_ID(N'dbo.EARLY_RETURN_POLICIES', N'U') IS NULL
CREATE TABLE dbo.EARLY_RETURN_POLICIES (
    policy_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    facility_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.FACILITIES(facility_id),
    version NVARCHAR(50) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    deposit_refundable BIT NOT NULL,
    inspection_deduction_allowed BIT NOT NULL,
    status NVARCHAR(20) NOT NULL,
    CONSTRAINT UX_EARLY_RETURN_POLICY_VERSION UNIQUE (facility_id, version),
    CONSTRAINT CK_EARLY_RETURN_POLICY_DATES CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
GO
IF OBJECT_ID(N'dbo.EARLY_RETURN_SETTLEMENTS', N'U') IS NULL
CREATE TABLE dbo.EARLY_RETURN_SETTLEMENTS (
    settlement_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    termination_id UNIQUEIDENTIFIER NOT NULL UNIQUE REFERENCES dbo.TERMINATION_REQUESTS(termination_id),
    contract_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.RENTAL_CONTRACTS(contract_id),
    policy_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.EARLY_RETURN_POLICIES(policy_id),
    policy_version NVARCHAR(50) NOT NULL,
    handover_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.HANDOVER_RECORDS(handover_id),
    inspection_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.INSPECTION_REPORTS(inspection_id),
    deposit_contract_amount DECIMAL(18,2) NOT NULL,
    deposit_collected DECIMAL(18,2) NOT NULL,
    prior_deposit_refunds DECIMAL(18,2) NOT NULL,
    rental_collected DECIMAL(18,2) NOT NULL,
    unused_rent_refund DECIMAL(18,2) NOT NULL,
    outstanding_receivables DECIMAL(18,2) NOT NULL,
    inspection_deduction DECIMAL(18,2) NOT NULL,
    deduction_reason NVARCHAR(MAX) NULL,
    deposit_refund_amount DECIMAL(18,2) NOT NULL,
    remaining_payable DECIMAL(18,2) NOT NULL,
    status NVARCHAR(30) NOT NULL,
    calculated_at DATETIME2 NOT NULL,
    approved_at DATETIME2 NOT NULL,
    approved_by UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.USERS(user_id),
    refund_id UNIQUEIDENTIFIER NULL REFERENCES dbo.DEPOSIT_REFUNDS(refund_id),
    refund_transaction_id UNIQUEIDENTIFIER NULL REFERENCES dbo.PAYMENT_TRANSACTIONS(transaction_id),
    CONSTRAINT CK_EARLY_RETURN_SETTLEMENT_AMOUNTS CHECK
        (deposit_contract_amount >= 0 AND deposit_collected >= 0 AND prior_deposit_refunds >= 0
         AND rental_collected >= 0 AND unused_rent_refund >= 0 AND outstanding_receivables >= 0
         AND inspection_deduction >= 0 AND deposit_refund_amount >= 0 AND remaining_payable >= 0)
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.TERMINATION_REQUESTS') AND name = N'UX_TERMINATION_EARLY_ACTIVE')
    CREATE UNIQUE INDEX UX_TERMINATION_EARLY_ACTIVE ON dbo.TERMINATION_REQUESTS(contract_id)
    WHERE termination_type = N'EARLY_RETURN' AND status IN (N'REQUESTED', N'SETTLEMENT_APPROVED', N'REFUNDED');
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.PAYMENT_TRANSACTIONS') AND name = N'UX_PAYMENT_UC19_REFUND_REFERENCE')
    CREATE UNIQUE INDEX UX_PAYMENT_UC19_REFUND_REFERENCE ON dbo.PAYMENT_TRANSACTIONS(reference_code)
    WHERE transaction_type = N'REFUND' AND reference_code IS NOT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.PAYMENT_TRANSACTIONS') AND name = N'IX_PAYMENT_REPORT_PERIOD')
    CREATE INDEX IX_PAYMENT_REPORT_PERIOD ON dbo.PAYMENT_TRANSACTIONS(status, transaction_date, contract_id) INCLUDE (amount, payment_type, transaction_type, invoice_id);
GO
