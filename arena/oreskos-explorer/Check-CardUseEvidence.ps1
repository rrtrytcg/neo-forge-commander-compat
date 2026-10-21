$ErrorActionPreference='Stop'
$runs=@(); $witnesses=@()
foreach($profileMode in @('on','off')) {
    foreach($seed in @(95901,95902)) {
        $dir=Join-Path $PSScriptRoot "target/card-use/$profileMode-$seed"
        $summaryPath=Join-Path $dir 'summary.json'
        $summary=Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
        if($summary.nativeExit -ne 0 -or $summary.unexpectedDiagnostics.Count) { throw "Probe failure: $dir" }
        foreach($file in $summary.files) {
            if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Changed evidence/input: $($file.path)" }
        }
        $lines=@(Get-Content -LiteralPath (Join-Path $dir 'probe.log'))
        $rows=@(); $cutoff=$false
        foreach($line in $lines) {
            if($line -eq 'EXPLORER_PROBE_CLOCK_STOP' -or $line.StartsWith('EXPLORER_PROBE_DONE')) { $cutoff=$true }
            if(!$cutoff -and $line.StartsWith('EXPLORER_WITNESS ')) { $rows+=($line.Substring(17) | ConvertFrom-Json) }
        }
        $payments=@()
        for($i=0;$i -lt $lines.Count;$i++) {
            if($lines[$i] -match '^Add To Stack: ExplorerProbe-0 cast Oreskos Explorer\s*$') {
                $mana=@()
                for($j=$i-1;$j -ge 0 -and $lines[$j] -match '^Mana: ';$j--) { $mana+=$lines[$j] }
                if($mana.Count -ne 2 -or @($mana | Where-Object { $_ -notmatch '^Mana: (Plains|Hallowed Fountain) ' }).Count -or !@($mana | Where-Object { $_ -match 'Add \{W\}' }).Count) { throw 'Missing native 1W payment' }
                $payments+=@{mana=$mana}
            }
        }
        if($profileMode -eq 'off' -and ($rows.Count -or $payments.Count)) { throw 'Off control used Oreskos Explorer' }
        $pending=@{}; $local=@(); $casts=@(); $searches=@()
        foreach($row in $rows) {
            if($row.player -ne 'ExplorerProbe-0') { throw 'Unexpected actor' }
            if($row.stage -eq 'stacked') { $pending[$row.abilityId]=$row; continue }
            $before=$pending[$row.abilityId]
            if($null -eq $before -or $row.fizzled -or $row.sourceId -ne $before.sourceId) { throw 'Unpaired or failed resolution' }
            $pending.Remove($row.abilityId)
            if($before.spell) {
                if(!@($row.battlefield | Where-Object id -eq $row.sourceId).Count) { throw 'Creature did not enter battlefield' }
                $casts+=@{seed=$seed;sourceId=$row.sourceId;turn=$row.turn;before=$before;after=$row}
            } elseif($before.trigger) {
                if(!@($casts | Where-Object { $_.sourceId -eq $row.sourceId -and $_.turn -eq $row.turn }).Count) { throw 'Search not tied to observed native cast' }
                $fetched=@($row.hand | Where-Object { $_.id -notin @($before.hand.id) })
                $eligible=@($before.library | Where-Object plains)
                $expected=[Math]::Min([int]$row.x,$eligible.Count)
                if($before.x -ne $row.x -or $fetched.Count -ne $expected -or $before.lands -ne $row.lands) { throw 'Search count/destination mismatch' }
                foreach($card in $fetched) {
                    if(!$card.plains -or $card.id -notin @($eligible.id) -or $card.id -in @($row.library.id)) { throw 'Search selected illegal card or did not move it from library' }
                }
                $searches+=@{seed=$seed;sourceId=$row.sourceId;turn=$row.turn;x=$row.x;fetched=$fetched;before=$before;after=$row}
            } else { throw 'Unexpected ability kind' }
        }
        if($profileMode -eq 'on') {
            if(!$casts.Count -or !$searches.Count -or $casts.Count -lt $searches.Count -or $payments.Count -lt $casts.Count) { throw 'Missing native cast/search/payment witnesses' }
            if(!$summary.clockReached -and ($pending.Count -or $casts.Count -ne $searches.Count)) { throw 'Incomplete search without a recorded clock stop' }
            if(!@($searches | Where-Object { $_.fetched.Count -gt 0 }).Count) { throw 'No successful search witnessed' }
        }
        $witnesses+=@{profile=$profileMode;seed=$seed;casts=$casts;searches=$searches;payments=$payments}
        $runs+=@{profile=$profileMode;seed=$seed;casts=$casts.Count;searches=$searches.Count;fetched=@($searches | ForEach-Object fetched).Count;
            xValues=@($searches | ForEach-Object x);clockReached=$summary.clockReached;diagnostics=$summary.diagnostics;
            pendingAtClockStop=$pending.Count;lateObserverEventsExcluded=($summary.observations.Count-$rows.Count);summary=$summaryPath;sha256=(Get-FileHash -LiteralPath $summaryPath).Hash}
    }
}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw | ConvertFrom-Json
$inputs=@($gate.files)+@('OreskosExplorerProbe.java','Run-CardUseProbe.ps1','Check-CardUseEvidence.ps1' | ForEach-Object {
    $path=Join-Path $PSScriptRoot $_; @{path=$path;sha256=(Get-FileHash -LiteralPath $path).Hash}
})
@{date='2026-10-17';scope='Native correctness and autonomous custom-deck card use, no strength or Commander-precon claim';nativeChecks=100;
    observer='Read-only subscriber; no forced actions, hands, draws, phases or payment';inputs=$inputs;runs=$runs;witnesses=$witnesses} |
    ConvertTo-Json -Depth 18 | Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs | ForEach-Object { Write-Output "$($_.profile) seed $($_.seed): casts=$($_.casts), searches=$($_.searches), fetched=$($_.fetched), X=$($_.xValues -join ',')" }
Write-Output 'Card-use evidence PASS'
