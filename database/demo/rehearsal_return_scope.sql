:ON ERROR EXIT
-- Isolated reference-data rehearsal only; no physical inventory claims.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME()<>N'SafeBox_FinalRehearsal_20261030' THROW 51940,'Wrong rehearsal database',1;
DECLARE @facility uniqueidentifier='414E5607-B0B4-F111-A965-932C2C6ADDB7',
        @staff uniqueidentifier='384E5607-B0B4-F111-A965-932C2C6ADDB7',
        @admin uniqueidentifier='3E4E5607-B0B4-F111-A965-932C2C6ADDB7',
        @assignment uniqueidentifier='00000000-0000-4000-8000-00000000D601';
IF NOT EXISTS(SELECT 1 FROM dbo.FACILITIES WHERE facility_id=@facility AND (region_code IS NULL OR region_code=N'Q7'))
    THROW 51941,'Facility region cannot be inferred safely',1;
IF NOT EXISTS(SELECT 1 FROM dbo.USER_FACILITY_ASSIGNMENTS WHERE user_id=@staff AND facility_id=@facility AND status='ACTIVE')
    THROW 51942,'Existing Staff facility scope missing',1;
IF NOT EXISTS(SELECT 1 FROM dbo.USERS WHERE user_id=@admin AND status='ACTIVE')
    THROW 51943,'Admin reference missing',1;
IF EXISTS(SELECT 1 FROM dbo.STAFF_REGION_ASSIGNMENTS WHERE assignment_id=@assignment AND (user_id<>@staff OR region_code<>N'Q7'))
    THROW 51944,'Synthetic assignment ID collision',1;
BEGIN TRANSACTION;
UPDATE dbo.FACILITIES SET region_code=N'Q7' WHERE facility_id=@facility AND region_code IS NULL;
IF NOT EXISTS(SELECT 1 FROM dbo.STAFF_REGION_ASSIGNMENTS WHERE user_id=@staff AND region_code=N'Q7' AND status='ACTIVE')
    INSERT dbo.STAFF_REGION_ASSIGNMENTS(assignment_id,user_id,region_code,status,assigned_by,assigned_at)
    VALUES(@assignment,@staff,N'Q7',N'ACTIVE',@admin,SYSDATETIME());
COMMIT;
SELECT f.name,f.region_code,a.user_id,a.status FROM dbo.FACILITIES f JOIN dbo.STAFF_REGION_ASSIGNMENTS a ON a.region_code=f.region_code WHERE f.facility_id=@facility AND a.user_id=@staff;
