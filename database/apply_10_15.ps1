param(
    [Parameter(Mandatory=$true)][string]$TargetDatabase,
    [string]$Server = 'localhost',
    [switch]$BootstrapExisting,
    [switch]$VerifyOnly,
    [switch]$AllowPrimary,
    [string]$VerifiedBackupPath,
    [string]$MigrationDirectory = $PSScriptRoot,
    [switch]$Migration17Only
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($MigrationDirectory)) { $MigrationDirectory = $PSScriptRoot }
if ($TargetDatabase -cnotmatch '^[A-Za-z][A-Za-z0-9_]{0,127}$') { throw 'Invalid target database name.' }
if ($TargetDatabase -eq 'SelfStorageFacilityDB' -and (!$AllowPrimary -or [string]::IsNullOrWhiteSpace($VerifiedBackupPath))) {
    throw 'Primary deployment requires -AllowPrimary and a verified backup path; review the change before running.'
}
if ($TargetDatabase -ne 'SelfStorageFacilityDB' -and $AllowPrimary) { throw '-AllowPrimary is only valid for the primary target.' }
if ($VerifyOnly -and $BootstrapExisting) { throw 'Choose verification or baseline initialization, not both.' }
if ($Migration17Only -and $BootstrapExisting) { throw 'Migration17 cannot be manually baselined.' }

function Invoke-Db([string]$Sql, [bool]$Quiet = $true) {
    $sqlcmdArgs = @('-b','-S',$Server,'-E','-C','-d',$TargetDatabase,'-l','5','-W','-h','-1','-Q',$Sql)
    $output = & sqlcmd @sqlcmdArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed: $($output -join ' ')" }
    if ($Quiet) { return ($output -join "`n").Trim() }
    $output
}
function Quote([string]$Value) { return $Value.Replace("'", "''") }

$identity = Invoke-Db 'SET NOCOUNT ON; SELECT DB_NAME();'
if ($identity -cne $TargetDatabase) { throw "Connected to '$identity', expected '$TargetDatabase'." }
Write-Output "Verified DB_NAME()=$identity"

if ($TargetDatabase -eq 'SelfStorageFacilityDB') {
    $backup = Quote $VerifiedBackupPath
    Invoke-Db "RESTORE VERIFYONLY FROM DISK=N'$backup' WITH CHECKSUM;" $false | Out-Host
}

$ledgerExists = Invoke-Db "SET NOCOUNT ON; SELECT CASE WHEN OBJECT_ID(N'dbo.SAFEBOX_SCHEMA_MIGRATIONS',N'U') IS NULL THEN 0 ELSE 1 END;"
if ($ledgerExists -eq '0') {
    if ($Migration17Only) { throw 'Migration17-only execution requires an existing verified migration ledger.' }
    if ($VerifyOnly) { throw 'Migration ledger is absent; verification cannot infer applied versions.' }
    $target = Quote $TargetDatabase
    Invoke-Db "IF DB_NAME()<>N'$target' THROW 51600,'Wrong target database',1; CREATE TABLE dbo.SAFEBOX_SCHEMA_MIGRATIONS (migration_id varchar(64) NOT NULL PRIMARY KEY, checksum_sha256 char(64) NOT NULL, applied_at datetime2 NOT NULL, status varchar(16) NOT NULL, CONSTRAINT CK_SAFEBOX_SCHEMA_MIGRATIONS_status CHECK (status IN ('RUNNING','APPLIED','BASELINED','FAILED')));" | Out-Null
    Write-Output 'Created migration ledger.'
}

$migration17Complete = @"
EXISTS(SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id
 WHERE c.object_id=OBJECT_ID('dbo.EARLY_RETURN_POLICIES') AND c.name='rental_refund_percent'
 AND t.name='decimal' AND c.precision=5 AND c.scale=2 AND c.is_nullable=0)
AND (SELECT COUNT(*) FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id
 JOIN (VALUES ('calculation_snapshot','nvarchar',-1),('refund_confirmation_reference','nvarchar',160),
 ('refund_confirmation_method','nvarchar',60),('refund_confirmed_by','uniqueidentifier',16),
 ('refund_confirmed_at','datetime2',8)) expected(column_name,type_name,byte_length)
 ON expected.column_name=c.name AND expected.type_name=t.name AND expected.byte_length=c.max_length
 WHERE c.object_id=OBJECT_ID('dbo.EARLY_RETURN_SETTLEMENTS') AND c.is_nullable=1
 AND (c.name<>'refund_confirmed_at' OR c.scale=7))=5
