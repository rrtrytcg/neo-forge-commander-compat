$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$gatePath=Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json'
$gate=Get-Content $gatePath -Raw|ConvertFrom-Json
if($gate.results.Count -ne 25 -or ($gate.results|Measure-Object checks -Sum).Sum -ne 1935) {throw 'Full native gate required'}
foreach($r in $gate.results) {if((Get-FileHash $r.log).Hash -ne $r.sha256) {throw "Native log changed: $($r.suite)"}}
$checkLog=Join-Path $PSScriptRoot 'target/card-use-check.log'
& (Join-Path $PSScriptRoot 'Check-CardUseEvidence.ps1') *> $checkLog
if((Get-Content $checkLog -Raw) -notmatch 'DONE (\d+) Greater Good card-use checks') {throw 'Card-use gate incomplete'}
$cardUse=Get-Content (Join-Path $PSScriptRoot 'card-use-evidence.json') -Raw|ConvertFrom-Json
$coverage=Get-Content (Join-Path $repo 'arena/coverage/coverage.json') -Raw|ConvertFrom-Json
if('Greater Good' -notin $coverage.cards.verified -or $coverage.cards.mechanicallyUnlocked -ne 45 -or $coverage.cards.strategicallyVerified -ne 35 -or $coverage.cards.unlockedOnly.Count -ne 10 -or $coverage.inventory.decksWithZeroBlocked -ne 34 -or $coverage.cards.registryUnwired.Count -ne 0) {throw 'Refresh coverage after recording verified evidence'}
$head=(& git -C $repo rev-parse HEAD).Trim()
if(!$head.StartsWith($coverage.revision)) {throw 'Coverage not refreshed from HEAD'}
$old=Get-Content (Join-Path $repo 'arena/version-compat/verification-summary.json') -Raw|ConvertFrom-Json
$install=(Resolve-Path (Join-Path $repo '../NeoForge')).Path
$installed=@($old.nativeEvidence.files|Where-Object {$_.path.StartsWith($install,[StringComparison]::OrdinalIgnoreCase)})
foreach($f in $installed) {if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) {throw 'Installed app changed'}}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $gate.runtime 'res/cardsfolder/cardsfolder.zip'))
try {
    $entry=$zip.GetEntry('g/greater_good.txt'); $reader=[IO.StreamReader]::new($entry.Open())
    try {$packed=$reader.ReadToEnd().Replace("`r`n","`n").TrimEnd()} finally {$reader.Dispose()}
    $scriptPath=Join-Path $repo 'forge-gui/res/cardsfolder/g/greater_good.txt'
    if($packed -cne (Get-Content $scriptPath -Raw).Replace("`r`n","`n").TrimEnd()) {throw 'Original Greater Good script differs'}
} finally {$zip.Dispose()}
$inputs=@(Get-ChildItem $PSScriptRoot -File|Where-Object Name -notin @('verification-summary.json','card-use-evidence.json')|ForEach-Object FullName)
$inputs+=@(Get-ChildItem (Join-Path $PSScriptRoot 'decks') -File|ForEach-Object FullName)
$inputs+=@('forge-ai/src/main/java/forge/ai/GreaterGoodAi.java','forge-ai/src/main/java/forge/ai/ability/DrawAi.java','forge-ai/src/main/java/forge/ai/ComputerUtil.java','forge-ai/src/main/java/forge/ai/AiCostDecision.java','forge-gui/res/ai/overrides/greater-good.properties','arena/coverage/card-status.tsv','arena/coverage/COVERAGE.md','arena/coverage/coverage.json','arena/COMMANDER_COMPAT_WORKFLOW.md'|ForEach-Object {Join-Path $repo $_})
$inputs+=@($gatePath,$scriptPath,$checkLog,$gate.candidate)
$diagnosticDir=Join-Path $PSScriptRoot 'target/diagnostics/combat-gigantosaurus-on-98021'
$diagnosticFiles=@('probe.log','summary.json','original-Combat.dck'|ForEach-Object {Join-Path $diagnosticDir $_})
@{date='2026-10-17';revision=$head;card='Greater Good';status='verified';engine=$gate.engine;
 nativeGate=@{candidate=$gate.candidate;candidateSha256=$gate.candidateSha256;results=$gate.results;total=1935};cardUse=$cardUse;
 coverage=@{inventory=$coverage.inventory;blocked=$coverage.blocked;unlocked=$coverage.cards.mechanicallyUnlocked;verified=$coverage.cards.strategicallyVerified;unlockedOnly=$coverage.cards.unlockedOnly};
 installedApplicationModified=$false;installedBaselineFiles=$installed;originalScriptMatchesInstalled=$true;
 initialCombatDiagnostic=@{acceptedWitness=$false;reason='Cast but no activation before clock; vanilla board stalled';files=@($diagnosticFiles|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}})};
 files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});
 scope='Opt-in doomed-creature rescue correctness and autonomous native custom-deck use; no strength, winner or game-completion claim'}|ConvertTo-Json -Depth 18|Set-Content (Join-Path $PSScriptRoot 'verification-summary.json')
Write-Output 'Greater Good native/card-use verification and refreshed coverage PASS'
