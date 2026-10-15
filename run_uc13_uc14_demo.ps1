param([string]$Server='localhost',[string]$User='sa',[string]$Password='12345',[string]$SqlCmd='sqlcmd.exe')
$ErrorActionPreference='Stop'; $db=Join-Path $PSScriptRoot 'database'
foreach($file in @('SelfStorageFacilityDB.sql','03_auth_email_migration.sql','UC11_UC12_Additive.sql','04_demo_catalog_seed.sql','05_uc13_uc14_demo_seed.sql')) { $path=Join-Path $db $file; if(!(Test-Path $path)){throw "Không tìm thấy $path"}; Write-Host "Running $file ..."; & $SqlCmd -S $Server -U $User -P $Password -b -i $path; if($LASTEXITCODE -ne 0){throw "SQLCMD thất bại ở $file với mã $LASTEXITCODE"} }
Write-Host 'UC13/UC14 demo database is ready.'
