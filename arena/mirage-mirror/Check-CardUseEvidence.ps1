$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label){if(!$ok){throw $label};$script:checks++;Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 38 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 4053) 'complete native gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'tested candidate hash'
foreach($f in $gate.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input $($f.path)"}
$initial=Get-Content (Join-Path $PSScriptRoot 'initial-probe-evidence.json') -Raw|ConvertFrom-Json
foreach($f in $initial.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "retained diagnostic $($f.path)"}
$runs=@();$offenseAttacks=0;$defenseBlocks=0;$allRestores=0
foreach($scenario in @('offense','defense')){foreach($profile in @('on','off')){foreach($seed in $(if($scenario -eq 'offense'){@(111011,111012)}else{@(111021,111022)})){
    $label="$scenario-$profile-$seed";$dir=Join-Path $PSScriptRoot "target/card-use-final/$label";$path=Join-Path $dir 'summary.json';$s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.engine -eq $gate.engine -and $s.candidateSha256 -eq $gate.candidateSha256 -and $s.scenario -eq $scenario -and $s.profile -eq $profile -and $s.seed -eq $seed) "$label requested engine and run"
    Check ($s.nativeExit -eq 0 -and !$s.unexpectedDiagnostics.Count) "$label native exit and diagnostics"
    foreach($f in $s.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label artifact $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $rows=@();$cutoff=$false
    foreach($line in Get-Content (Join-Path $dir 'probe.log')){
        if($line -eq 'MIRROR_PROBE_CLOCK_STOP' -or $line.StartsWith('MIRROR_PROBE_DONE')){$cutoff=$true}
        if($line.StartsWith('MIRROR_WITNESS ')){if($cutoff){throw "$label late observer event"};$rows+=($line.Substring(15)|ConvertFrom-Json)}
    }
    Check ($true) "$label no late observer events"
    Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -Pattern "Couldn't add to stack|AI failed to play" -Quiet)) "$label no native stack or payment failure"
    $casts=@($rows|Where-Object kind -eq 'cast');$resolved=@($rows|Where-Object kind -eq 'resolved')
    Check ($casts.Count -eq $resolved.Count) "$label no pending observed actions"
    $artifactCasts=0;$offense=0;$defense=0;$attacks=0;$blocks=0;$restores=0;$activations=@();$uses=@{}
    foreach($r in $resolved){
        $b=$r.before
        Check ($null -ne $b -and @($casts|Where-Object abilityId -eq $b.abilityId).Count -eq 1 -and $r.source.id -eq $b.source.id -and $r.abilityId -eq $b.abilityId) "$label unique original action pairing"
        Check (!$r.fizzled) "$label original action resolves without fizzle"
        if($b.source.original -ne 'Mirage Mirror'){continue}
        Check ($profile -eq 'on' -and $b.player -eq 'MirrorProbe-0' -and $b.stackSize -eq 1) "$label opted-in native action with previously empty stack"
        if($b.spell){
            Check ($b.phase -eq 'MAIN2' -and $b.turnPlayer -eq $b.player -and $b.mana.Count -eq 3) "$label native second-main three-mana artifact cast"
            Check ($r.source.name -eq 'Mirage Mirror' -and $r.source.artifact -and !$r.source.creature -and !$r.source.cloned -and $r.source.hasClone) "$label original artifact enters without copying";$artifactCasts++;continue
        }
        Check ($b.api -eq 'Clone' -and $b.mana.Count -eq 2 -and !$b.source.tapped -and $b.targets.Count -eq 1 -and $r.targets.Count -eq 1 -and $b.targets[0].id -eq $r.targets[0].id) "$label original two-mana targeted activation"
        $target=$b.targets[0]
        Check ($r.source.id -ne $target.id -and $r.source.stamp -eq $b.source.stamp -and $r.source.name -eq $target.name -and $r.source.creature -and $r.source.cloned -and !$r.source.hasClone) "$label actual copy with source identity preserved"
        Check ($r.source.power -eq $target.power -and $r.source.toughness -eq $target.toughness -and $r.source.flying -eq $target.flying -and $r.source.vigilance -eq $target.vigilance -and !$r.source.tapped) "$label native copied stats and combat keywords without target tapped state"
        $key="$($b.source.id):$($b.source.stamp):$($b.turn)";Check (!$uses.ContainsKey($key)) "$label one activation per source incarnation and native turn";$uses[$key]=$true
        if($b.phase -eq 'COMBAT_BEGIN'){
            Check ($b.turnPlayer -eq $b.player -and $r.source.power -ge 4 -and !$r.source.sick) "$label attack-ready offensive copy";$offense++
            $events=@($rows|Where-Object {$_.kind -eq 'attackers' -and $_.turn -eq $b.turn -and $_.event -gt $r.event -and @($_.combat.attackers|Where-Object {$_.id -eq $r.source.id -and $_.stamp -eq $r.source.stamp -and $_.cloned}).Count -eq 1})
            if($events.Count -gt 0){Check ($events[0].phase -eq 'COMBAT_DECLARE_ATTACKERS') "$label normal native attack declaration";$attacks++}
        }else{
            Check ($b.phase -eq 'COMBAT_DECLARE_ATTACKERS' -and $b.turnPlayer -ne $b.player -and @($b.combat.attackers|Where-Object defender -eq $b.player).Count -gt 0) "$label actual incoming attack prompts defensive copy";$defense++
            $events=@($rows|Where-Object {$_.kind -eq 'blockers' -and $_.turn -eq $b.turn -and $_.event -gt $r.event -and @($_.combat.blocks|Where-Object {$_.blocker.id -eq $r.source.id -and $_.blocker.stamp -eq $r.source.stamp -and $_.blocker.cloned}).Count -gt 0})
            if($events.Count -gt 0){Check ($events[0].phase -eq 'COMBAT_DECLARE_BLOCKERS') "$label normal native block declaration";$blocks++}
        }
        $restored=@($rows|Where-Object {$_.kind -eq 'phase' -and $_.turn -gt $r.turn -and $_.event -gt $r.event -and @($_.mirrors|Where-Object {$_.id -eq $r.source.id -and $_.stamp -eq $r.source.stamp -and $_.name -eq 'Mirage Mirror' -and $_.artifact -and !$_.creature -and !$_.cloned -and $_.hasClone}).Count -eq 1})
        if($restored.Count -gt 0){Check ($true) "$label original artifact and ability observed after native cleanup";$restores++}
        $activations+=$r
    }
    Check (@($resolved|Where-Object {$_.before.source.original -in $(if($scenario -eq 'offense'){@('Serra Angel','Isamaru, Hound of Konda')}else{@('Walking Corpse','Isamaru, Hound of Konda')})}).Count -gt 0) "$label ordinary native creature casting remains active"
    if($profile -eq 'off'){Check ($artifactCasts -eq 0 -and $activations.Count -eq 0) "$label original default admission and clone behavior retained"}
    if($scenario -eq 'offense'){$offenseAttacks+=$attacks}else{$defenseBlocks+=$blocks};$allRestores+=$restores
    $runs+=@{scenario=$scenario;profile=$profile;seed=$seed;artifactCasts=$artifactCasts;activations=$activations.Count;offensiveActivations=$offense;defensiveActivations=$defense;actualAttacks=$attacks;actualBlocks=$blocks;restorationWitnesses=$restores;clockReached=$s.clockReached;summary=$path;sha256=(Get-FileHash $path).Hash}
}}}
Check ($offenseAttacks -gt 0) 'offensive scenario autonomously activates and attacks with a copied Mirror'
Check ($defenseBlocks -gt 0) 'defensive scenario autonomously activates and blocks with a copied Mirror'
Check ($allRestores -gt 0) 'original artifact and activation observed after native end-of-turn restoration'
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$runs;initialDiagnostics=$initial;scope='Autonomous four-player Commander card use with intentionally invalid repeated-card decks; no completion, winner or strength claim'}|ConvertTo-Json -Depth 10|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
Write-Output "DONE $checks Mirage Mirror card-use checks"
