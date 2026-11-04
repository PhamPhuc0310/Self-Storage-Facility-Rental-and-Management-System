-- Prefer restoring the verified disposable pre-demo snapshot.
-- This fail-closed placeholder prevents deletion without an ownership manifest.
:ON ERROR EXIT
IF DB_NAME() <> N'$(SafeBoxDemoDb)' OR DB_NAME() = N'SelfStorageFacilityDB'
    THROW 51920, 'Wrong or primary database', 1;
THROW 51921, 'No reviewed owned-record manifest; restore the isolated snapshot', 1;