AND EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c
 ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id
 WHERE d.parent_object_id=OBJECT_ID('dbo.EARLY_RETURN_POLICIES') AND c.name='rental_refund_percent'
 AND REPLACE(REPLACE(d.definition,'(',''),')','')='0')
AND EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.EARLY_RETURN_POLICIES')
 AND name='CK_EARLY_RENT_RATE' AND is_disabled=0 AND is_not_trusted=0
 AND UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(definition,'[',''),']',''),'(',''),')',''),' ',''))
 IN ('RENTAL_REFUND_PERCENT=80ORRENTAL_REFUND_PERCENT=0','RENTAL_REFUND_PERCENT=0ORRENTAL_REFUND_PERCENT=80'))
AND EXISTS(SELECT 1 FROM sys.foreign_keys f JOIN sys.foreign_key_columns k ON k.constraint_object_id=f.object_id
 WHERE f.parent_object_id=OBJECT_ID('dbo.EARLY_RETURN_SETTLEMENTS') AND f.referenced_object_id=OBJECT_ID('dbo.USERS')
 AND COL_NAME(k.parent_object_id,k.parent_column_id)='refund_confirmed_by'
 AND COL_NAME(k.referenced_object_id,k.referenced_column_id)='user_id' AND f.is_disabled=0 AND f.is_not_trusted=0)
