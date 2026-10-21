$ErrorActionPreference='Stop'
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw|ConvertFrom-Json
foreach($file in $gate.files) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Native evidence changed: $($file.path)" } }
if((Get-FileHash $gate.candidate).Hash -ne $gate.candidateSha256) { throw 'Native candidate changed' }
$runs=@(); $witnesses=@()
foreach($profileMode in @('on','off')) { foreach($seed in @(96451,96452)) {
    $summaryPath=Join-Path $PSScriptRoot "target/card-use/$profileMode-$seed/summary.json"
    $s=Get-Content -LiteralPath $summaryPath -Raw|ConvertFrom-Json
    if($s.nativeExit -ne 0 -or $s.unexpectedDiagnostics.Count) { throw 'Probe execution failed' }
    foreach($file in $s.files) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Probe evidence changed: $($file.path)" } }
    if(@($s.files|Where-Object {$_.path -like '*candidate.jar' -and $_.sha256 -eq $gate.candidateSha256}).Count -ne 1) { throw 'Probe did not use native-tested candidate' }
    $log=Join-Path (Split-Path $summaryPath) 'probe.log'; $lines=@(Get-Content -LiteralPath $log)
    $payments=@()
    for($i=0;$i -lt $lines.Count;$i++) {
        if($lines[$i] -match '^Add To Stack: AugurProbe-0 cast Corpse Augur') {
            $mana=@(); for($j=$i-1;$j -ge 0 -and $lines[$j] -match '^Mana: ';$j--) { $mana+=$lines[$j] }
            if($mana.Count -ne 4 -or @($mana|Where-Object {$_ -notmatch '^Mana: Swamp .*Add \{B\}'}).Count) { throw 'Native 3B payment missing' }
            $payments+=@{mana=$mana}
        }
    }
    $pending=@{}; $bodies=@{}; $casts=@(); $deaths=@(); $drawn=0
    foreach($row in $s.observations) {
        if($row.player -ne 'AugurProbe-0') { throw 'Unexpected actor' }
        $key=[string]$row.abilityId
        if($row.stage -eq 'stacked') {
            if($pending.ContainsKey($key)) { throw 'Duplicate stacked ability' }
            if($row.trigger) {
                # Native death triggers use a battlefield LKI host; its zone is not the live card's zone.
                # The original unmodified trigger and a preceding resolved body establish native death.
                if($row.spell -or !$bodies.ContainsKey([string]$row.sourceId)) { throw 'Death trigger lacks a resolved creature witness' }
                $legal=@($row.players|Where-Object legal)
                $safe=@($legal|Where-Object {$_.creaturesInGraveyard -le $row.life-5 -and $_.creaturesInGraveyard -lt $row.library})
                if($safe.Count) { $expected=($safe|Measure-Object creaturesInGraveyard -Maximum).Maximum }
                else { $expected=($legal|Measure-Object creaturesInGraveyard -Minimum).Minimum }
                $target=@($legal|Where-Object player -eq $row.target)
                if($target.Count -ne 1 -or $target[0].creaturesInGraveyard -ne $expected) { throw 'Actual trigger chose an illegal or policy-inconsistent graveyard' }
            } elseif(!$row.spell) { throw 'Expected a creature spell or death trigger' }
            $pending[$key]=@{row=$row;drawn=$drawn}; continue
        }
        $start=$pending[$key]; if($null -eq $start -or $row.fizzled -or $row.sourceId -ne $start.row.sourceId) { throw 'Unpaired or fizzled resolution' }
        $pending.Remove($key); $before=$start.row
        if($row.trigger) {
            if($row.target -ne $before.target) { throw 'Target changed without native retarget evidence' }
            $target=@($row.players|Where-Object player -eq $row.target)[0]; $x=$target.creaturesInGraveyard
            # Several simultaneous Augur deaths can stack before any of their draws resolve.
            $intervening=$drawn-$start.drawn
            if($row.life -ne $before.life-$intervening-$x -or $row.library -ne $before.library-$intervening-$x -or $row.hand -ne $before.hand+$intervening+$x) { throw 'Native draw and life-loss deltas do not match resolved X' }
            $drawn+=$x
            $deaths+=@{x=$x;before=$before;after=$row}
        } else {
            if(!$row.bodyOnBattlefield) { throw 'Creature did not reach battlefield' }
            $bodies[[string]$row.sourceId]=$true; $casts+=@{before=$before;after=$row}
        }
    }
    $pendingBodies=@($pending.Values|Where-Object {!$_.row.trigger}).Count
    if($payments.Count -ne $casts.Count+$pendingBodies) { throw 'Creature payments do not match observed cast events' }
    if($profileMode -eq 'off' -and ($s.observations.Count -or $payments.Count)) { throw 'Off control used Corpse Augur' }
    if($profileMode -eq 'on' -and (!$casts.Count -or !@($deaths|Where-Object x -gt 0).Count)) { throw 'Missing autonomous paid cast and nonzero death-trigger resolution' }
    if(!$s.clockReached -and $pending.Count) { throw 'Pending ability without a clock stop' }
    $runs+=@{profile=$profileMode;seed=$seed;casts=$casts.Count;deathTriggers=$deaths.Count;cardsDrawn=$drawn;payments=$payments;clockReached=$s.clockReached;
        pendingAtClockStop=$pending.Count;lateObserverEventsExcluded=$s.lateObserverEventsExcluded;diagnostics=$s.diagnostics;summary=$summaryPath;sha256=(Get-FileHash $summaryPath).Hash}
    $witnesses+=@{profile=$profileMode;seed=$seed;casts=$casts;deathTriggers=$deaths}
} }
$inputs=@($gate.files)+@('CorpseAugurProbe.java','Run-CardUseProbe.ps1','Check-CardUseEvidence.ps1'|ForEach-Object {
    $path=Join-Path $PSScriptRoot $_; @{path=$path;sha256=(Get-FileHash -LiteralPath $path).Hash}
})
@{date='2026-10-17';engine=$gate.engine;candidateSha256=$gate.candidateSha256;nativeResults=$gate.results;inputs=$inputs;runs=$runs;witnesses=$witnesses;
 scope='Bounded ordinary draw/life-loss policy and autonomous original-script use; no completion, winner or strength acceptance'}|ConvertTo-Json -Depth 18|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs|ForEach-Object {Write-Output "$($_.profile)/$($_.seed): $($_.casts) paid/resolved casts, $($_.deathTriggers) death triggers, $($_.cardsDrawn) cards drawn"}
Write-Output 'Card-use evidence PASS'
