-- Required mapped columns formerly bundled with owner-specific catalog seed data.
:ON ERROR EXIT
IF DB_NAME() NOT LIKE N'SafeBox[_]Local%' THROW 52601,'Local schema initialization only',1;
IF COL_LENGTH('dbo.FACILITIES','demo_intro') IS NULL ALTER TABLE dbo.FACILITIES ADD demo_intro nvarchar(max) NULL;
IF COL_LENGTH('dbo.FACILITIES','demo_safety') IS NULL ALTER TABLE dbo.FACILITIES ADD demo_safety nvarchar(max) NULL;
IF COL_LENGTH('dbo.FACILITIES','demo_access') IS NULL ALTER TABLE dbo.FACILITIES ADD demo_access nvarchar(max) NULL;
IF COL_LENGTH('dbo.FACILITIES','demo_terms') IS NULL ALTER TABLE dbo.FACILITIES ADD demo_terms nvarchar(max) NULL;
IF COL_LENGTH('dbo.FACILITIES','image_path') IS NULL ALTER TABLE dbo.FACILITIES ADD image_path nvarchar(500) NULL;
IF COL_LENGTH('dbo.STORAGE_UNIT_TYPES','demo_intro') IS NULL ALTER TABLE dbo.STORAGE_UNIT_TYPES ADD demo_intro nvarchar(max) NULL;
IF COL_LENGTH('dbo.STORAGE_UNIT_TYPES','demo_goods') IS NULL ALTER TABLE dbo.STORAGE_UNIT_TYPES ADD demo_goods nvarchar(max) NULL;
IF COL_LENGTH('dbo.STORAGE_UNIT_TYPES','demo_conditions') IS NULL ALTER TABLE dbo.STORAGE_UNIT_TYPES ADD demo_conditions nvarchar(max) NULL;
IF COL_LENGTH('dbo.STORAGE_UNIT_TYPES','image_path') IS NULL ALTER TABLE dbo.STORAGE_UNIT_TYPES ADD image_path nvarchar(500) NULL;
