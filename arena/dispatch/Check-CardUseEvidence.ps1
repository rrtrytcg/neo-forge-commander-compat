$ErrorActionPreference='Stop'
$runs=@(); $pairs=@()
foreach($profileMode in @('on','off')) {
    foreach($seed in @(96011,96012)) {
        $dir=Join-Path $PSScriptRoot "target/card-use/$profileMode-$seed"
        $summaryPath=Join-Path $dir 'summary.json'
        $summary=Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
        if($summary.nativeExit -ne 0 -or $summary.unexpectedDiagnostics.Count) { throw "Probe failure: $dir" }
        foreach($file in $summary.files) {
            if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Changed input/evidence: $($file.path)" }
        }
        $lines=@(Get-Content -LiteralPath (Join-Path $dir 'probe.log'))
        $rows=@(); $cutoff=$false
        foreach($line in $lines) {
            if($line -eq 'DISPATCH_PROBE_CLOCK_STOP' -or $line.StartsWith('DISPATCH_PROBE_DONE')) { $cutoff=$true }
            if(!$cutoff -and $line.StartsWith('DISPATCH_WITNESS ')) { $rows+=($line.Substring(17) | ConvertFrom-Json) }
        }
        $payments=@()
        for($i=0;$i -lt $lines.Count;$i++) {
            if($lines[$i] -match '^Add To Stack: DispatchProbe-0 cast Dispatch targeting \[.+ \((\d+)\)\]\s*$') {
                $targetId=[int]$Matches[1]
                $mana=@()
                for($j=$i-1;$j -ge 0 -and $lines[$j] -match '^Mana: ';$j--) { $mana+=$lines[$j] }
                if($mana.Count -ne 1 -or $mana[0] -notmatch '^Mana: Plains .*Add \{W\}') { throw 'Missing native W payment' }
                $payments+=@{mana=$mana;targetId=$targetId}
            }
        }
        if($profileMode -eq 'off' -and ($rows.Count -or $payments.Count)) { throw 'Off control used Dispatch' }
        $pending=@{}; $local=@(); $casts=@($rows | Where-Object stage -eq 'stacked')
        foreach($row in $rows) {
            if($row.player -ne 'DispatchProbe-0') { throw 'Unexpected actor' }
            if($row.stage -eq 'stacked') {
                if(!$row.metalcraft -or $row.artifacts -lt 3 -or $row.targets.Count -ne 1) { throw 'Invalid metalcraft/target count' }
                $target=$row.targets[0]
                if(!$target.creature -or !$target.opponent -or !$target.legal -or !$target.onBattlefield -or $target.exiled) { throw 'Invalid initial target' }
                $pending[$row.abilityId]=$row; continue
            }
            $before=$pending[$row.abilityId]
            if($null -eq $before -or $row.fizzled -or $row.sourceId -ne $before.sourceId -or !$row.metalcraft) { throw 'Unpaired/failed resolution' }
            $pending.Remove($row.abilityId)
            if($row.targets.Count -ne 1 -or $row.targets[0].id -ne $before.targets[0].id -or !$row.targets[0].exiled -or $row.targets[0].onBattlefield) { throw 'Original target did not reach exile' }
            $local+=@{seed=$seed;turn=$row.turn;target=$before.targets[0].name;tappedBefore=$before.targets[0].tapped;
                indestructibleBefore=$before.targets[0].indestructible;before=$before;after=$row}
        }
        if($profileMode -eq 'on') {
            if(!$local.Count -or $payments.Count -lt $casts.Count) { throw "Missing complete native cast/payment/exile witness: seed=$seed pairs=$($local.Count) casts=$($casts.Count) payments=$($payments.Count)" }
            if(!$summary.clockReached -and $pending.Count) { throw 'Incomplete action without a recorded clock stop' }
            for($i=0;$i -lt $casts.Count;$i++) {
                if($payments[$i].targetId -ne $casts[$i].targets[0].id) { throw 'Native payment/cast log target does not match observed spell' }
            }
        }
        $pairs+=$local
        $runs+=@{profile=$profileMode;seed=$seed;casts=$casts.Count;resolvedExiles=$local.Count;
            tappedTargets=@($local | Where-Object tappedBefore).Count;indestructibleTargets=@($local | Where-Object indestructibleBefore).Count;
            clockReached=$summary.clockReached;diagnostics=$summary.diagnostics;pendingAtClockStop=$pending.Count;
            lateObserverEventsExcluded=($summary.observations.Count-$rows.Count);payments=$payments;summary=$summaryPath;sha256=(Get-FileHash -LiteralPath $summaryPath).Hash}
    }
}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw | ConvertFrom-Json
$inputs=@($gate.files)+@('DispatchProbe.java','Run-CardUseProbe.ps1','Check-CardUseEvidence.ps1' | ForEach-Object {
    $path=Join-Path $PSScriptRoot $_; @{path=$path;sha256=(Get-FileHash -LiteralPath $path).Hash}
})
@{date='2026-10-17';scope='Bounded native removal rules and autonomous custom-deck card use; no Commander-precon or strength claim';nativeChecks=61;
    observer='Read-only subscriber; no forced actions, hands, draws, phases or payment';inputs=$inputs;runs=$runs;resolvedWitnesses=$pairs} |
    ConvertTo-Json -Depth 15 | Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs | ForEach-Object { Write-Output "$($_.profile) seed $($_.seed): casts=$($_.casts), resolved exiles=$($_.resolvedExiles), tapped=$($_.tappedTargets), indestructible=$($_.indestructibleTargets)" }
Write-Output "Card-use evidence PASS: $($pairs.Count) resolved exiles"
