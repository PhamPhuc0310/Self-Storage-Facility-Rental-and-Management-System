-- Run with sqlcmd from the repository root after a verified full database backup.
-- sqlcmd -S tcp:127.0.0.1,1433 -d SelfStorageFacilityDB -E -C -b -i database\08_uc19_uc24_apply.sql
:ON ERROR EXIT
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
GO
IF OBJECT_ID(N'dbo.TERMINATION_REQUESTS', N'U') IS NULL
    OR OBJECT_ID(N'dbo.PAYMENT_TRANSACTIONS', N'U') IS NULL
    OR OBJECT_ID(N'dbo.DEPOSIT_REFUNDS', N'U') IS NULL
    OR OBJECT_ID(N'dbo.HANDOVER_RECORDS', N'U') IS NULL
    OR OBJECT_ID(N'dbo.INSPECTION_REPORTS', N'U') IS NULL
    THROW 51019, 'Required SafeBox source table is missing.', 1;
IF COL_LENGTH(N'dbo.PAYMENT_TRANSACTIONS', N'contract_id') IS NULL
    OR COL_LENGTH(N'dbo.PAYMENT_TRANSACTIONS', N'payment_type') IS NULL
    OR COL_LENGTH(N'dbo.PAYMENT_TRANSACTIONS', N'idempotency_key') IS NULL
    THROW 51020, 'Apply UC11_UC12_Additive.sql before UC19/UC24.', 1;
IF EXISTS (SELECT 1 FROM dbo.TERMINATION_REQUESTS
           WHERE termination_type=N'EARLY_RETURN' AND status IN (N'REQUESTED',N'SETTLEMENT_APPROVED',N'REFUNDED')
           GROUP BY contract_id HAVING COUNT(*) > 1)
    THROW 51021, 'Duplicate active early returns require manual review.', 1;
IF EXISTS (SELECT 1 FROM dbo.PAYMENT_TRANSACTIONS
           WHERE transaction_type=N'REFUND' AND reference_code IS NOT NULL
           GROUP BY reference_code HAVING COUNT(*) > 1)
    THROW 51022, 'Duplicate refund references require manual review.', 1;
GO
:r database\07_uc19_uc24_additive.sql
GO
IF OBJECT_ID(N'dbo.EARLY_RETURN_POLICIES', N'U') IS NULL
    OR OBJECT_ID(N'dbo.EARLY_RETURN_SETTLEMENTS', N'U') IS NULL
    OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.TERMINATION_REQUESTS') AND name=N'UX_TERMINATION_EARLY_ACTIVE')
    OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.PAYMENT_TRANSACTIONS') AND name=N'UX_PAYMENT_UC19_REFUND_REFERENCE')
    OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.PAYMENT_TRANSACTIONS') AND name=N'IX_PAYMENT_REPORT_PERIOD')
    THROW 51023, 'UC19/UC24 migration postcondition failed.', 1;
COMMIT TRANSACTION;
PRINT N'UC19/UC24 migration committed.';
GO