"@
$migration17Partial = "COL_LENGTH('dbo.EARLY_RETURN_POLICIES','rental_refund_percent') IS NOT NULL OR EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.EARLY_RETURN_SETTLEMENTS') AND name IN ('calculation_snapshot','refund_confirmation_reference','refund_confirmation_method','refund_confirmed_by','refund_confirmed_at'))"
$migrations = @(
    @{ Id='10_sprint3_remediation'; File='10_sprint3_remediation.sql'; Complete="COL_LENGTH('dbo.RENTAL_POLICIES','overdue_approved_at') IS NOT NULL AND COL_LENGTH('dbo.RENTAL_POLICIES','overdue_approved_by') IS NOT NULL AND EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='FK_RENTAL_POLICIES_OVERDUE_APPROVER') AND OBJECT_ID('dbo.SUPPORT_TICKETS','U') IS NOT NULL AND OBJECT_ID('dbo.SUPPORT_TICKET_REPLIES','U') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.SUPPORT_REQUESTS r WHERE NOT EXISTS(SELECT 1 FROM dbo.SUPPORT_TICKETS t WHERE t.ticket_id=r.request_id)) AND NOT EXISTS(SELECT 1 FROM dbo.SUPPORT_REQUEST_MESSAGES m WHERE NOT EXISTS(SELECT 1 FROM dbo.SUPPORT_TICKET_REPLIES p WHERE p.reply_id=m.message_id))"; Partial="COL_LENGTH('dbo.RENTAL_POLICIES','overdue_approved_at') IS NOT NULL OR COL_LENGTH('dbo.RENTAL_POLICIES','overdue_approved_by') IS NOT NULL" },
    @{ Id='11_management_phase1'; File='11_management_phase1.sql'; Complete="COL_LENGTH('dbo.FACILITIES','facility_code') IS NOT NULL AND COL_LENGTH('dbo.FACILITIES','region_code') IS NOT NULL AND COL_LENGTH('dbo.STORAGE_UNITS','length_m') IS NOT NULL AND COL_LENGTH('dbo.STORAGE_UNITS','width_m') IS NOT NULL AND COL_LENGTH('dbo.STORAGE_UNITS','height_m') IS NOT NULL AND COL_LENGTH('dbo.STORAGE_UNITS','readiness_verified') IS NOT NULL AND COL_LENGTH('dbo.STORAGE_UNITS','dimensions_verified') IS NOT NULL AND OBJECT_ID('dbo.STAFF_REGION_ASSIGNMENTS','U') IS NOT NULL AND EXISTS(SELECT 1 FROM sys.indexes WHERE name='UX_FACILITIES_facility_code' AND object_id=OBJECT_ID('dbo.FACILITIES'))"; Partial="COL_LENGTH('dbo.FACILITIES','facility_code') IS NOT NULL OR COL_LENGTH('dbo.STORAGE_UNITS','length_m') IS NOT NULL OR OBJECT_ID('dbo.STAFF_REGION_ASSIGNMENTS','U') IS NOT NULL" },
    @{ Id='12_phase2a_pricing'; File='12_phase2a_pricing.sql'; Complete="OBJECT_ID('dbo.PRICE_ADJUSTMENT_PROPOSALS','U') IS NOT NULL AND (SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.PRICING_POLICIES') AND name IN ('deposit_amount','daily_overdue_rate','late_payment_rate','early_termination_fee','fee_waiver_allowed') AND is_nullable=1)=5"; Partial="OBJECT_ID('dbo.PRICE_ADJUSTMENT_PROPOSALS','U') IS NOT NULL" },
    @{ Id='13_phase2b_overdue_governance'; File='13_phase2b_overdue_governance.sql'; Complete="OBJECT_ID('dbo.OVERDUE_POLICY_PROPOSALS','U') IS NOT NULL AND COL_LENGTH('dbo.RENTAL_POLICIES','overdue_version') IS NOT NULL AND COL_LENGTH('dbo.RENTAL_POLICIES','overdue_source_policy_id') IS NOT NULL AND COL_LENGTH('dbo.RENTAL_POLICIES','overdue_proposal_id') IS NOT NULL AND COL_LENGTH('dbo.RENTAL_POLICIES','overdue_created_by') IS NOT NULL AND COL_LENGTH('dbo.OVERDUE_POLICY_PROPOSALS','withdrawn_at') IS NOT NULL AND EXISTS(SELECT 1 FROM sys.indexes WHERE name='UX_RENTAL_POLICIES_OVERDUE_VERSION' AND object_id=OBJECT_ID('dbo.RENTAL_POLICIES'))"; Partial="OBJECT_ID('dbo.OVERDUE_POLICY_PROPOSALS','U') IS NOT NULL OR COL_LENGTH('dbo.RENTAL_POLICIES','overdue_version') IS NOT NULL" },
    @{ Id='14_unit_activation'; File='14_unit_activation.sql'; Complete="OBJECT_ID('dbo.UNIT_MEASUREMENT_SUBMISSIONS','U') IS NOT NULL AND COL_LENGTH('dbo.STORAGE_UNITS','measurement_source') IS NOT NULL AND EXISTS(SELECT 1 FROM sys.indexes WHERE name='UX_UMS_PENDING_UNIT' AND object_id=OBJECT_ID('dbo.UNIT_MEASUREMENT_SUBMISSIONS'))"; Partial="OBJECT_ID('dbo.UNIT_MEASUREMENT_SUBMISSIONS','U') IS NOT NULL OR COL_LENGTH('dbo.STORAGE_UNITS','measurement_source') IS NOT NULL" },
    @{ Id='15_phase46_payment_confirmation'; File='15_phase46_payment_confirmation.sql'; Complete="COL_LENGTH('dbo.PAYMENT_TRANSACTIONS','confirmed_by') IS NOT NULL AND COL_LENGTH('dbo.PAYMENT_TRANSACTIONS','confirmed_at') IS NOT NULL"; Partial="COL_LENGTH('dbo.PAYMENT_TRANSACTIONS','confirmed_by') IS NOT NULL OR COL_LENGTH('dbo.PAYMENT_TRANSACTIONS','confirmed_at') IS NOT NULL" },
    @{ Id='16_return_manager_assignment'; File='16_return_manager_assignment.sql'; Complete="EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.HANDOVER_RECORDS') AND name='staff_id' AND is_nullable=1)"; Partial="COL_LENGTH('dbo.HANDOVER_RECORDS','staff_id') IS NULL" },
    @{ Id='17_early_return_rent_refund'; File='17_early_return_rent_refund.sql'; Complete=$migration17Complete; Partial=$migration17Partial; RequiredChecksum='264c2de6a875d30b7129ffc12dc6d75cf0089b8b1401f9c9983c862bba0e600e' }
)

