$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$gatePath=Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json';$gate=Get-Content $gatePath -Raw|ConvertFrom-Json
if($gate.engine -ne 'NeoForge 1.0.25' -or $gate.results.Count -ne 36 -or ($gate.results|Measure-Object checks -Sum).Sum -ne 3671){throw 'Complete full native gate first'}
foreach($r in $gate.results){if((Get-FileHash $r.log).Hash -ne $r.sha256){throw "Native log changed: $($r.suite)"}}
foreach($f in $gate.files){if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256){throw "Native input changed: $($f.path)"}}
if((Get-FileHash $gate.candidate).Hash -ne $gate.candidateSha256){throw 'Candidate changed'}
$checkLog=Join-Path $PSScriptRoot 'target/card-use-check.log'
& (Join-Path $PSScriptRoot 'Check-CardUseEvidence.ps1') *> $checkLog
if((Get-Content $checkLog -Raw) -notmatch 'DONE (\d+) Signpost card-use checks'){throw 'Card-use gate incomplete'}
$cardUse=Get-Content (Join-Path $PSScriptRoot 'card-use-evidence.json') -Raw|ConvertFrom-Json
$coverage=Get-Content (Join-Path $repo 'arena/coverage/coverage.json') -Raw|ConvertFrom-Json
if('Misleading Signpost' -notin $coverage.cards.verified -or $coverage.cards.mechanicallyUnlocked -ne 56 -or $coverage.cards.strategicallyVerified -ne 46 -or $coverage.cards.unlockedOnly.Count -ne 10 -or $coverage.inventory.decksWithZeroBlocked -ne 46 -or $coverage.cards.registryUnwired.Count -ne 0){throw 'Refresh coverage after evidence gates pass'}
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
    $entry=$zip.GetEntry('m/misleading_signpost.txt');$reader=[IO.StreamReader]::new($entry.Open())
    try{$packed=$reader.ReadToEnd().Replace("`r`n","`n").TrimEnd()}finally{$reader.Dispose()}
    $scriptPath=Join-Path $repo 'forge-gui/res/cardsfolder/m/misleading_signpost.txt'
    if($packed -cne (Get-Content $scriptPath -Raw).Replace("`r`n","`n").TrimEnd()){throw 'Original Signpost script differs'}
}finally{$zip.Dispose()}
$inputs=@(Get-ChildItem $PSScriptRoot -File|Where-Object Name -notin @('verification-summary.json','card-use-evidence.json')|ForEach-Object FullName)
$inputs+=@(Get-ChildItem (Join-Path $PSScriptRoot 'decks') -File|ForEach-Object FullName)
$inputs+=@('forge-ai/src/main/java/forge/ai/MisleadingSignpostAi.java','forge-ai/src/main/java/forge/ai/ability/PermanentNoncreatureAi.java','forge-ai/src/main/java/forge/ai/ability/ChangeCombatantsAi.java','forge-ai/src/main/java/forge/ai/ComputerUtil.java','forge-gui/res/ai/overrides/misleading-signpost.properties','arena/coverage/card-status.tsv','arena/coverage/COVERAGE.md','arena/coverage/coverage.json','arena/COMMANDER_COMPAT_WORKFLOW.md'|ForEach-Object {Join-Path $repo $_})
$inputs+=@($gatePath,$scriptPath,$checkLog,$gate.candidate,$unrelatedPath,(Join-Path $PSScriptRoot 'card-use-evidence.json'))
@{date='2026-10-18';revision=$head;card='Misleading Signpost';status='verified';engine=$gate.engine;
 nativeGate=@{candidate=$gate.candidate;candidateSha256=$gate.candidateSha256;results=$gate.results;total=3671};cardUse=$cardUse;
 coverage=@{inventory=$coverage.inventory;blocked=$coverage.blocked;unlocked=$coverage.cards.mechanicallyUnlocked;verified=$coverage.cards.strategicallyVerified;unlockedOnly=$coverage.cards.unlockedOnly};
 installedApplicationModified=$false;installedBaselineFiles=$installed;originalScriptMatchesInstalled=$true;unrelatedBaselineFiles=$unrelated;
 files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});
 scope='Opt-in native flash rescue, original optional attacker redirection and unchanged blue mana ability; native correctness and autonomous card use only, no strength/winner/completion claim'}|ConvertTo-Json -Depth 18|Set-Content (Join-Path $PSScriptRoot 'verification-summary.json')
Write-Output 'Misleading Signpost native/card-use verification and refreshed coverage PASS'
