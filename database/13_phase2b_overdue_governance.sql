:ON ERROR EXIT
/* SafeBox Phase 2B. Apply after UC18 approval metadata and migration 12.
   Caller must select and verify the target database before execution. */
IF DB_NAME() <> N'$(SafeBoxTargetDb)' THROW 51300, 'Explicit SafeBox target database does not match the current database.', 1;
IF DB_NAME() = N'SelfStorageFacilityDB' AND N'$(SafeBoxAllowPrimary)' <> N'YES' THROW 51302, 'Primary database requires explicit reviewed deployment authorization.', 1;
IF OBJECT_ID(N'dbo.RENTAL_POLICIES',N'U') IS NULL OR OBJECT_ID(N'dbo.FACILITIES',N'U') IS NULL
   OR OBJECT_ID(N'dbo.USERS',N'U') IS NULL OR OBJECT_ID(N'dbo.ACTIVITY_LOGS',N'U') IS NULL
   OR COL_LENGTH(N'dbo.RENTAL_POLICIES',N'overdue_approved_at') IS NULL
   OR COL_LENGTH(N'dbo.RENTAL_POLICIES',N'overdue_approved_by') IS NULL
   THROW 51301, 'UC18 and management dependencies are missing.', 1;

IF COL_LENGTH(N'dbo.RENTAL_POLICIES',N'overdue_version') IS NULL
    ALTER TABLE dbo.RENTAL_POLICIES ADD overdue_version INT NULL;
IF COL_LENGTH(N'dbo.RENTAL_POLICIES',N'overdue_source_policy_id') IS NULL
    ALTER TABLE dbo.RENTAL_POLICIES ADD overdue_source_policy_id UNIQUEIDENTIFIER NULL;
IF COL_LENGTH(N'dbo.RENTAL_POLICIES',N'overdue_proposal_id') IS NULL
    ALTER TABLE dbo.RENTAL_POLICIES ADD overdue_proposal_id UNIQUEIDENTIFIER NULL;
IF COL_LENGTH(N'dbo.RENTAL_POLICIES',N'overdue_created_by') IS NULL
    ALTER TABLE dbo.RENTAL_POLICIES ADD overdue_created_by UNIQUEIDENTIFIER NULL;

IF OBJECT_ID(N'dbo.OVERDUE_POLICY_PROPOSALS',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.OVERDUE_POLICY_PROPOSALS (
        proposal_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_OVERDUE_POLICY_PROPOSALS PRIMARY KEY,
        facility_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.FACILITIES(facility_id),
        base_policy_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.RENTAL_POLICIES(policy_id),
        overdue_grace_days INT NOT NULL,
        penalty_type NVARCHAR(50) NOT NULL,
        daily_penalty_rate DECIMAL(5,4) NOT NULL,
        fixed_penalty_amount DECIMAL(18,2) NOT NULL,
        effective_from DATE NOT NULL,
        effective_to DATE NULL,
        reason NVARCHAR(500) NOT NULL,
        status NVARCHAR(20) NOT NULL,
        created_by UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.USERS(user_id),
        created_at DATETIME2 NOT NULL,
        submitted_at DATETIME2 NULL,
        withdrawn_at DATETIME2 NULL,
        reviewed_by UNIQUEIDENTIFIER NULL REFERENCES dbo.USERS(user_id),
        reviewed_at DATETIME2 NULL,
        decision_reason NVARCHAR(500) NULL,
        approved_policy_id UNIQUEIDENTIFIER NULL REFERENCES dbo.RENTAL_POLICIES(policy_id),
        CONSTRAINT CK_OVERDUE_POLICY_PROPOSALS_VALUES CHECK (
            overdue_grace_days >= 0 AND penalty_type IN (N'PERCENT_DAILY',N'FIXED_DAILY')
            AND daily_penalty_rate >= 0 AND fixed_penalty_amount >= 0
            AND (effective_to IS NULL OR effective_to >= effective_from)),
        CONSTRAINT CK_OVERDUE_POLICY_PROPOSALS_STATUS CHECK (status IN (N'DRAFT',N'SUBMITTED',N'WITHDRAWN',N'REJECTED',N'APPROVED'))
    );
END;

IF COL_LENGTH(N'dbo.OVERDUE_POLICY_PROPOSALS',N'withdrawn_at') IS NULL
    ALTER TABLE dbo.OVERDUE_POLICY_PROPOSALS ADD withdrawn_at DATETIME2 NULL;

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.RENTAL_POLICIES') AND name=N'UX_RENTAL_POLICIES_OVERDUE_VERSION')
    EXEC(N'CREATE UNIQUE INDEX UX_RENTAL_POLICIES_OVERDUE_VERSION ON dbo.RENTAL_POLICIES(facility_id,overdue_version) WHERE overdue_version IS NOT NULL');
