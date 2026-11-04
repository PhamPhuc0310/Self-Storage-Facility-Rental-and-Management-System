:ON ERROR EXIT
-- Apply only through the guarded, explicitly targeted deployment runner.
IF DB_NAME() <> N'$(SafeBoxTargetDb)' THROW 51046, 'Explicit SafeBox target database does not match the current database.', 1;
IF DB_NAME() = N'SelfStorageFacilityDB' AND N'$(SafeBoxAllowPrimary)' <> N'YES' THROW 51047, 'Primary database requires explicit reviewed deployment authorization.', 1;
IF OBJECT_ID(N'dbo.PAYMENT_TRANSACTIONS',N'U') IS NULL
    THROW 51048, 'Payment transaction dependency is missing.', 1;

IF COL_LENGTH('dbo.PAYMENT_TRANSACTIONS', 'confirmed_by') IS NULL
    ALTER TABLE dbo.PAYMENT_TRANSACTIONS ADD confirmed_by uniqueidentifier NULL;
IF COL_LENGTH('dbo.PAYMENT_TRANSACTIONS', 'confirmed_at') IS NULL
    ALTER TABLE dbo.PAYMENT_TRANSACTIONS ADD confirmed_at datetime2 NULL;
