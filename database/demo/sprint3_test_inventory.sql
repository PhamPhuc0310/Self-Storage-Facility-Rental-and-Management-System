-- Isolated Sprint 3 booking fixture. Synthetic dimensions are not physical inspection evidence.
-- This script is intentionally bound to SelfStorageFacilityDB_Test and can be rerun safely.
:ON ERROR EXIT
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF DB_NAME() <> N'SelfStorageFacilityDB_Test'
    THROW 52031, 'Sprint 3 inventory fixture requires SelfStorageFacilityDB_Test', 1;
IF COL_LENGTH('dbo.STORAGE_UNITS','measurement_source') IS NULL
    THROW 52032, 'Unit measurement migration is missing', 1;

DECLARE @unit uniqueidentifier = '00000000-0000-4000-8000-00000000D301';
DECLARE @facility uniqueidentifier = '3F4E5607-B0B4-F111-A965-932C2C6ADDB7';
DECLARE @source uniqueidentifier = 'FA88D008-0594-4EF0-911E-4348A976499C';
IF NOT EXISTS (
    SELECT 1 FROM dbo.STORAGE_UNITS s JOIN dbo.FACILITIES f ON f.facility_id=s.facility_id
    JOIN dbo.STORAGE_UNIT_TYPES t ON t.type_id=s.type_id
    WHERE s.unit_id=@source AND s.facility_id=@facility AND s.type_id=1
      AND s.length_m=2 AND s.width_m=3 AND s.height_m=4
      AND s.measurement_source=N'SYNTHETIC_DEMO' AND s.dimensions_verified=1
      AND f.status=N'ACTIVE' AND t.status=N'ACTIVE'
) THROW 52033, 'Reviewed synthetic source is missing or changed', 1;
IF NOT EXISTS (
    SELECT 1 FROM dbo.PRICING_POLICIES WHERE facility_id=@facility AND type_id=1
      AND status=N'ACTIVE' AND monthly_price>0
      AND effective_from<=CONVERT(date,SYSDATETIME())
      AND (effective_to IS NULL OR effective_to>=CONVERT(date,SYSDATETIME()))
) THROW 52034, 'No effective active price for demo unit', 1;
IF EXISTS (SELECT 1 FROM dbo.STORAGE_UNITS WHERE unit_number=N'S3-DEMO-AVAILABLE-01' AND unit_id<>@unit)
    THROW 52035, 'Demo unit number belongs to another row', 1;

IF NOT EXISTS (SELECT 1 FROM dbo.STORAGE_UNITS WHERE unit_id=@unit)
    INSERT dbo.STORAGE_UNITS
      (unit_id,facility_id,type_id,unit_number,floor,zone,status,condition_note,
       length_m,width_m,height_m,readiness_verified,dimensions_verified,measurement_source)
    VALUES
      (@unit,@facility,1,N'S3-DEMO-AVAILABLE-01',N'1',N'DEMO',N'AVAILABLE',
       N'Synthetic Sprint 3 test inventory; no physical inspection represented.',
       2,3,4,1,1,N'SYNTHETIC_DEMO');
ELSE IF EXISTS (
    SELECT 1 FROM dbo.STORAGE_UNITS WHERE unit_id=@unit
      AND (facility_id<>@facility OR type_id<>1 OR unit_number<>N'S3-DEMO-AVAILABLE-01'
           OR length_m<>2 OR width_m<>3 OR height_m<>4
           OR measurement_source<>N'SYNTHETIC_DEMO')
) THROW 52036, 'Existing demo unit differs; no automatic reset or overwrite', 1;

COMMIT TRANSACTION;
SELECT DB_NAME() AS database_name, unit_number, status,
       length_m*width_m*height_m AS volume_m3, measurement_source
FROM dbo.STORAGE_UNITS WHERE unit_id=@unit;
