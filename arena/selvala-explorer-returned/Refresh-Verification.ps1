$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$nativePath=Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json'
$native=Get-Content $nativePath -Raw|ConvertFrom-Json
if($native.engine -ne 'NeoForge 1.0.25' -or $native.results.Count -ne 22 -or ($native.results|Measure-Object checks -Sum).Sum -ne 1760) { throw 'Native 1.0.25 gate incomplete' }
if(($native.results|Where-Object suite -eq 'SelvalaTests').checks -ne 13) { throw 'Selvala native suite incomplete' }
foreach($result in $native.results) { if((Get-FileHash $result.log).Hash -ne $result.sha256) { throw "Native result log changed: $($result.suite)" } }
if((Get-FileHash $native.candidate).Hash -ne $native.candidateSha256) { throw 'Candidate changed' }
foreach($file in $native.files) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Native input changed: $($file.path)" } }
$probeRuns=@()
foreach($profile in @('on','off')) {
    foreach($seed in @(96901,96902)) {
        $dir=Join-Path $PSScriptRoot "target/card-use/$profile-$seed"
        $path=Join-Path $dir 'summary.json'
        $s=Get-Content $path -Raw|ConvertFrom-Json
        if($s.engine -ne 'NeoForge 1.0.25' -or $s.nativeExit -ne 0 -or @($s.unexpectedDiagnostics).Count -ne 0) { throw "Probe invalid: $profile/$seed" }
        foreach($file in $s.files) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Probe input changed: $($file.path)" } }
        $rows=@(Get-Content (Join-Path $dir 'probe.log') | Where-Object { $_.StartsWith('SELVALA_WITNESS ') } | ForEach-Object { $_.Substring(16) | ConvertFrom-Json })
        $casts=@($rows|Where-Object kind -eq 'cast').Count
        $parleys=@($rows|Where-Object kind -eq 'parley').Count
        $resolved=@($rows|Where-Object { $_.kind -eq 'resolved' -and $_.source -eq 'parley' }).Count
        if($profile -eq 'on') {
            if($casts -lt 1 -or $resolved -lt 1) { throw "Missing on-profile card use: $seed" }
        } else {
            if($rows.Count -ne 0) { throw "Off-profile control used the card: $seed" }
        }
        $probeRuns+=@{profile=$profile;seed=$seed;summary=$path;sha256=(Get-FileHash $path).Hash;casts=$casts;parleys=$parleys;resolvedParleys=$resolved;clockReached=$s.clockReached}
    }
}
$checkLog=Join-Path $PSScriptRoot 'target/card-use-check.log'
& (Join-Path $PSScriptRoot 'Check-CardUseEvidence.ps1') *> $checkLog
$checkText=Get-Content $checkLog -Raw
if($checkText -notmatch 'DONE 155 Selvala card-use checks \(resolvedParleys=23\)') { throw 'Card-use evidence checker did not complete' }
$coverage=Get-Content (Join-Path $repo 'arena/coverage/coverage.json') -Raw|ConvertFrom-Json
$head=(& git -C $repo rev-parse HEAD).Trim()
if(!$head.StartsWith($coverage.revision)) { throw 'Coverage was not generated from HEAD' }
if('Selvala, Explorer Returned' -notin $coverage.cards.verified -or $coverage.cards.strategicallyVerified -ne 32 -or $coverage.cards.mechanicallyUnlocked -ne 42 -or $coverage.cards.unlockedOnly.Count -ne 10 -or $coverage.cards.registryUnwired.Count -ne 0 -or $coverage.inventory.decksWithZeroBlocked -ne 31) { throw 'Refresh coverage with the verified registry entry first' }
$version=Get-Content (Join-Path $repo 'arena/version-compat/verification-summary.json') -Raw|ConvertFrom-Json
$install=(Resolve-Path (Join-Path $repo '../NeoForge')).Path
$installed=@($version.nativeEvidence.files|Where-Object { $_.path.StartsWith($install,[StringComparison]::OrdinalIgnoreCase) })
foreach($file in $installed) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Installed input changed: $($file.path)" } }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $native.runtime 'res/cardsfolder/cardsfolder.zip'))
try {
    $entry=$zip.GetEntry('s/selvala_explorer_returned.txt'); if(!$entry) { throw 'Installed original card absent' }
    $reader=[IO.StreamReader]::new($entry.Open())
    try { $packed=$reader.ReadToEnd().Replace("`r`n","`n").TrimEnd() } finally { $reader.Dispose() }
    $source=(Get-Content (Join-Path $repo 'forge-gui/res/cardsfolder/s/selvala_explorer_returned.txt') -Raw).Replace("`r`n","`n").TrimEnd()
    if($packed -cne $source) { throw 'Installed script differs from source original' }
} finally { $zip.Dispose() }
$inputs=@(Get-ChildItem $PSScriptRoot -File|Where-Object Name -notin @('verification-summary.json','card-use-evidence.json')|ForEach-Object FullName)
$inputs+=@(Get-ChildItem (Join-Path $PSScriptRoot 'decks') -File|ForEach-Object FullName)
$inputs+=@('forge-ai/src/main/java/forge/ai/AiOverrideProfile.java','forge-gui/res/ai/overrides/selvala-explorer-returned.properties','arena/Audit-Deck.ps1','arena/coverage/card-status.tsv','arena/coverage/COVERAGE.md','arena/coverage/coverage.json'|ForEach-Object {Join-Path $repo $_})
$inputs+=@('target/final-1.0.25/provenance.json','target/card-use-check.log','target/final-1.0.25/SelvalaTests.log','target/final-1.0.25/engine.jar'|ForEach-Object {Join-Path $PSScriptRoot $_})
$cardUse=@{engine=$native.engine;candidateSha256=$native.candidateSha256;runs=$probeRuns;checks=155;checkLog=$checkLog;checkLogSha256=(Get-FileHash $checkLog).Hash}
$cardUse|ConvertTo-Json -Depth 10|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
@{date='2026-10-17';card='Selvala, Explorer Returned';status='verified';engine=$native.engine;revision=$head;candidateSha256=$native.candidateSha256;
 nativeGate=@{results=$native.results;total=(($native.results|Measure-Object checks -Sum).Sum);candidate=$native.candidate;candidateSha256=$native.candidateSha256;runtime=$native.runtime};
 cardUse=$cardUse;
 coverage=@{inventory=$coverage.inventory;distinctRemaining=$coverage.blocked.distinctRemaining;occurrencesRemaining=$coverage.blocked.occurrencesRemaining;occurrencesUnlocked=$coverage.blocked.occurrencesUnlocked;mechanicallyUnlocked=$coverage.cards.mechanicallyUnlocked;strategicallyVerified=$coverage.cards.strategicallyVerified;unlockedOnly=$coverage.cards.unlockedOnly;revision=$coverage.revision};
 installedApplicationModified=$false;installedBaselineFiles=$installed;originalScriptMatchesInstalled=$true;
 files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});
 scope='Opt-in bounded admission and autonomous custom-deck card use; no game-completion, winner or strength claim'}|ConvertTo-Json -Depth 24|Set-Content (Join-Path $PSScriptRoot 'verification-summary.json')
Write-Output 'Verification and refreshed coverage PASS; installed originals and prior suites preserved'
