:ON ERROR EXIT
-- Run manually after backup, against a non-production clone first. No existing rows are rewritten.
IF DB_NAME() <> N'$(SafeBoxTargetDb)' THROW 51100, 'Explicit SafeBox target database does not match the current database.', 1;
IF DB_NAME() = N'SelfStorageFacilityDB' AND N'$(SafeBoxAllowPrimary)' <> N'YES' THROW 51101, 'Primary database requires explicit reviewed deployment authorization.', 1;
IF COL_LENGTH('dbo.FACILITIES', 'facility_code') IS NULL
    ALTER TABLE dbo.FACILITIES ADD facility_code NVARCHAR(30) NULL;
IF COL_LENGTH('dbo.FACILITIES', 'region_code') IS NULL
    ALTER TABLE dbo.FACILITIES ADD region_code NVARCHAR(30) NULL;
GO
-- Required SQL Server session settings for a filtered index (including sqlcmd sessions).
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_FACILITIES_facility_code' AND object_id = OBJECT_ID('dbo.FACILITIES'))
    CREATE UNIQUE INDEX UX_FACILITIES_facility_code ON dbo.FACILITIES(facility_code) WHERE facility_code IS NOT NULL;
IF COL_LENGTH('dbo.STORAGE_UNITS', 'length_m') IS NULL
    ALTER TABLE dbo.STORAGE_UNITS ADD length_m DECIMAL(8,3) NULL;
IF COL_LENGTH('dbo.STORAGE_UNITS', 'width_m') IS NULL
    ALTER TABLE dbo.STORAGE_UNITS ADD width_m DECIMAL(8,3) NULL;
IF COL_LENGTH('dbo.STORAGE_UNITS', 'height_m') IS NULL
    ALTER TABLE dbo.STORAGE_UNITS ADD height_m DECIMAL(8,3) NULL;
IF COL_LENGTH('dbo.STORAGE_UNITS', 'readiness_verified') IS NULL
    ALTER TABLE dbo.STORAGE_UNITS ADD readiness_verified BIT NOT NULL CONSTRAINT DF_STORAGE_UNITS_readiness_verified DEFAULT (0);
IF COL_LENGTH('dbo.STORAGE_UNITS', 'dimensions_verified') IS NULL
    ALTER TABLE dbo.STORAGE_UNITS ADD dimensions_verified BIT NOT NULL CONSTRAINT DF_STORAGE_UNITS_dimensions_verified DEFAULT (0);
IF OBJECT_ID('dbo.STAFF_REGION_ASSIGNMENTS', 'U') IS NULL
    CREATE TABLE dbo.STAFF_REGION_ASSIGNMENTS (
        assignment_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        user_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.USERS(user_id),
        region_code NVARCHAR(30) NOT NULL,
        status NVARCHAR(20) NOT NULL,
        assigned_by UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.USERS(user_id),
        assigned_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );
