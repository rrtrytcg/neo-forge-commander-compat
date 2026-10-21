$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label) {if(!$ok){throw $label};$script:checks++;Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 28 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 2209) 'complete native gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'candidate hash'
foreach($f in $gate.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input hash $($f.path)"}
$results=@()
$runs=@(@{scenario='removal';mode='on';seed=101011},@{scenario='removal';mode='on';seed=101012},@{scenario='combat';mode='on';seed=101021},@{scenario='removal';mode='off';seed=101011},@{scenario='combat';mode='off';seed=101021})
foreach($run in $runs) {
    $label="$($run.scenario)-$($run.mode)-$($run.seed)";$dir=Join-Path $PSScriptRoot "target/card-use/$label";$path=Join-Path $dir 'summary.json'
    $s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.nativeExit -eq 0 -and @($s.unexpectedDiagnostics).Count -eq 0) "$label native exit and diagnostics"
    Check ($s.candidateSha256 -eq $gate.candidateSha256 -and $s.engine -eq $gate.engine) "$label verified engine"
    Check ($s.seed -eq $run.seed -and $s.scenario -eq $run.scenario -and $s.profile -eq $run.mode) "$label requested run"
    foreach($f in $s.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label input hash $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $rows=@(Get-Content (Join-Path $dir 'probe.log')|Where-Object {$_.StartsWith('CARD_WITNESS ')}|ForEach-Object {$_.Substring(13)|ConvertFrom-Json})
    $casts=@($rows|Where-Object {$_.kind -eq 'cast' -and $_.spell})
    $resolved=@($rows|Where-Object {$_.kind -eq 'resolved' -and $_.api -eq 'Scry'})
    $removal=0;$combat=0;$attacking=0;$self=0;$top=0;$bottom=0
    if($run.mode -eq 'off'){Check ($rows.Count -eq 0) "$label original prohibition retained"}
    else {
        Check ($casts.Count -gt 0 -and $resolved.Count -gt 0) "$label native creature cast and resolved activation"
        Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch "Couldn't add to stack" -Quiet)) "$label no failed stack insertion"
        foreach($r in $resolved) {
            $b=$r.before
            Check ($b -ne $null -and !$b.spell -and !$r.fizzled) "$label matched native activation"
            Check (@($b.sacrificed).Count -eq 1) "$label exactly one paid sacrifice"
            $sac=$b.sacrificed[0];$before=@($b.players|Where-Object player -eq 'Viscera Seer-0')[0];$after=@($r.players|Where-Object player -eq 'Viscera Seer-0')[0]
            Check ($sac.name -in $before.graveyard -and $sac.id -notin @($before.battlefield.id)) "$label chosen creature actually left battlefield for native sacrifice"
            Check ($before.librarySize -gt 0 -and $after.librarySize -eq $before.librarySize) "$label nonempty library unchanged by scry"
            Check ($after.handSize -eq $before.handSize -and $after.life -eq $before.life) "$label scry does not draw or lose life"
            $event=@($rows|Where-Object {$_.kind -eq 'scry' -and $_.witnessId -eq $b.witnessId})
            Check ($event.Count -eq 1 -and $event[0].player -eq 'Viscera Seer-0' -and ($event[0].toTop+$event[0].toBottom) -eq 1) "$label original native scry-one event"
            if($event[0].toTop -eq 1){Check ($after.topId -eq $before.topId) "$label native keep-top ordering";$top++}
            else {Check ($after.bottomId -eq $before.topId) "$label native put-bottom ordering";$bottom++}
            $hit=@($b.threats|Where-Object {$_.api -eq 'Destroy' -and $sac.id -in $_.targets})
            $blocked=$b.phase -eq 'COMBAT_DECLARE_BLOCKERS' -and $sac.id -in $b.declaredBlockers
            $attacked=$b.phase -eq 'COMBAT_DECLARE_BLOCKERS' -and $sac.id -in $b.declaredAttackers
            Check ($hit.Count -gt 0 -or $blocked -or $attacked) "$label public removal target or already declared combatant"
            if($hit.Count){$removal++};if($blocked){$combat++};if($attacked){$attacking++};if($sac.name -eq 'Viscera Seer'){$self++}
        }
        if($run.scenario -eq 'removal'){Check ($removal -gt 0) "$label removal-response witness"}
        if($run.scenario -eq 'combat'){Check ($combat -gt 0) "$label declared-blocker witness"}
    }
    $results+=@{scenario=$run.scenario;profile=$run.mode;seed=$run.seed;casts=$casts.Count;resolved=$resolved.Count;removalResponses=$removal;combatRescues=$combat;attackingRescues=$attacking;selfSacrifices=$self;keptTop=$top;putBottom=$bottom;clockReached=$s.clockReached;summary=$path;sha256=(Get-FileHash $path).Hash}
}
Check (($results|Measure-Object selfSacrifices -Sum).Sum -gt 0) 'autonomous Seer self-sacrifice witness'
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$results;scope='Autonomous four-player custom-deck card use only; no completed-game, winner or strength claim'}|ConvertTo-Json -Depth 12|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
Write-Output "DONE $checks Viscera Seer card-use checks"
