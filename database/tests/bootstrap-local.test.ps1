$ErrorActionPreference='Stop'
$bootstrap=Join-Path (Split-Path $PSScriptRoot -Parent) 'bootstrap-local.ps1'
foreach($name in @('SelfStorageFacilityDB','SelfStorageFacilityDB_Test','master','SafeBox_Local;DROP','SafeBox_Local_bad-name')){
 $rejected=$false
 try{& $bootstrap -TargetDatabase $name -Server 'invalid-host-must-never-be-contacted'}
 catch{$rejected=$_.Exception.Message.StartsWith('Bootstrap accepts only')}
 if(!$rejected){throw "Unsafe target was not rejected before connecting: $name"}
}
Write-Output 'PASS: five unsafe/primary/shared database targets rejected before SQL access.'