foreach ($migration in $migrations) {
    $id = $migration.Id
    $file = Join-Path $MigrationDirectory $migration.File
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing migration file: $file" }
    $checksum = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($migration.RequiredChecksum -and $checksum -cne $migration.RequiredChecksum) {
        throw "Reviewed checksum mismatch for $id; no schema or ledger write is permitted."
    }
    $state = Invoke-Db "SET NOCOUNT ON; SELECT CASE WHEN $($migration.Complete) THEN 'COMPLETE' WHEN $($migration.Partial) THEN 'PARTIAL' ELSE 'ABSENT' END;"
    $row = Invoke-Db "SET NOCOUNT ON; SELECT checksum_sha256+'|'+status FROM dbo.SAFEBOX_SCHEMA_MIGRATIONS WHERE migration_id='$id';"
    if ($row) {
        $parts = $row.Split('|')
        if ($parts[0] -cne $checksum) { throw "Checksum mismatch for $id. Recorded=$($parts[0]); file=$checksum. Manual review required." }
        if ($parts[1] -notin @('APPLIED','BASELINED') -or $state -ne 'COMPLETE' -or ($id -eq '17_early_return_rent_refund' -and $parts[1] -ne 'APPLIED')) {
            throw "Migration $id has status $($parts[1]) and schema state $state; manual review required."
        }
        Write-Output "VERIFIED $id $($parts[1]) $checksum"
        continue
    }
    if ($Migration17Only -and $id -ne '17_early_return_rent_refund') {
        throw "Migration17-only execution requires verified existing history for $id; earlier migrations will not be applied or baselined."
    }
    if ($state -eq 'PARTIAL') { throw "Partial schema for $id; manual review required before ledger initialization or apply." }
    if ($state -eq 'COMPLETE') {
        if ($id -eq '17_early_return_rent_refund') { throw 'Migration17 schema exists without its ledger entry; reviewed reconciliation is required.' }
        if (!$BootstrapExisting) { throw "Existing schema for $id has no ledger entry. Run reviewed -BootstrapExisting; never replay blindly." }
        if ($VerifyOnly) { throw "Untracked existing migration: $id" }
        Invoke-Db "IF DB_NAME()<>N'$(Quote $TargetDatabase)' THROW 51601,'Wrong target database',1; INSERT dbo.SAFEBOX_SCHEMA_MIGRATIONS(migration_id,checksum_sha256,applied_at,status) VALUES('$id','$checksum',SYSDATETIME(),'BASELINED');" | Out-Null
        Write-Output "BASELINED $id $checksum"
        continue
    }
    if ($VerifyOnly) { throw "Missing migration $id; verification cannot apply it." }
    if ($id -eq '17_early_return_rent_refund') {
        # One connection and outer transaction keep the unchanged migration's nested COMMIT atomic with its ledger.
        $sourceBytes = [System.IO.File]::ReadAllBytes($file)
        $hasher = [System.Security.Cryptography.SHA256]::Create()
        try { $sourceHash = [BitConverter]::ToString($hasher.ComputeHash($sourceBytes)).Replace('-','').ToLowerInvariant() }
        finally { $hasher.Dispose() }
        if ($sourceHash -cne $checksum) { throw 'Migration17 changed while preparing execution.' }
        $source = [System.Text.Encoding]::UTF8.GetString($sourceBytes)
        $target = Quote $TargetDatabase
        if ($TargetDatabase -cmatch '^SafeBox_Local(?:_[A-Za-z0-9]+)?$') {
            # Extend only the execution target guard; preserve the canonical migration bytes/checksum and DDL.
            $allowed = "(N'SelfStorageFacilityDB_Test',"
            if (!$source.Contains($allowed)) { throw 'Migration17 development target guard was not found.' }
            $source = $source.Replace($allowed, "(N'$target',N'SelfStorageFacilityDB_Test',")
            Write-Output "Development target allowlisted: $TargetDatabase (canonical migration17 unchanged)"
        }
        $primaryGuard = ''
        if ($TargetDatabase -eq 'SelfStorageFacilityDB') {
            $backup = Quote $VerifiedBackupPath
            $primaryGuard = @"
IF @@SERVERNAME<>N'phucpham\SQLEXPRESS' THROW 51700,'Unexpected primary server',1;
IF NOT EXISTS(SELECT 1 FROM msdb.dbo.backupset b JOIN msdb.dbo.backupmediafamily f ON f.media_set_id=b.media_set_id
 WHERE b.database_name=N'SelfStorageFacilityDB' AND b.type='D' AND b.is_copy_only=1 AND b.has_backup_checksums=1
 AND b.backup_finish_date>DATEADD(minute,-30,SYSDATETIME()) AND f.physical_device_name=N'$backup')
 THROW 51701,'Recent verified primary backup required',1;
EXEC sys.sp_set_session_context @key=N'SafeBoxPrimaryApproval',@value=N'REFUND80-20261112';
"@
        }
        $batch = @"
:ON ERROR EXIT
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME() COLLATE Latin1_General_100_BIN2<>N'$target' OR DATALENGTH(DB_NAME())<>DATALENGTH(N'$target')
 THROW 51702,'Unexpected migration17 target',1;
$primaryGuard
BEGIN TRAN;
DECLARE @migration17Lock int;
EXEC @migration17Lock=sys.sp_getapplock @Resource=N'SafeBox-Migration17',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
IF @migration17Lock<0 THROW 51703,'Migration17 execution lock unavailable',1;
IF EXISTS(SELECT 1 FROM dbo.SAFEBOX_SCHEMA_MIGRATIONS WHERE migration_id='$id') BEGIN
 IF NOT EXISTS(SELECT 1 FROM dbo.SAFEBOX_SCHEMA_MIGRATIONS WHERE migration_id='$id' AND checksum_sha256='$checksum' AND status='APPLIED')
 OR NOT ($migration17Complete) THROW 51704,'Concurrent ledger/schema mismatch',1;
 COMMIT;
 EXEC sys.sp_set_session_context @key=N'SafeBoxPrimaryApproval',@value=NULL;
 PRINT 'VERIFIED $id APPLIED $checksum'; RETURN;
END;
IF ($migration17Partial) THROW 51705,'Untracked partial or complete migration17 schema',1;
INSERT dbo.SAFEBOX_SCHEMA_MIGRATIONS(migration_id,checksum_sha256,applied_at,status)
 VALUES('$id','$checksum',SYSDATETIME(),'RUNNING');
$source
IF @@TRANCOUNT<>1 THROW 51706,'Migration17 transaction boundary changed',1;
IF NOT ($migration17Complete) THROW 51707,'Migration17 postcondition failed',1;
UPDATE dbo.SAFEBOX_SCHEMA_MIGRATIONS SET status='APPLIED',applied_at=SYSDATETIME()
 WHERE migration_id='$id' AND checksum_sha256='$checksum' AND status='RUNNING';
IF @@ROWCOUNT<>1 THROW 51708,'Migration17 ledger update did not affect exactly one row',1;
COMMIT;
EXEC sys.sp_set_session_context @key=N'SafeBoxPrimaryApproval',@value=NULL;
PRINT 'APPLIED $id $checksum';
"@
        $batchFile = Join-Path ([System.IO.Path]::GetTempPath()) ('safebox-migration17-'+[guid]::NewGuid().ToString('N')+'.sql')
        [System.IO.File]::WriteAllText($batchFile,$batch,[System.Text.UTF8Encoding]::new($false))
        Write-Output "ATOMIC_BATCH $batchFile"
        & sqlcmd -b -S $Server -E -C -d $TargetDatabase -l 5 -i $batchFile
        if ($LASTEXITCODE -ne 0) { throw 'Migration17 failed or its commit outcome could not be confirmed. Inspect both schema and ledger before retrying; the retained batch provides execution evidence.' }
        continue
    }
    Invoke-Db "IF DB_NAME()<>N'$(Quote $TargetDatabase)' THROW 51602,'Wrong target database',1; INSERT dbo.SAFEBOX_SCHEMA_MIGRATIONS(migration_id,checksum_sha256,applied_at,status) VALUES('$id','$checksum',SYSDATETIME(),'RUNNING');" | Out-Null
    $approval = if ($AllowPrimary) { 'YES' } else { 'NO' }
    $sqlcmdArgs = @('-b','-S',$Server,'-E','-C','-d',$TargetDatabase,'-l','5','-v',"SafeBoxTargetDb=$TargetDatabase","SafeBoxAllowPrimary=$approval",'-i',$file)
    $output = & sqlcmd @sqlcmdArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        Invoke-Db "UPDATE dbo.SAFEBOX_SCHEMA_MIGRATIONS SET status='FAILED' WHERE migration_id='$id' AND status='RUNNING';" | Out-Null
        throw "Migration $id failed and is locked for manual review: $($output -join ' ')"
    }
    $state = Invoke-Db "SET NOCOUNT ON; SELECT CASE WHEN $($migration.Complete) THEN 'COMPLETE' ELSE 'INCOMPLETE' END;"
    if ($state -ne 'COMPLETE') {
        Invoke-Db "UPDATE dbo.SAFEBOX_SCHEMA_MIGRATIONS SET status='FAILED' WHERE migration_id='$id' AND status='RUNNING';" | Out-Null
        throw "Postcondition failed for $id; manual review required."
    }
    Invoke-Db "UPDATE dbo.SAFEBOX_SCHEMA_MIGRATIONS SET status='APPLIED',applied_at=SYSDATETIME() WHERE migration_id='$id' AND status='RUNNING';" | Out-Null
    Write-Output "APPLIED $id $checksum"
}
