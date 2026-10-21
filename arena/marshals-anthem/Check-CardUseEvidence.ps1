$ErrorActionPreference='Stop'
$runs=@(); $witnesses=@()
foreach($mode in @('kicked','unkicked')) { foreach($profileMode in @('on','off')) { foreach($seed in @(96231,96232)) {
    $dir=Join-Path $PSScriptRoot "target/card-use/$mode-$profileMode-$seed"
    $summaryPath=Join-Path $dir 'summary.json'
    $summary=Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
    if($summary.nativeExit -ne 0 -or $summary.unexpectedDiagnostics.Count) { throw "Probe failure: $dir" }
    foreach($file in $summary.files) {
        if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Changed input/evidence: $($file.path)" }
    }
    $lines=@(Get-Content -LiteralPath (Join-Path $dir 'probe.log')); $rows=@(); $cutoff=$false
    foreach($line in $lines) {
        if($line -eq 'ANTHEM_PROBE_CLOCK_STOP' -or $line.StartsWith('ANTHEM_PROBE_DONE')) { $cutoff=$true }
        if(!$cutoff -and $line.StartsWith('ANTHEM_WITNESS ')) { $rows+=($line.Substring(15) | ConvertFrom-Json) }
    }
    $payments=@()
    for($i=0;$i -lt $lines.Count;$i++) {
        if($lines[$i] -match "^Add To Stack: AnthemProbe-0 cast Marshal's Anthem\s*$") {
            $mana=@()
            for($j=$i-1;$j -ge 0 -and $lines[$j] -match '^Mana: ';$j--) { $mana+=$lines[$j] }
            if(@($mana | Where-Object { $_ -notmatch '^Mana: (Plains|Swamp) ' }).Count) { throw 'Unexpected mana source' }
            $payments+=@{mana=$mana;white=@($mana | Where-Object { $_ -match 'Add \{W\}' }).Count}
        }
    }
    if($profileMode -eq 'off' -and ($rows.Count -or $payments.Count)) { throw 'Off control used Anthem' }
    $pending=@{}; $casts=@(); $returns=@(); $spellStarts=@($rows | Where-Object { $_.stage -eq 'stacked' -and $_.spell })
    if($payments.Count -lt $spellStarts.Count) { throw 'Native payment records missing' }
    for($i=0;$i -lt $spellStarts.Count;$i++) {
        $before=$spellStarts[$i]; $payment=$payments[$i]
        if($payment.mana.Count -ne 4+2*$before.kicks -or $payment.white -lt 2+$before.kicks) { throw 'Native payment does not match chosen kicks' }
        $available=@($before.lands | Where-Object { !$_.tapped }).Count+$payment.mana.Count
        $white=@($before.lands | Where-Object { !$_.tapped -and $_.name -eq 'Plains' }).Count+$payment.white
        $eligible=@($before.graveyard | Where-Object creature).Count
        $maximum=[Math]::Min($eligible,[Math]::Min([Math]::Floor(($available-4)/2),$white-2))
        if($before.kicks -ne $maximum -or ($before.kicks -eq 0 -and $before.creatures.Count -lt 2)) { throw 'Kick cap or unkicked-benefit rule violated' }
    }
    foreach($row in $rows) {
        if($row.player -ne 'AnthemProbe-0') { throw 'Unexpected actor' }
        if($row.stage -eq 'stacked') { $pending[$row.abilityId]=$row; continue }
        $before=$pending[$row.abilityId]
        if($null -eq $before -or $row.fizzled -or $row.sourceId -ne $before.sourceId -or $row.kicks -ne $before.kicks) { throw 'Unpaired or failed native resolution' }
        $pending.Remove($row.abilityId)
        if($before.spell) {
            if(!$row.anthemOnBattlefield -or $row.creatures.Count -ne $before.creatures.Count) { throw 'Original enchantment did not resolve normally' }
            foreach($card in $before.creatures) {
                $after=@($row.creatures | Where-Object id -eq $card.id)
                if($after.Count -ne 1 -or $after[0].power -ne $card.power+1 -or $after[0].toughness -ne $card.toughness+1) { throw 'Native anthem bonus missing or duplicated' }
            }
            $casts+=@{mode=$mode;seed=$seed;turn=$row.turn;kicks=$row.kicks;sourceId=$row.sourceId;before=$before;after=$row}
        } elseif($before.trigger) {
            if(!@($casts | Where-Object sourceId -eq $row.sourceId).Count) { throw 'ETB not tied to an observed resolved cast' }
            if($before.targets.Count -ne $before.kicks) { throw 'ETB selected fewer useful targets than paid kicks' }
            $new=@($row.creatures | Where-Object { $_.id -notin @($before.creatures.id) })
            if($new.Count -ne $before.kicks) { throw 'Native returned creature count does not match paid kicks' }
            foreach($card in $new) {
                if($card.id -notin @($before.targets.id) -or $card.id -notin @($before.graveyard.id) -or $card.id -in @($row.graveyard.id) -or $card.toughness -le 0) { throw 'Target did not move from own graveyard into play alive' }
            }
            $returns+=@{mode=$mode;seed=$seed;turn=$row.turn;sourceId=$row.sourceId;kicks=$row.kicks;returned=$new;before=$before;after=$row}
        } else { throw 'Unexpected ability kind' }
    }
    if($profileMode -eq 'on') {
        if(!$casts.Count -or !$returns.Count) { throw 'No completed native spell/ETB witness' }
        if($mode -eq 'kicked' -and !@($returns | Where-Object { $_.returned.Count -gt 0 }).Count) { throw 'No reanimation witness' }
        if($mode -eq 'unkicked' -and (!@($casts | Where-Object kicks -eq 0).Count -or @($casts | Where-Object kicks -ne 0).Count)) { throw 'Missing pure unkicked-buff witness' }
        if(!$summary.clockReached -and ($pending.Count -or $casts.Count -ne $returns.Count)) { throw 'Incomplete trigger without clock stop' }
    }
    $witnesses+=@{mode=$mode;profile=$profileMode;seed=$seed;casts=$casts;triggers=$returns}
    $runs+=@{mode=$mode;profile=$profileMode;seed=$seed;casts=$casts.Count;triggers=$returns.Count;chosenKicks=@($casts | ForEach-Object kicks);
        creaturesReturned=@($returns | ForEach-Object returned).Count;clockReached=$summary.clockReached;diagnostics=$summary.diagnostics;
        pendingAtClockStop=$pending.Count;lateObserverEventsExcluded=($summary.observations.Count-$rows.Count);payments=$payments;
        summary=$summaryPath;sha256=(Get-FileHash -LiteralPath $summaryPath).Hash}
} } }
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw | ConvertFrom-Json
$inputs=@($gate.files)+@('MarshalsAnthemProbe.java','Run-CardUseProbe.ps1','Check-CardUseEvidence.ps1' | ForEach-Object {
    $path=Join-Path $PSScriptRoot $_; @{path=$path;sha256=(Get-FileHash -LiteralPath $path).Hash}
})
@{date='2026-10-17';scope='Bounded kick/buff decisions and autonomous native card use; no Commander-precon or strength claim';nativeChecks=135;
    observer='Read-only event subscriber; no forced hands, draws, actions, choices, phases, targets or payment';inputs=$inputs;runs=$runs;witnesses=$witnesses} |
    ConvertTo-Json -Depth 18 | Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs | ForEach-Object { Write-Output "$($_.mode)/$($_.profile) seed $($_.seed): casts=$($_.casts), kicks=$($_.chosenKicks -join ','), returns=$($_.creaturesReturned)" }
Write-Output 'Card-use evidence PASS'
