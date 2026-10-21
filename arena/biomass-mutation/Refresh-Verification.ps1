$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$native=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw|ConvertFrom-Json
$timeouts=Get-Content (Join-Path $PSScriptRoot 'target/final/timeout-summary.json') -Raw|ConvertFrom-Json
$probes=Get-Content (Join-Path $PSScriptRoot 'card-use-evidence.json') -Raw|ConvertFrom-Json
if(!$native.completeRegressionGate -or $native.results.Count -ne 20 -or ($native.results|Measure-Object checks -Sum).Sum -ne 1726) { throw 'Native gate incomplete' }
foreach($result in $native.results) { if((Get-FileHash $result.log).Hash -ne $result.sha256) { throw 'Native result log changed' } }
if($timeouts.integrationChecks -ne 9 -or $timeouts.upstream -notmatch 'Total tests run: 11, Passes: 11, Failures: 0, Skips: 0') { throw 'Deadline gate incomplete' }
if($timeouts.candidateSha256 -ne $native.candidateSha256 -or $probes.candidateSha256 -ne $native.candidateSha256 -or (Get-FileHash $native.candidate).Hash -ne $native.candidateSha256) { throw 'Candidate mismatch' }
foreach($f in @($native.files)+@($timeouts.files)+@($probes.inputs)) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Input changed: $($f.path)" } }
foreach($run in $probes.runs) {
    if((Get-FileHash $run.summary).Hash -ne $run.sha256) { throw 'Probe summary changed' }
    $s=Get-Content $run.summary -Raw|ConvertFrom-Json
    foreach($f in $s.files) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Probe input changed: $($f.path)" } }
}
if($probes.runs.Count -ne 8 -or @($probes.runs|Where-Object {$_.profile -eq 'on' -and $_.casts -lt 1}).Count -or @($probes.runs|Where-Object {$_.profile -eq 'off' -and $_.casts -ne 0}).Count) { throw 'Missing paired on/off use evidence' }
& (Join-Path $PSScriptRoot 'Check-SharedEvaluation.ps1') *> (Join-Path $PSScriptRoot 'target/shared-evaluation.log')
$baseline='8578241e2caf3db492b9379e02c03f7196566a19'
$apiPath='forge-ai/src/main/java/forge/ai/ability/AnimateAllAi.java'
$api=(Get-Content (Join-Path $repo $apiPath) -Raw).Replace("`r`n","`n")
$api=$api.Replace("import forge.ai.BiomassMutationAi;`n",'').Replace(' || BiomassMutationAi.uses(sa)','')
$hook=@'
        if (BiomassMutationAi.uses(sa)) {
            return BiomassMutationAi.consider(aiPlayer, sa);
        }
'@
$api=$api.Replace($hook.Replace("`r`n","`n").TrimEnd()+"`n",'')
$original=(& git -C $repo show "${baseline}:$apiPath") -join "`n"
if($LASTEXITCODE -ne 0 -or $api.TrimEnd() -cne $original.TrimEnd()) { throw 'AnimateAll differs from the verified baseline beyond the explicit opt-in hooks' }
$version=Get-Content (Join-Path $repo 'arena/version-compat/verification-summary.json') -Raw|ConvertFrom-Json
$install=(Resolve-Path (Join-Path $repo '../NeoForge')).Path
$installed=@($version.nativeEvidence.files|Where-Object {$_.path.StartsWith($install,[StringComparison]::OrdinalIgnoreCase)})
foreach($f in $installed) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Installed input changed: $($f.path)" } }
$unrelated=Get-Content (Join-Path $repo 'arena/corpse-augur/target/precommit-unrelated-hashes.json') -Raw|ConvertFrom-Json
foreach($f in $unrelated) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Unrelated file changed: $($f.path)" } }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $native.runtime 'res/cardsfolder/cardsfolder.zip'))
try {
    $entry=$zip.GetEntry('b/biomass_mutation.txt'); if(!$entry) { throw 'Installed original card absent' }
    $reader=[IO.StreamReader]::new($entry.Open())
    try { $packed=$reader.ReadToEnd().Replace("`r`n","`n").TrimEnd() } finally { $reader.Dispose() }
    $source=(Get-Content (Join-Path $repo 'forge-gui/res/cardsfolder/b/biomass_mutation.txt') -Raw).Replace("`r`n","`n").TrimEnd()
    if($packed -cne $source) { throw 'Installed script differs from source original' }
} finally { $zip.Dispose() }
$coverage=Get-Content (Join-Path $repo 'arena/coverage/coverage.json') -Raw|ConvertFrom-Json
if('Biomass Mutation' -notin $coverage.cards.verified -or 'PIP - Mutant Menace' -notin $coverage.inventory.zeroBlockedDecks -or $coverage.inventory.decksWithZeroBlocked -ne 31 -or $coverage.cards.mechanicallyUnlocked -ne 40 -or $coverage.cards.strategicallyVerified -ne 30 -or $coverage.cards.unlockedOnly.Count -ne 10 -or $coverage.cards.registryUnwired.Count) { throw 'Refresh coverage with the verified registry entry first' }
$inputs=@(Get-ChildItem $PSScriptRoot -File|Where-Object Name -ne 'verification-summary.json'|ForEach-Object FullName)
$inputs+=@(Get-ChildItem (Join-Path $PSScriptRoot 'decks') -File|ForEach-Object FullName)
$inputs+=@('forge-ai/src/main/java/forge/ai/CombatBaseStatsAi.java','forge-ai/src/main/java/forge/ai/MirrorEntityAi.java','forge-ai/src/main/java/forge/ai/BiomassMutationAi.java','forge-ai/src/main/java/forge/ai/ability/AnimateAllAi.java','forge-gui/res/ai/overrides/biomass-mutation.properties','arena/coverage/card-status.tsv','arena/coverage/COVERAGE.md','arena/coverage/coverage.json'|ForEach-Object {Join-Path $repo $_})
$inputs+=@('target/final/provenance.json','target/final/timeout-summary.json','target/shared-evaluation.log','target/coverage-audit.log'|ForEach-Object {Join-Path $PSScriptRoot $_})
@{date='2026-10-17';card='Biomass Mutation';status='verified';engine=$native.engine;revision=(& git -C $repo rev-parse HEAD).Trim();candidateSha256=$native.candidateSha256;
 nativeEvidence=$native;timeoutEvidence=$timeouts;cardUseEvidence=$probes;sharedMirrorAlgorithmPreserved=$true;animateAllUnchangedBeyondOptIn=$true;comparisonBaseline=$baseline;
 coverage=@{inventory=$coverage.inventory;distinctRemaining=$coverage.blocked.distinctRemaining;occurrencesRemaining=$coverage.blocked.occurrencesRemaining;occurrencesUnlocked=$coverage.blocked.occurrencesUnlocked;mechanicallyUnlocked=$coverage.cards.mechanicallyUnlocked;strategicallyVerified=$coverage.cards.strategicallyVerified;unlockedOnly=$coverage.cards.unlockedOnly};
 installedApplicationModified=$false;installedBaselineFiles=$installed;originalScriptMatchesInstalled=$true;unrelatedFilesUnchanged=$unrelated;
 files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});
 scope='Opt-in bounded combat correctness and autonomous custom-deck card use; no game-completion, winner or strength claim'}|ConvertTo-Json -Depth 24|Set-Content (Join-Path $PSScriptRoot 'verification-summary.json')
Write-Output 'Verification and refreshed coverage PASS; shared Mirror policy, installed originals and unrelated files preserved'
