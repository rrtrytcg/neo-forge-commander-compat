$ErrorActionPreference='Stop'
$runs=@(); $pairs=@()
foreach($mode in @('offense','defense')) {
    foreach($profileMode in @('on','off')) {
        foreach($seed in @(95881,95882)) {
            $dir=Join-Path $PSScriptRoot "target/card-use/$mode-$profileMode-$seed"
            $summaryPath=Join-Path $dir 'summary.json'
            $summary=Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
            if($summary.nativeExit -ne 0 -or $summary.unexpectedDiagnostics.Count) { throw "Unexpected probe failure: $dir" }
            foreach($file in $summary.files) {
                if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Changed probe input/evidence: $($file.path)" }
            }
            $logPath=Join-Path $dir 'probe.log'; $lines=@(Get-Content -LiteralPath $logPath)
            $rows=@(); $cutoff=$false
            foreach($line in $lines) {
                if($line -eq 'ENTITY_PROBE_CLOCK_STOP' -or $line.StartsWith('ENTITY_PROBE_DONE')) { $cutoff=$true }
                if(!$cutoff -and $line.StartsWith('ENTITY_WITNESS ')) { $rows+=($line.Substring(15) | ConvertFrom-Json) }
            }
            $pending=@{}; $localPairs=@(); $nativePayments=@()
            for($i=0;$i -lt $lines.Count;$i++) {
                if($lines[$i] -match '^Add To Stack: EntityProbe-0 activated Mirror Entity\s*$') {
                    $mana=0
                    for($j=$i-1;$j -ge 0 -and $lines[$j] -match '^Mana: Plains ';$j--) { $mana++ }
                    $nativePayments+=$mana
                }
            }
            $activations=@($rows | Where-Object stage -eq 'activated')
            if($profileMode -eq 'off' -and ($rows.Count -or $nativePayments.Count)) { throw "Default AI activated Entity: $dir" }
            foreach($row in $rows) {
                if($row.stage -eq 'activated') {
                    if($pending.Count -or $row.x -lt 3) { throw 'Redundant pending activation or X below three' }
                    $pending[$row.abilityId]=$row
                } else {
                    $before=$pending[$row.abilityId]
                    if($null -eq $before -or $row.fizzled -or $row.x -ne $before.x) { throw 'Missing/failed resolution witness' }
                    $pending.Remove($row.abilityId)
                    $timing=if($before.phase -eq 'COMBAT_BEGIN' -and $before.turnPlayer -eq $before.player) { 'offense' }
                        elseif($before.phase -eq 'COMBAT_DECLARE_BLOCKERS' -and $before.turnPlayer -ne $before.player) { 'defense' }
                        else { throw 'Activation outside approved combat window' }
                    $improved=0
                    foreach($card in $before.creatures) {
                        $after=@($row.creatures | Where-Object id -eq $card.id)
                        if($after.Count -ne 1 -or $after[0].power -lt $card.power -or $after[0].toughness -lt $card.toughness -or $after[0].toughness -le 0 -or !$after[0].elfAndGoblin) { throw 'Actual friendly stats/types violate safety rule' }
                        $participant=if($timing -eq 'offense') { $card.canAttack } else { $card.blocking }
                        if($participant -and $after[0].power -gt $card.power) { $improved++ }
                    }
                    if($improved -lt 2) { throw 'Fewer than two actual combat participants improved' }
                    $localPairs+=@{mode=$timing;seed=$seed;abilityId=$row.abilityId;turn=$row.turn;x=$row.x;improvedParticipants=$improved;before=$before;after=$row}
                }
            }
            if($profileMode -eq 'on') {
                if(@($localPairs | Where-Object mode -eq $mode).Count -lt 1) { throw "Missing intended combat-use witness: $dir" }
                if($nativePayments.Count -lt $activations.Count) { throw 'Native payment log missing activation' }
                for($i=0;$i -lt $activations.Count;$i++) {
                    if($nativePayments[$i] -ne $activations[$i].x) { throw 'Native Plains payment does not match chosen X' }
                }
            }
            $pairs+=$localPairs
            $runs+=@{mode=$mode;profile=$profileMode;seed=$seed;activations=$activations.Count;resolvedPairs=$localPairs.Count;
                offense=@($localPairs | Where-Object mode -eq 'offense').Count;defense=@($localPairs | Where-Object mode -eq 'defense').Count;
                chosenX=@($activations | ForEach-Object x);clockReached=$summary.clockReached;diagnostics=$summary.diagnostics;
                lateObserverEventsExcluded=($summary.observations.Count-$rows.Count);summary=$summaryPath;sha256=(Get-FileHash -LiteralPath $summaryPath).Hash}
        }
    }
}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw | ConvertFrom-Json
$inputs=@($gate.files)+@((Get-ChildItem -LiteralPath $PSScriptRoot -File | Where-Object { $_.Name -in @('MirrorEntityProbe.java','Run-CardUseProbe.ps1','Check-CardUseEvidence.ps1') }) | ForEach-Object { @{path=$_.FullName;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash} })
@{date='2026-10-17';scope='Native correctness and autonomous custom-deck combat use only; no completed-game, Commander-use or strength claim';nativeChecks=48;decreeRegressionChecks=69;observer='Read-only native event subscriber, no action/hand/phase/draw/payment forcing';acceptance=@{bothCombatModesObserved=$true;offControlsZero=$true;actualParticipantsImprove=$true;noFriendlyStatsReduced=$true;nativePaymentMatchesX=$true;nativeTypesApplied=$true;completedGameRequired=$false};inputs=$inputs;runs=$runs;resolvedWitnesses=$pairs} | ConvertTo-Json -Depth 16 | Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs | ForEach-Object { Write-Output "$($_.mode)/$($_.profile) seed $($_.seed): activations=$($_.activations), offense=$($_.offense), defense=$($_.defense)" }
Write-Output "Card-use evidence PASS: $($pairs.Count) native-paid resolved activations"
