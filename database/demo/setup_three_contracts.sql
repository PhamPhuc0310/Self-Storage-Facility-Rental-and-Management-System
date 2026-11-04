-- Preparation gate. No fixture writes until the manifest is populated and reviewed.
-- Target only an identity-verified disposable restore; never primary.
:ON ERROR EXIT
IF DB_NAME() <> N'$(SafeBoxDemoDb)' OR DB_NAME() = N'SelfStorageFacilityDB'
    THROW 51910, 'Wrong or primary database', 1;
IF COL_LENGTH('dbo.STORAGE_UNITS','dimensions_verified') IS NULL
    OR COL_LENGTH('dbo.PAYMENT_TRANSACTIONS','confirmed_at') IS NULL
    OR OBJECT_ID('dbo.STAFF_REGION_ASSIGNMENTS','U') IS NULL
    THROW 51911, 'Demo schema migrations incomplete', 1;
THROW 51912, 'Fixture manifest and exact references require clone rehearsal and review; no writes made', 1;
