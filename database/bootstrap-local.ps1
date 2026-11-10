param(
    [Parameter(Mandatory=$true)][string]$TargetDatabase,
    [string]$Server='localhost'
)
$ErrorActionPreference='Stop'
if($TargetDatabase -cnotmatch '^SafeBox_Local(?:_[A-Za-z0-9]+)?$'){
    throw 'Bootstrap accepts only SafeBox_Local or SafeBox_Local_<letters/digits>; never a primary/test/shared database.'
}
$root=Split-Path $PSScriptRoot -Parent
function RunSql([string]$db,[string]$query){
    $output=& sqlcmd -S $Server -E -C -d $db -b -l 5 -W -h -1 -Q $query 2>&1
    if($LASTEXITCODE -ne 0){throw "SQL setup stopped: $($output -join ' ')"}
    return ($output -join "`n").Trim()
}
$exists=RunSql 'master' "SET NOCOUNT ON; SELECT CASE WHEN DB_ID(N'$TargetDatabase') IS NULL THEN 0 ELSE 1 END;"
if($exists -notin @('0','1')){throw 'Unexpected database-existence query result; no initialization attempted.'}
if($exists -eq '0'){[void](RunSql 'master' "CREATE DATABASE [$TargetDatabase];")}
$tables=RunSql $TargetDatabase 'SET NOCOUNT ON; SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0;'
if($tables -ne '0'){throw 'Bootstrap requires an empty database. Existing objects were preserved; use runner -VerifyOnly for an initialized database.'}
$files=@('SelfStorageFacilityDB.sql','catalog-schema.sql','03_auth_email_migration.sql','UC11_UC12_Additive.sql',
 'UC17_Additive.sql','UC18_Additive.sql','06_uc22_support_requests_compatibility.sql',
 '07_uc22_uc23_support.sql','sprint3_uc20_migration.sql','sprint3_uc21_migration.sql','08_uc19_uc24_apply.sql')
$temp=Join-Path ([IO.Path]::GetTempPath()) ('safebox-bootstrap-'+[guid]::NewGuid().ToString('N')+'.sql')
Push-Location $root
try{
 foreach($file in $files){
  # Legacy USE/CREATE targets are rebound only in an execution copy on the verified empty local DB.
  $source=(Get-Content -LiteralPath (Join-Path $PSScriptRoot $file) -Raw).Replace('SelfStorageFacilityDB',$TargetDatabase)
  $guard=":ON ERROR EXIT`nSET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF;`nIF DB_NAME()<>N'$TargetDatabase' THROW 52600,'Unexpected local bootstrap target',1;`nGO`n"
  [IO.File]::WriteAllText($temp,$guard+$source,[Text.UTF8Encoding]::new($false))
  & sqlcmd -S $Server -E -C -d $TargetDatabase -b -l 5 -i $temp
  if($LASTEXITCODE -ne 0){throw "Failed prerequisite $file; partial LOCAL setup retained, no automatic cleanup/retry."}
  Write-Output "BOOTSTRAPPED $file"
 }
 & "$PSScriptRoot/apply_10_15.ps1" -TargetDatabase $TargetDatabase -Server $Server
 [void](RunSql $TargetDatabase "INSERT dbo.ROLES(role_name,description) SELECT role_name,N'Local role catalogue' FROM (VALUES(N'CUSTOMER'),(N'FACILITY_STAFF'),(N'FACILITY_MANAGER'),(N'BUSINESS_OPERATIONS_MANAGER'),(N'SYSTEM_ADMIN')) r(role_name) WHERE NOT EXISTS(SELECT 1 FROM dbo.ROLES old WHERE old.role_name=r.role_name);")
 Write-Output "READY: $TargetDatabase, migrations10-17 and five roles. No users, financial policy or customer/payment data seeded."
}finally{Pop-Location}
