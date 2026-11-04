:ON ERROR EXIT
-- Phase 4.5 additive schema. Execute only after verifying the isolated test database identity.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME() <> N'$(SafeBoxTargetDb)' THROW 51045, 'Explicit SafeBox target database does not match the current database.', 1;
IF DB_NAME() = N'SelfStorageFacilityDB' AND N'$(SafeBoxAllowPrimary)' <> N'YES' THROW 51047, 'Primary database requires explicit reviewed deployment authorization.', 1;
IF OBJECT_ID('dbo.STORAGE_UNITS','U') IS NULL OR COL_LENGTH('dbo.STORAGE_UNITS','dimensions_verified') IS NULL
    THROW 51046, 'Migration 11 dimensions dependency missing', 1;
IF COL_LENGTH('dbo.STORAGE_UNITS','measurement_source') IS NULL
    ALTER TABLE dbo.STORAGE_UNITS ADD measurement_source varchar(30) NULL;
IF OBJECT_ID('dbo.UNIT_MEASUREMENT_SUBMISSIONS','U') IS NULL
BEGIN
    CREATE TABLE dbo.UNIT_MEASUREMENT_SUBMISSIONS (
      submission_id uniqueidentifier NOT NULL PRIMARY KEY,
      facility_id uniqueidentifier NOT NULL,
      unit_id uniqueidentifier NOT NULL,
      length_m decimal(8,3) NOT NULL,
      width_m decimal(8,3) NOT NULL,
      height_m decimal(8,3) NOT NULL,
      condition_note nvarchar(1000) NOT NULL,
      source varchar(30) NOT NULL,
      status varchar(20) NOT NULL,
      submitted_by uniqueidentifier NOT NULL,
      submitted_at datetime2 NOT NULL,
      reviewed_by uniqueidentifier NULL,
      reviewed_at datetime2 NULL,
      decision_reason nvarchar(1000) NULL,
      CONSTRAINT FK_UMS_FACILITY FOREIGN KEY (facility_id) REFERENCES dbo.FACILITIES(facility_id),
      CONSTRAINT FK_UMS_UNIT FOREIGN KEY (unit_id) REFERENCES dbo.STORAGE_UNITS(unit_id),
      CONSTRAINT CK_UMS_MEASUREMENTS CHECK (length_m>0 AND width_m>0 AND height_m>0),
      CONSTRAINT CK_UMS_SOURCE CHECK (source IN ('PHYSICAL_INSPECTION','SYNTHETIC_DEMO')),
      CONSTRAINT CK_UMS_STATUS CHECK (status IN ('SUBMITTED','APPROVED','REJECTED'))
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.UNIT_MEASUREMENT_SUBMISSIONS') AND name='UX_UMS_PENDING_UNIT')
    CREATE UNIQUE INDEX UX_UMS_PENDING_UNIT ON dbo.UNIT_MEASUREMENT_SUBMISSIONS(unit_id) WHERE status='SUBMITTED';
