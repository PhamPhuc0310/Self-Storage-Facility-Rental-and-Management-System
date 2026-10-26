-- =============================================================================
-- Migration Script for UC17: Contract Renewal Management (Yêu cầu gia hạn hợp đồng)
-- Apply after SelfStorageFacilityDB.sql / UC11_UC12_Additive.sql to an existing SQL Server database.
-- Additive only: preserves existing contracts, invoices, and payment transactions.
-- =============================================================================

-- 1. Ensure table dbo.RENEWALS exists
IF OBJECT_ID(N'dbo.RENEWALS', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.RENEWALS (
        renewal_id UNIQUEIDENTIFIER NOT NULL
            CONSTRAINT DF_RENEWALS_renewal_id DEFAULT NEWSEQUENTIALID(),
        contract_id UNIQUEIDENTIFIER NOT NULL,
        old_end_date DATE NOT NULL,
        new_end_date DATE NOT NULL,
        renewal_amount DECIMAL(18,2) NOT NULL,
        requested_at DATETIME2 NOT NULL
            CONSTRAINT DF_RENEWALS_requested_at DEFAULT SYSDATETIME(),
        reviewed_at DATETIME2 NULL,
        approved_by UNIQUEIDENTIFIER NULL,
        payment_deadline DATETIME2 NULL,
        status NVARCHAR(30) NOT NULL,
        rejection_reason NVARCHAR(MAX) NULL,

        CONSTRAINT PK_RENEWALS PRIMARY KEY (renewal_id),
        CONSTRAINT FK_RENEWALS_CONTRACTS
            FOREIGN KEY (contract_id) REFERENCES dbo.RENTAL_CONTRACTS(contract_id),
        CONSTRAINT FK_RENEWALS_APPROVED_BY
            FOREIGN KEY (approved_by) REFERENCES dbo.USERS(user_id),
        CONSTRAINT CK_RENEWALS_date_range
            CHECK (new_end_date > old_end_date),
        CONSTRAINT CK_RENEWALS_amount
            CHECK (renewal_amount >= 0)
    );
END;
GO

-- 2. Additive snapshot columns for BR17.4 (Giá, VAT, ưu đãi được chốt thành snapshot)
IF COL_LENGTH('dbo.RENEWALS', 'extension_months') IS NULL
    ALTER TABLE dbo.RENEWALS ADD extension_months INT NULL;
GO
IF COL_LENGTH('dbo.RENEWALS', 'monthly_price') IS NULL
    ALTER TABLE dbo.RENEWALS ADD monthly_price DECIMAL(18,2) NULL;
GO
IF COL_LENGTH('dbo.RENEWALS', 'subtotal') IS NULL
    ALTER TABLE dbo.RENEWALS ADD subtotal DECIMAL(18,2) NULL;
GO
IF COL_LENGTH('dbo.RENEWALS', 'vat_amount') IS NULL
    ALTER TABLE dbo.RENEWALS ADD vat_amount DECIMAL(18,2) NULL;
GO
IF COL_LENGTH('dbo.RENEWALS', 'discount_amount') IS NULL
    ALTER TABLE dbo.RENEWALS ADD discount_amount DECIMAL(18,2) NULL;
GO
IF COL_LENGTH('dbo.RENEWALS', 'pricing_id') IS NULL
    ALTER TABLE dbo.RENEWALS ADD pricing_id UNIQUEIDENTIFIER NULL;
GO

-- 3. Helpful query indexes
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.RENEWALS') AND name = N'IX_RENEWALS_contract_status')
    CREATE INDEX IX_RENEWALS_contract_status ON dbo.RENEWALS(contract_id, status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.RENEWALS') AND name = N'IX_RENEWALS_requested_at')
    CREATE INDEX IX_RENEWALS_requested_at ON dbo.RENEWALS(requested_at DESC);
GO
