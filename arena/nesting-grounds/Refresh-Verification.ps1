$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$native=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw|ConvertFrom-Json
$timeouts=Get-Content (Join-Path $PSScriptRoot 'target/final/timeout-summary.json') -Raw|ConvertFrom-Json
$probes=Get-Content (Join-Path $PSScriptRoot 'card-use-evidence.json') -Raw|ConvertFrom-Json
if(!$native.completeRegressionGate -or $native.results.Count -ne 19 -or ($native.results|Measure-Object checks -Sum).Sum -ne 1610) { throw 'Native gate incomplete' }
if($timeouts.integrationChecks -ne 9 -or $timeouts.upstream -notmatch 'Total tests run: 11, Passes: 11, Failures: 0, Skips: 0') { throw 'Deadline gate incomplete' }
if($timeouts.candidateSha256 -ne $native.candidateSha256 -or $probes.candidateSha256 -ne $native.candidateSha256 -or (Get-FileHash $native.candidate).Hash -ne $native.candidateSha256) { throw 'Candidate mismatch' }
foreach($f in @($native.files)+@($timeouts.files)+@($probes.inputs)) {
    if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Input changed: $($f.path)" }
}
foreach($run in $probes.runs) {
    if((Get-FileHash $run.summary).Hash -ne $run.sha256) { throw 'Probe summary changed' }
    $s=Get-Content $run.summary -Raw|ConvertFrom-Json
    foreach($f in $s.files) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Probe input changed: $($f.path)" } }
}
if($probes.runs.Count -ne 8 -or @($probes.runs|Where-Object {$_.profile -eq 'on' -and $_.activations -lt 1}).Count -or @($probes.runs|Where-Object {$_.profile -eq 'off' -and $_.activations -ne 0}).Count) { throw 'Missing paired on/off evidence' }
$version=Get-Content (Join-Path $repo 'arena/version-compat/verification-summary.json') -Raw|ConvertFrom-Json
$install=(Resolve-Path (Join-Path $repo '../NeoForge')).Path
$installed=@($version.nativeEvidence.files|Where-Object {$_.path.StartsWith($install,[StringComparison]::OrdinalIgnoreCase)})
foreach($f in $installed) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Installed input changed: $($f.path)" } }
$unrelated=Get-Content (Join-Path $repo 'arena/corpse-augur/target/precommit-unrelated-hashes.json') -Raw|ConvertFrom-Json
foreach($f in $unrelated) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Unrelated file changed: $($f.path)" } }
$upstreamRef='64569999b8b99dd7176ab9d3e1e6008f3ab68590'
$hooks=@{
    'forge-ai/src/main/java/forge/ai/ability/PumpAi.java'=@(@'
        if (NestingGroundsAi.uses(sa)) {
            return NestingGroundsAi.consider(ai, sa);
        }
'@)
    'forge-ai/src/main/java/forge/ai/ability/CountersMoveAi.java'=@(@'
        if (NestingGroundsAi.uses(sa)) {
            return NestingGroundsAi.validate(ai, sa);
        }
'@,@'
        if (NestingGroundsAi.uses(sa) && params.get("Source") instanceof Card && params.get("Target") instanceof Card) {
            final CounterType chosen = NestingGroundsAi.type(sa.getActivatingPlayer(), sa.getRootAbility(),
                    (Card) params.get("Source"), (Card) params.get("Target"), options);
            if (chosen != null) { return chosen; }
            // Resolution is mandatory. If a response removed the useful type, preserve the
            // engine's legal fallback rather than returning null or inventing cancellation.
        }
'@)
}
foreach($path in $hooks.Keys) {
    $original=(Get-Content (Join-Path $repo $path) -Raw).Replace("`r`n","`n")
    foreach($hook in $hooks[$path]) {
        $snippet=$hook.Replace("`r`n","`n").TrimEnd()+"`n"
        if(!$original.Contains($snippet)) { throw "Expected opt-in hook missing: $path" }
        $original=$original.Replace($snippet,'')
    }
    $official=(& git -C $repo show "${upstreamRef}:$path") -join "`n"
    if($LASTEXITCODE -ne 0 -or $original.TrimEnd() -cne $official.TrimEnd()) { throw "Shared class differs from pinned 1.0.25 source beyond the opt-in hooks: $path" }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $native.runtime 'res/cardsfolder/cardsfolder.zip'))
try {
    $entry=$zip.GetEntry('n/nesting_grounds.txt'); if(!$entry) { throw 'Installed original card absent' }
    $reader=[IO.StreamReader]::new($entry.Open())
    try { $packed=$reader.ReadToEnd().Replace("`r`n","`n").TrimEnd() } finally { $reader.Dispose() }
    $source=(Get-Content (Join-Path $repo 'forge-gui/res/cardsfolder/n/nesting_grounds.txt') -Raw).Replace("`r`n","`n").TrimEnd()
    if($packed -ne $source) { throw 'Installed script differs from source original' }
} finally { $zip.Dispose() }
$coverage=Get-Content (Join-Path $repo 'arena/coverage/coverage.json') -Raw|ConvertFrom-Json
if('Nesting Grounds' -notin $coverage.cards.verified -or 'FIC - Counter Blitz' -notin $coverage.inventory.zeroBlockedDecks -or $coverage.inventory.decksWithZeroBlocked -ne 30 -or $coverage.cards.mechanicallyUnlocked -ne 39 -or $coverage.cards.strategicallyVerified -ne 29 -or $coverage.cards.unlockedOnly.Count -ne 10 -or $coverage.cards.registryUnwired.Count) { throw 'Refresh coverage with verified registry entry first' }
$inputs=@(Get-ChildItem $PSScriptRoot -File|Where-Object Name -ne 'verification-summary.json'|ForEach-Object FullName)
$inputs+=@(Get-ChildItem (Join-Path $PSScriptRoot 'decks') -File|ForEach-Object FullName)
$inputs+=@('forge-ai/src/main/java/forge/ai/NestingGroundsAi.java','forge-ai/src/main/java/forge/ai/ability/PumpAi.java','forge-ai/src/main/java/forge/ai/ability/CountersMoveAi.java','forge-gui/res/ai/overrides/nesting-grounds.properties','arena/coverage/card-status.tsv','arena/coverage/COVERAGE.md','arena/coverage/coverage.json'|ForEach-Object {Join-Path $repo $_})
$inputs+=@(Join-Path $PSScriptRoot 'target/final/provenance.json'); $inputs+=@(Join-Path $PSScriptRoot 'target/final/timeout-summary.json'); $inputs+=@(Join-Path $PSScriptRoot 'target/coverage-audit.log')
@{date='2026-10-17';card='Nesting Grounds';status='verified';engine=$native.engine;revision=(& git -C $repo rev-parse HEAD).Trim();
 candidateSha256=$native.candidateSha256;nativeEvidence=$native;timeoutEvidence=$timeouts;cardUseEvidence=$probes;
 coverage=@{inventory=$coverage.inventory;distinctRemaining=$coverage.blocked.distinctRemaining;occurrencesRemaining=$coverage.blocked.occurrencesRemaining;occurrencesUnlocked=$coverage.blocked.occurrencesUnlocked;mechanicallyUnlocked=$coverage.cards.mechanicallyUnlocked;strategicallyVerified=$coverage.cards.strategicallyVerified;unlockedOnly=$coverage.cards.unlockedOnly};
 installedApplicationModified=$false;installedBaselineFiles=$installed;originalScriptMatchesInstalled=$true;unrelatedFilesUnchanged=$unrelated;
 sharedHooksMatchPinnedUpstreamAfterRemovingOptIn=@{upstreamRef=$upstreamRef;paths=@($hooks.Keys);match=$true};
 files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});
 scope='Opt-in bounded native correctness and autonomous custom-deck card use; no game-completion, winner or strength claim'}|
 ConvertTo-Json -Depth 24|Set-Content (Join-Path $PSScriptRoot 'verification-summary.json')
Write-Output 'Verification and refreshed coverage PASS; installed originals and unrelated files unchanged'
