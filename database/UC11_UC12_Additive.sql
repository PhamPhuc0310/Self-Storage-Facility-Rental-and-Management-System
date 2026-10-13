-- Apply after SelfStorageFacilityDB.sql to an existing SQL Server database.
-- Additive only: existing reservations, contracts, invoices and transactions are retained.

IF COL_LENGTH('dbo.RESERVATIONS', 'quoted_monthly_price') IS NULL
    ALTER TABLE dbo.RESERVATIONS ADD quoted_monthly_price DECIMAL(18,2) NULL;
GO
IF COL_LENGTH('dbo.RESERVATIONS', 'pricing_version') IS NULL
    ALTER TABLE dbo.RESERVATIONS ADD pricing_version NVARCHAR(10) NOT NULL
        CONSTRAINT DF_RESERVATIONS_pricing_version DEFAULT N'v1';
GO

IF COL_LENGTH('dbo.PAYMENT_TRANSACTIONS', 'contract_id') IS NULL
    ALTER TABLE dbo.PAYMENT_TRANSACTIONS ADD contract_id UNIQUEIDENTIFIER NULL;
GO
IF COL_LENGTH('dbo.PAYMENT_TRANSACTIONS', 'payment_type') IS NULL
    ALTER TABLE dbo.PAYMENT_TRANSACTIONS ADD payment_type NVARCHAR(30) NULL;
GO
IF COL_LENGTH('dbo.PAYMENT_TRANSACTIONS', 'created_by') IS NULL
    ALTER TABLE dbo.PAYMENT_TRANSACTIONS ADD created_by UNIQUEIDENTIFIER NULL;
GO
IF COL_LENGTH('dbo.PAYMENT_TRANSACTIONS', 'idempotency_key') IS NULL
    ALTER TABLE dbo.PAYMENT_TRANSACTIONS ADD idempotency_key NVARCHAR(100) NULL;
GO
IF COL_LENGTH('dbo.PAYMENT_TRANSACTIONS', 'created_at') IS NULL
    ALTER TABLE dbo.PAYMENT_TRANSACTIONS ADD created_at DATETIME2 NULL;
GO
IF COL_LENGTH('dbo.PAYMENT_TRANSACTIONS', 'updated_at') IS NULL
    ALTER TABLE dbo.PAYMENT_TRANSACTIONS ADD updated_at DATETIME2 NULL;
GO

-- Backfill old UC15 transactions without changing their financial state.
UPDATE p SET p.contract_id = i.contract_id
FROM dbo.PAYMENT_TRANSACTIONS p
JOIN dbo.INVOICES i ON i.invoice_id = p.invoice_id
WHERE p.contract_id IS NULL;
GO
UPDATE dbo.PAYMENT_TRANSACTIONS
SET created_at = transaction_date, updated_at = transaction_date
WHERE created_at IS NULL OR updated_at IS NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.PAYMENT_TRANSACTIONS') AND name = N'UX_PAYMENT_TRANSACTIONS_actor_idempotency')
    CREATE UNIQUE INDEX UX_PAYMENT_TRANSACTIONS_actor_idempotency
    ON dbo.PAYMENT_TRANSACTIONS(created_by, idempotency_key)
    WHERE created_by IS NOT NULL AND idempotency_key IS NOT NULL;
GO

IF OBJECT_ID(N'dbo.PICKUP_APPOINTMENTS', N'U') IS NULL
CREATE TABLE dbo.PICKUP_APPOINTMENTS (
    appointment_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    contract_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.RENTAL_CONTRACTS(contract_id),
    facility_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.FACILITIES(facility_id),
    customer_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.USERS(user_id),
    appointment_time DATETIME2 NOT NULL,
    assigned_staff_id UNIQUEIDENTIFIER NULL REFERENCES dbo.USERS(user_id),
    status NVARCHAR(30) NOT NULL,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.PICKUP_APPOINTMENTS') AND name = N'IX_PICKUP_APPOINTMENTS_contract_status')
    CREATE INDEX IX_PICKUP_APPOINTMENTS_contract_status ON dbo.PICKUP_APPOINTMENTS(contract_id, status);
GO

IF OBJECT_ID(N'dbo.DEPOSIT_REFUNDS', N'U') IS NULL
CREATE TABLE dbo.DEPOSIT_REFUNDS (
    refund_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    contract_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.RENTAL_CONTRACTS(contract_id),
    customer_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.USERS(user_id),
    facility_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.FACILITIES(facility_id),
    payment_reference UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.PAYMENT_TRANSACTIONS(transaction_id),
    requested_amount DECIMAL(18,2) NOT NULL,
    approved_amount DECIMAL(18,2) NULL,
    reason NVARCHAR(MAX) NOT NULL,
    status NVARCHAR(30) NOT NULL,
    reviewer_id UNIQUEIDENTIFIER NULL REFERENCES dbo.USERS(user_id),
    review_note NVARCHAR(MAX) NULL,
    refund_transaction_reference NVARCHAR(100) NULL,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,
    completed_at DATETIME2 NULL,
    CONSTRAINT CK_DEPOSIT_REFUNDS_amount CHECK (requested_amount > 0 AND (approved_amount IS NULL OR approved_amount >= 0))
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.DEPOSIT_REFUNDS') AND name = N'IX_DEPOSIT_REFUNDS_contract_status')
    CREATE INDEX IX_DEPOSIT_REFUNDS_contract_status ON dbo.DEPOSIT_REFUNDS(contract_id, status);
GO
