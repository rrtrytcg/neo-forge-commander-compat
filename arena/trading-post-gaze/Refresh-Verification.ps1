$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$nativePath=Join-Path $PSScriptRoot 'target/repair-1.0.25/provenance.json'
$native=Get-Content $nativePath -Raw|ConvertFrom-Json
if($native.engine -ne 'NeoForge 1.0.25' -or $native.results.Count -ne 24 -or ($native.results|Measure-Object checks -Sum).Sum -ne 1860) { throw 'Native 1.0.25 gate incomplete' }
if(($native.results|Where-Object suite -eq 'TradingPostTests').checks -ne 60) { throw 'Trading Post native suite incomplete' }
if(($native.results|Where-Object suite -eq 'GazeOfGraniteTests').checks -ne 40) { throw 'Gaze of Granite native suite incomplete' }
foreach($result in $native.results) { if((Get-FileHash $result.log).Hash -ne $result.sha256) { throw "Native result log changed: $($result.suite)" } }
if((Get-FileHash $native.candidate).Hash -ne $native.candidateSha256) { throw 'Candidate changed' }
foreach($file in $native.files) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Native input changed: $($file.path)" } }
function Rows($dir) {
    return @(Get-Content (Join-Path $dir 'probe.log') | Where-Object { $_.StartsWith('CARD_WITNESS ') } | ForEach-Object { $_.Substring(13) | ConvertFrom-Json })
}
function NonlandMap($players) {
    $map=@{}
    foreach($p in $players) { foreach($c in $p.battlefield) { if($c.types -notmatch 'Land') { $map[[string]$c.id]=$c } } }
    return $map
}
# The runner's legacy text counter includes nested before.kind="cast" snapshots.
# Recount root events without replaying or altering the native games, preserving the
# original summaries and every recorded execution-input/log hash for audit.
foreach($file in Get-ChildItem (Join-Path $PSScriptRoot 'target/repair-card-use') -Filter summary.json -Recurse) {
    $summary=Get-Content $file.FullName -Raw|ConvertFrom-Json
    $rows=Rows $file.DirectoryName
    $casts=@($rows|Where-Object kind -eq 'cast').Count
    $resolved=@($rows|Where-Object kind -eq 'resolved').Count
    if($summary.castWitnesses -ne $casts -or $summary.resolvedWitnesses -ne $resolved) {
        $original=Join-Path $file.DirectoryName 'summary-before-root-event-recount.json'
        if(!(Test-Path $original)) { Copy-Item -LiteralPath $file.FullName -Destination $original }
        $summary.castWitnesses=$casts; $summary.resolvedWitnesses=$resolved
        $summary|Add-Member -Force -NotePropertyName eventCountCorrection -NotePropertyValue @{
            method='Count root event.kind; raw log and native execution unchanged';
            original=$original;sha256=(Get-FileHash $original).Hash
        }
        $summary|ConvertTo-Json -Depth 16|Set-Content $file.FullName
    }
}
$probeRuns=@()
foreach($run in @(@{seed=97021;label='trading-post';scenario='standard'},@{seed=97022;label='trading-post';scenario='standard'},@{seed=97041;label='trading-post-return';scenario='return'})) {
    $seed=$run.seed
    $dir=Join-Path $PSScriptRoot "target/repair-card-use/$($run.label)-on-$seed"; $path=Join-Path $dir 'summary.json'
    $s=Get-Content $path -Raw|ConvertFrom-Json
    if($s.engine -ne 'NeoForge 1.0.25' -or $s.nativeExit -ne 0 -or @($s.unexpectedDiagnostics).Count -ne 0) { throw "Trading Post probe invalid: $seed" }
    foreach($file in $s.files) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Probe input changed: $($file.path)" } }
    $rows=Rows $dir
    $casts=@($rows|Where-Object { $_.kind -eq 'cast' -and $_.api -eq 'PermanentNoncreature' }).Count
    $resolved=@($rows|Where-Object { $_.kind -eq 'resolved' }).Count
    $modes=@($rows|Where-Object { $_.kind -eq 'resolved' }|ForEach-Object api|Sort-Object -Unique)
    if($casts -lt 1 -or $resolved -lt 1 -or ($run.scenario -eq 'standard' -and ('Draw' -notin $modes -or 'Token' -notin $modes)) -or ($run.scenario -eq 'return' -and 'ChangeZone' -notin $modes)) { throw "Missing Trading Post card use: $seed" }
    $probeRuns+=@{card='Trading Post';scenario=$run.scenario;profile='on';seed=$seed;summary=$path;sha256=(Get-FileHash $path).Hash;casts=$casts;resolved=$resolved;modes=$modes;clockReached=$s.clockReached}
}
foreach($seed in @(97031,97032)) {
    $dir=Join-Path $PSScriptRoot "target/repair-card-use/gaze-of-granite-on-$seed"; $path=Join-Path $dir 'summary.json'
    $s=Get-Content $path -Raw|ConvertFrom-Json
    if($s.engine -ne 'NeoForge 1.0.25' -or $s.nativeExit -ne 0 -or @($s.unexpectedDiagnostics).Count -ne 0) { throw "Gaze probe invalid: $seed" }
    foreach($file in $s.files) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Probe input changed: $($file.path)" } }
    $rows=Rows $dir
    $casts=@($rows|Where-Object { $_.kind -eq 'cast' -and $_.api -eq 'DestroyAll' }).Count
    $resolved=@($rows|Where-Object { $_.kind -eq 'resolved' -and $_.api -eq 'DestroyAll' })
    $destroyed=0
    foreach($row in $resolved) {
        $x=[int]$row.x; $before=NonlandMap $row.before.players; $after=NonlandMap $row.players
        foreach($key in $before.Keys) { $c=$before[$key]; if($c.cmc -le $x -and $c.controller -ne 'Gaze of Granite-0' -and -not $after.ContainsKey($key)) { $destroyed++ } }
    }
    if($casts -lt 1 -or $resolved.Count -lt 1 -or $destroyed -lt 1) { throw "Missing Gaze card use: $seed" }
    $probeRuns+=@{card='Gaze of Granite';profile='on';seed=$seed;summary=$path;sha256=(Get-FileHash $path).Hash;casts=$casts;resolved=$resolved.Count;destroyed=$destroyed;clockReached=$s.clockReached}
}
foreach($pair in @(@('trading-post',97021),@('gaze-of-granite',97031))) {
    $dir=Join-Path $PSScriptRoot "target/repair-card-use/$($pair[0])-off-$($pair[1])"; $path=Join-Path $dir 'summary.json'
    $s=Get-Content $path -Raw|ConvertFrom-Json
    if($s.nativeExit -ne 0 -or @($s.unexpectedDiagnostics).Count -ne 0 -or (Rows $dir).Count -ne 0) { throw "Off-profile control used $($pair[0])" }
    $probeRuns+=@{card=$pair[0];profile='off';seed=$pair[1];summary=$path;sha256=(Get-FileHash $path).Hash;casts=0;resolved=0;clockReached=$s.clockReached}
}
$checkLog=Join-Path $PSScriptRoot 'target/repair-card-use-check.log'
& (Join-Path $PSScriptRoot 'Check-CardUseEvidence.ps1') *> $checkLog
$checkText=Get-Content $checkLog -Raw
if($checkText -notmatch 'DONE (\d+) Trading Post and Gaze of Granite card-use checks') { throw 'Card-use evidence checker did not complete' }
$cardUseChecks=[int]$Matches[1]
$coverage=Get-Content (Join-Path $repo 'arena/coverage/coverage.json') -Raw|ConvertFrom-Json
$head=(& git -C $repo rev-parse HEAD).Trim()
if(!$head.StartsWith($coverage.revision)) { throw 'Coverage was not generated from HEAD' }
if('Trading Post' -notin $coverage.cards.verified -or 'Gaze of Granite' -notin $coverage.cards.verified -or $coverage.cards.strategicallyVerified -ne 34 -or $coverage.cards.mechanicallyUnlocked -ne 44 -or $coverage.cards.unlockedOnly.Count -ne 10 -or $coverage.cards.registryUnwired.Count -ne 0 -or $coverage.inventory.decksWithZeroBlocked -ne 32) { throw 'Refresh coverage with both verified registry entries first' }
$version=Get-Content (Join-Path $repo 'arena/version-compat/verification-summary.json') -Raw|ConvertFrom-Json
$install=(Resolve-Path (Join-Path $repo '../NeoForge')).Path
$installed=@($version.nativeEvidence.files|Where-Object { $_.path.StartsWith($install,[StringComparison]::OrdinalIgnoreCase) })
foreach($file in $installed) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Installed input changed: $($file.path)" } }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $native.runtime 'res/cardsfolder/cardsfolder.zip'))
try {
    foreach($pair in @(@('t/trading_post.txt','t/trading_post.txt'),@('g/gaze_of_granite.txt','g/gaze_of_granite.txt'))) {
        $entry=$zip.GetEntry($pair[1]); if(!$entry) { throw "Installed original absent: $($pair[1])" }
        $reader=[IO.StreamReader]::new($entry.Open())
        try { $packed=$reader.ReadToEnd().Replace("`r`n","`n").TrimEnd() } finally { $reader.Dispose() }
        $source=(Get-Content (Join-Path $repo "forge-gui/res/cardsfolder/$($pair[0])") -Raw).Replace("`r`n","`n").TrimEnd()
        if($packed -cne $source) { throw "Installed script differs: $($pair[0])" }
    }
} finally { $zip.Dispose() }
$inputs=@(Get-ChildItem $PSScriptRoot -File|Where-Object Name -notin @('verification-summary.json','card-use-evidence.json')|ForEach-Object FullName)
$inputs+=@(Get-ChildItem (Join-Path $PSScriptRoot 'decks') -File|ForEach-Object FullName)
$inputs+=@('forge-ai/src/main/java/forge/ai/GazeOfGraniteAi.java','forge-ai/src/main/java/forge/ai/TradingPostAi.java','forge-ai/src/main/java/forge/ai/ability/DrawAi.java','forge-ai/src/main/java/forge/ai/ability/ChangeZoneAi.java','forge-ai/src/main/java/forge/ai/ability/TokenAi.java','forge-ai/src/main/java/forge/ai/ability/LifeGainAi.java','forge-ai/src/main/java/forge/ai/ability/DestroyAllAi.java','forge-ai/src/main/java/forge/ai/AiController.java','forge-ai/src/main/java/forge/ai/SpellAbilityAi.java','forge-gui/res/ai/overrides/trading-post.properties','forge-gui/res/ai/overrides/gaze-of-granite.properties','arena/COMMANDER_COMPAT_WORKFLOW.md','arena/coverage/card-status.tsv','arena/coverage/COVERAGE.md','arena/coverage/coverage.json'|ForEach-Object {Join-Path $repo $_})
$inputs+=@('target/repair-1.0.25/provenance.json','target/repair-card-use-check.log','target/repair-1.0.25/TradingPostTests.log','target/repair-1.0.25/GazeOfGraniteTests.log','target/repair-1.0.25/engine.jar'|ForEach-Object {Join-Path $PSScriptRoot $_})
$cardUse=@{engine=$native.engine;candidateSha256=$native.candidateSha256;runs=$probeRuns;checks=$cardUseChecks;checkLog=$checkLog;checkLogSha256=(Get-FileHash $checkLog).Hash}
$cardUse|ConvertTo-Json -Depth 10|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
@{date='2026-10-17';cards=@('Trading Post','Gaze of Granite');status='verified';engine=$native.engine;revision=$head;candidateSha256=$native.candidateSha256;
 nativeGate=@{results=$native.results;total=(($native.results|Measure-Object checks -Sum).Sum);candidate=$native.candidate;candidateSha256=$native.candidateSha256;runtime=$native.runtime};
 cardUse=$cardUse;
 coverage=@{inventory=$coverage.inventory;distinctRemaining=$coverage.blocked.distinctRemaining;occurrencesRemaining=$coverage.blocked.occurrencesRemaining;occurrencesUnlocked=$coverage.blocked.occurrencesUnlocked;mechanicallyUnlocked=$coverage.cards.mechanicallyUnlocked;strategicallyVerified=$coverage.cards.strategicallyVerified;unlockedOnly=$coverage.cards.unlockedOnly;revision=$coverage.revision};
 installedApplicationModified=$false;installedBaselineFiles=$installed;originalScriptsMatchInstalled=$true;
 files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});
 scope='Opt-in Trading Post targeting/draw safety and conservative Gaze wipe correctness and autonomous custom-deck card use; no game-completion, winner or strength claim'}|ConvertTo-Json -Depth 24|Set-Content (Join-Path $PSScriptRoot 'verification-summary.json')
Write-Output 'Verification and refreshed coverage PASS; installed originals and prior suites preserved'
