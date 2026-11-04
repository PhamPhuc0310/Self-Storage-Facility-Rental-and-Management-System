:ON ERROR EXIT
/* Apply only after migration 11 through the guarded, explicitly targeted deployment runner. */
IF DB_NAME() <> N'$(SafeBoxTargetDb)' THROW 51200, 'Explicit SafeBox target database does not match the current database.', 1;
IF DB_NAME() = N'SelfStorageFacilityDB' AND N'$(SafeBoxAllowPrimary)' <> N'YES' THROW 51202, 'Primary database requires explicit reviewed deployment authorization.', 1;
IF OBJECT_ID(N'dbo.PRICING_POLICIES', N'U') IS NULL OR OBJECT_ID(N'dbo.FACILITIES', N'U') IS NULL
    OR OBJECT_ID(N'dbo.STORAGE_UNIT_TYPES', N'U') IS NULL OR OBJECT_ID(N'dbo.USERS', N'U') IS NULL
    THROW 51201, 'Pricing dependencies are missing.', 1;

/* Legacy fee columns are unrelated to the fixed VAT/deposit and UC18 formula.
   A new rental price intentionally leaves them NULL instead of inventing rates. */
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.PRICING_POLICIES') AND name = N'deposit_amount' AND is_nullable = 0)
    ALTER TABLE dbo.PRICING_POLICIES ALTER COLUMN deposit_amount DECIMAL(18,2) NULL;
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.PRICING_POLICIES') AND name = N'daily_overdue_rate' AND is_nullable = 0)
    ALTER TABLE dbo.PRICING_POLICIES ALTER COLUMN daily_overdue_rate DECIMAL(18,2) NULL;
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.PRICING_POLICIES') AND name = N'late_payment_rate' AND is_nullable = 0)
    ALTER TABLE dbo.PRICING_POLICIES ALTER COLUMN late_payment_rate DECIMAL(18,2) NULL;
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.PRICING_POLICIES') AND name = N'early_termination_fee' AND is_nullable = 0)
    ALTER TABLE dbo.PRICING_POLICIES ALTER COLUMN early_termination_fee DECIMAL(18,2) NULL;
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.PRICING_POLICIES') AND name = N'fee_waiver_allowed' AND is_nullable = 0)
    ALTER TABLE dbo.PRICING_POLICIES ALTER COLUMN fee_waiver_allowed BIT NULL;

IF OBJECT_ID(N'dbo.PRICE_ADJUSTMENT_PROPOSALS', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PRICE_ADJUSTMENT_PROPOSALS (
        proposal_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_PRICE_ADJUSTMENT_PROPOSALS PRIMARY KEY,
        facility_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.FACILITIES(facility_id),
        type_id INT NOT NULL REFERENCES dbo.STORAGE_UNIT_TYPES(type_id),
        proposed_monthly_price DECIMAL(18,2) NOT NULL,
        effective_from DATE NOT NULL,
        effective_to DATE NULL,
        reason NVARCHAR(500) NOT NULL,
        status NVARCHAR(20) NOT NULL,
        proposed_by UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.USERS(user_id),
        proposed_at DATETIME2 NOT NULL,
        reviewed_by UNIQUEIDENTIFIER NULL REFERENCES dbo.USERS(user_id),
        reviewed_at DATETIME2 NULL,
        pricing_id UNIQUEIDENTIFIER NULL REFERENCES dbo.PRICING_POLICIES(pricing_id),
        CONSTRAINT CK_PRICE_ADJUSTMENT_PROPOSALS_price CHECK (proposed_monthly_price > 0),
        CONSTRAINT CK_PRICE_ADJUSTMENT_PROPOSALS_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
        CONSTRAINT CK_PRICE_ADJUSTMENT_PROPOSALS_status CHECK (status IN (N'SUBMITTED', N'REJECTED', N'APPLIED'))
    );
END;
