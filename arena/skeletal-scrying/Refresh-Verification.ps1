$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$gatePath=Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json';$gate=Get-Content $gatePath -Raw|ConvertFrom-Json
if($gate.engine -ne 'NeoForge 1.0.25' -or $gate.results.Count -ne 34 -or ($gate.results|Measure-Object checks -Sum).Sum -ne 3314){throw 'Complete full native gate first'}
foreach($r in $gate.results){if((Get-FileHash $r.log).Hash -ne $r.sha256){throw "Native log changed: $($r.suite)"}}
foreach($f in $gate.files){if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256){throw "Native input changed: $($f.path)"}}
if((Get-FileHash $gate.candidate).Hash -ne $gate.candidateSha256){throw 'Candidate changed'}
$checkLog=Join-Path $PSScriptRoot 'target/card-use-check.log'
& (Join-Path $PSScriptRoot 'Check-CardUseEvidence.ps1') *> $checkLog
if((Get-Content $checkLog -Raw) -notmatch 'DONE (\d+) Scrying card-use checks'){throw 'Card-use gate incomplete'}
$cardUse=Get-Content (Join-Path $PSScriptRoot 'card-use-evidence.json') -Raw|ConvertFrom-Json
$coverage=Get-Content (Join-Path $repo 'arena/coverage/coverage.json') -Raw|ConvertFrom-Json
if('Skeletal Scrying' -notin $coverage.cards.verified -or $coverage.cards.mechanicallyUnlocked -ne 54 -or $coverage.cards.strategicallyVerified -ne 44 -or $coverage.cards.unlockedOnly.Count -ne 10 -or $coverage.inventory.decksWithZeroBlocked -ne 44 -or $coverage.cards.registryUnwired.Count -ne 0){throw 'Refresh coverage after evidence gates pass'}
$head=(& git -C $repo rev-parse HEAD).Trim();if(!$head.StartsWith($coverage.revision)){throw 'Coverage not refreshed from HEAD'}
$old=Get-Content (Join-Path $repo 'arena/version-compat/verification-summary.json') -Raw|ConvertFrom-Json
$install=(Resolve-Path (Join-Path $repo '../NeoForge')).Path
$installed=@($old.nativeEvidence.files|Where-Object {$_.path.StartsWith($install,[StringComparison]::OrdinalIgnoreCase)})
if($installed.Count -ne 6){throw 'Expected six installed baseline files'}
foreach($f in $installed){if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256){throw 'Installed app changed'}}
$unrelatedPath=Join-Path $PSScriptRoot 'target/unrelated-baseline.json';$unrelated=Get-Content $unrelatedPath -Raw|ConvertFrom-Json
if($unrelated.Count -ne 16){throw 'Expected sixteen unrelated baseline files'}
foreach($f in $unrelated){if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256){throw "Unrelated work changed: $($f.path)"}}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $gate.runtime 'res/cardsfolder/cardsfolder.zip'))
try{
    $entry=$zip.GetEntry('s/skeletal_scrying.txt');$reader=[IO.StreamReader]::new($entry.Open())
    try{$packed=$reader.ReadToEnd().Replace("`r`n","`n").TrimEnd()}finally{$reader.Dispose()}
    $scriptPath=Join-Path $repo 'forge-gui/res/cardsfolder/s/skeletal_scrying.txt'
    if($packed -cne (Get-Content $scriptPath -Raw).Replace("`r`n","`n").TrimEnd()){throw 'Original Scrying script differs'}
}finally{$zip.Dispose()}
$inputs=@(Get-ChildItem $PSScriptRoot -File|Where-Object Name -notin @('verification-summary.json','card-use-evidence.json')|ForEach-Object FullName)
$inputs+=@(Get-ChildItem (Join-Path $PSScriptRoot 'decks') -File|ForEach-Object FullName)
$inputs+=@('forge-ai/src/main/java/forge/ai/SkeletalScryingAi.java','forge-ai/src/main/java/forge/ai/ability/DrawAi.java','forge-ai/src/main/java/forge/ai/AiController.java','forge-ai/src/main/java/forge/ai/ComputerUtil.java','forge-gui/res/ai/overrides/skeletal-scrying.properties','arena/coverage/card-status.tsv','arena/coverage/COVERAGE.md','arena/coverage/coverage.json','arena/COMMANDER_COMPAT_WORKFLOW.md'|ForEach-Object {Join-Path $repo $_})
$inputs+=@($gatePath,$scriptPath,$checkLog,$gate.candidate,$unrelatedPath,(Join-Path $PSScriptRoot 'card-use-evidence.json'))
@{date='2026-10-18';revision=$head;card='Skeletal Scrying';status='verified';engine=$gate.engine;
 nativeGate=@{candidate=$gate.candidate;candidateSha256=$gate.candidateSha256;results=$gate.results;total=3314};cardUse=$cardUse;
 coverage=@{inventory=$coverage.inventory;blocked=$coverage.blocked;unlocked=$coverage.cards.mechanicallyUnlocked;verified=$coverage.cards.strategicallyVerified;unlockedOnly=$coverage.cards.unlockedOnly};
 installedApplicationModified=$false;installedBaselineFiles=$installed;originalScriptMatchesInstalled=$true;unrelatedBaselineFiles=$unrelated;
 files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});
 scope='Opt-in bounded native draw/life loss and protected graveyard payment; native correctness and autonomous card use only, no strength/winner/completion claim'}|ConvertTo-Json -Depth 18|Set-Content (Join-Path $PSScriptRoot 'verification-summary.json')
Write-Output 'Skeletal Scrying native/card-use verification and refreshed coverage PASS'
