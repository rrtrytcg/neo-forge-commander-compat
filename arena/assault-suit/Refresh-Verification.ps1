$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$nativePath=Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json'
$native=Get-Content $nativePath -Raw|ConvertFrom-Json
if($native.engine -ne 'NeoForge 1.0.25' -or $native.results.Count -ne 21 -or ($native.results|Measure-Object checks -Sum).Sum -ne 1747) { throw 'Native 1.0.25 gate incomplete' }
if(($native.results|Where-Object suite -eq 'AssaultSuitTests').checks -ne 21) { throw 'Assault Suit native suite incomplete' }
foreach($result in $native.results) { if((Get-FileHash $result.log).Hash -ne $result.sha256) { throw "Native result log changed: $($result.suite)" } }
if((Get-FileHash $native.candidate).Hash -ne $native.candidateSha256) { throw 'Candidate changed' }
foreach($file in $native.files) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Native input changed: $($file.path)" } }
$regPath=Join-Path $PSScriptRoot 'target/regressions/summary.json'
$reg=Get-Content $regPath -Raw|ConvertFrom-Json
if($reg.total -ne 671 -or $reg.results.Count -ne 9) { throw 'Shared-hook regression gate incomplete' }
foreach($result in $reg.results) { if((Get-FileHash $result.log).Hash -ne $result.sha256) { throw "Regression log changed: $($result.suite)" } }
$probeRuns=@()
foreach($profile in @('on','off')) {
    foreach($seed in @(96801,96802)) {
        $path=Join-Path $PSScriptRoot "target/card-use/$profile-$seed/summary.json"
        $s=Get-Content $path -Raw|ConvertFrom-Json
        if($s.engine -ne 'NeoForge 1.0.25' -or $s.nativeExit -ne 0 -or @($s.unexpectedDiagnostics).Count -ne 0) { throw "Probe invalid: $profile/$seed" }
        foreach($file in $s.files) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Probe input changed: $($file.path)" } }
        $rows=@($s.observations)
        $casts=@($rows|Where-Object kind -eq 'cast').Count
        $equips=@($rows|Where-Object kind -eq 'equip').Count
        $attachments=@($rows|Where-Object kind -eq 'attached').Count
        $accepted=@($rows|Where-Object { $_.kind -eq 'resolved' -and $_.source -eq 'trigger' -and $_.controllerBefore -ne $_.equippedController }).Count
        $declined=@($rows|Where-Object { $_.kind -eq 'trigger' -and ($_.availableMana -lt 3 -or $_.equippedId -lt 0) }).Count
        if($profile -eq 'on') {
            if($casts -lt 1 -or $equips -lt 1 -or $attachments -lt 1 -or $accepted -lt 1 -or $declined -lt 1) { throw "Missing on-profile card use: $seed" }
        } else {
            if(@($rows).Count -ne 0) { throw "Off-profile control used the card: $seed" }
        }
        $probeRuns+=@{profile=$profile;seed=$seed;summary=$path;sha256=(Get-FileHash $path).Hash;casts=$casts;equips=$equips;attachments=$attachments;accepted=$accepted;declined=$declined;clockReached=$s.clockReached}
    }
}
$checkLog=Join-Path $PSScriptRoot 'target/card-use-check.log'
& (Join-Path $PSScriptRoot 'Check-CardUseEvidence.ps1') *> $checkLog
$checkText=Get-Content $checkLog -Raw
if($checkText -notmatch 'DONE 234 Assault Suit card-use checks \(accepted=26 declined=56\)') { throw 'Card-use evidence checker did not complete' }
$coverage=Get-Content (Join-Path $repo 'arena/coverage/coverage.json') -Raw|ConvertFrom-Json
$head=(& git -C $repo rev-parse HEAD).Trim()
if(!$head.StartsWith($coverage.revision)) { throw 'Coverage was not generated from HEAD' }
if('Assault Suit' -notin $coverage.cards.verified -or $coverage.cards.strategicallyVerified -ne 31 -or $coverage.cards.mechanicallyUnlocked -ne 41 -or $coverage.cards.unlockedOnly.Count -ne 10 -or $coverage.cards.registryUnwired.Count -ne 0 -or $coverage.inventory.decksWithZeroBlocked -ne 31) { throw 'Refresh coverage with the verified registry entry first' }
$version=Get-Content (Join-Path $repo 'arena/version-compat/verification-summary.json') -Raw|ConvertFrom-Json
$install=(Resolve-Path (Join-Path $repo '../NeoForge')).Path
$installed=@($version.nativeEvidence.files|Where-Object { $_.path.StartsWith($install,[StringComparison]::OrdinalIgnoreCase) })
foreach($file in $installed) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Installed input changed: $($file.path)" } }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $native.runtime 'res/cardsfolder/cardsfolder.zip'))
try {
    $entry=$zip.GetEntry('a/assault_suit.txt'); if(!$entry) { throw 'Installed original card absent' }
    $reader=[IO.StreamReader]::new($entry.Open())
    try { $packed=$reader.ReadToEnd().Replace("`r`n","`n").TrimEnd() } finally { $reader.Dispose() }
    $source=(Get-Content (Join-Path $repo 'forge-gui/res/cardsfolder/a/assault_suit.txt') -Raw).Replace("`r`n","`n").TrimEnd()
    if($packed -cne $source) { throw 'Installed script differs from source original' }
} finally { $zip.Dispose() }
$inputs=@(Get-ChildItem $PSScriptRoot -File|Where-Object Name -notin @('verification-summary.json','card-use-evidence.json')|ForEach-Object FullName)
$inputs+=@(Get-ChildItem (Join-Path $PSScriptRoot 'decks') -File|ForEach-Object FullName)
$inputs+=@('forge-ai/src/main/java/forge/ai/AssaultSuitAi.java','forge-ai/src/main/java/forge/ai/ability/AttachAi.java','forge-ai/src/main/java/forge/ai/ability/ControlGainAi.java','forge-gui/res/ai/overrides/assault-suit.properties','arena/coverage/card-status.tsv','arena/coverage/COVERAGE.md','arena/coverage/coverage.json'|ForEach-Object {Join-Path $repo $_})
$inputs+=@('target/final-1.0.25/provenance.json','target/regressions/summary.json','target/card-use-check.log','target/final-1.0.25/AssaultSuitTests.log','target/final-1.0.25/engine.jar'|ForEach-Object {Join-Path $PSScriptRoot $_})
$cardUse=@{engine=$native.engine;candidateSha256=$native.candidateSha256;runs=$probeRuns;checks=234;checkLog=$checkLog;checkLogSha256=(Get-FileHash $checkLog).Hash}
$cardUse|ConvertTo-Json -Depth 10|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
@{date='2026-10-17';card='Assault Suit';status='verified';engine=$native.engine;revision=$head;candidateSha256=$native.candidateSha256;
 nativeGate=@{results=$native.results;total=(($native.results|Measure-Object checks -Sum).Sum);candidate=$native.candidate;candidateSha256=$native.candidateSha256;runtime=$native.runtime};
 sharedHookRegressions=@{results=$reg.results;total=$reg.total};
 cardUse=$cardUse;
 coverage=@{inventory=$coverage.inventory;distinctRemaining=$coverage.blocked.distinctRemaining;occurrencesRemaining=$coverage.blocked.occurrencesRemaining;occurrencesUnlocked=$coverage.blocked.occurrencesUnlocked;mechanicallyUnlocked=$coverage.cards.mechanicallyUnlocked;strategicallyVerified=$coverage.cards.strategicallyVerified;unlockedOnly=$coverage.cards.unlockedOnly;revision=$coverage.revision};
 installedApplicationModified=$false;installedBaselineFiles=$installed;originalScriptMatchesInstalled=$true;
 files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});
 scope='Opt-in bounded equipment correctness and autonomous custom-deck card use; no game-completion, winner or strength claim'}|ConvertTo-Json -Depth 24|Set-Content (Join-Path $PSScriptRoot 'verification-summary.json')
Write-Output 'Verification and refreshed coverage PASS; installed originals and prior suites preserved'
