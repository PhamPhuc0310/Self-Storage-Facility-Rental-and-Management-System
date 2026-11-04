:ON ERROR EXIT
-- Separate Customer scheduling from Manager Staff assignment.
IF DB_NAME() <> N'$(SafeBoxTargetDb)' THROW 51660, 'Explicit target mismatch', 1;
IF DB_NAME() = N'SelfStorageFacilityDB' AND N'$(SafeBoxAllowPrimary)' <> N'YES'
    THROW 51661, 'Primary requires separately reviewed approval', 1;
IF OBJECT_ID(N'dbo.HANDOVER_RECORDS',N'U') IS NULL THROW 51662, 'Missing handover table', 1;
IF EXISTS(SELECT 1 FROM dbo.HANDOVER_RECORDS WHERE handover_type='RETURN' AND status='SCHEDULED' AND staff_id IS NULL)
    THROW 51663, 'Unexpected existing unassigned returns before migration', 1;
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.HANDOVER_RECORDS') AND name=N'staff_id' AND is_nullable=0)
    ALTER TABLE dbo.HANDOVER_RECORDS ALTER COLUMN staff_id UNIQUEIDENTIFIER NULL;
IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.HANDOVER_RECORDS') AND name=N'staff_id' AND is_nullable=1)
    THROW 51664, 'Staff assignment schema postcondition failed', 1;
