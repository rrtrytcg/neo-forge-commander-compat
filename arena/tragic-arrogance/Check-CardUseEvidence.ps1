$ErrorActionPreference='Stop'

# Independent exhaustive oracle for the small custom-deck boards, not the policy's 16-state solver.
function Get-SurvivorOptimum($cards,[bool]$friendly) {
    $groups=[Collections.Generic.List[object]]::new()
    foreach($bit in @(1,2,4,8)) {
        $group=@($cards | Where-Object { ($_.types -band $bit) -ne 0 })
        if(!$group.Count) { $group=@($null) }
        $groups.Add($group)
    }
    $bestValue=if($friendly) { [int]::MinValue } else { [int]::MaxValue }
    $bestCount=0
    foreach($artifact in $groups[0]) { foreach($creature in $groups[1]) {
        foreach($enchantment in $groups[2]) { foreach($walker in $groups[3]) {
            $seen=@{}; $value=0; $count=0
            foreach($card in @($artifact,$creature,$enchantment,$walker)) {
                if($null -ne $card -and !$card.land -and $card.sacrificable -and !$seen.ContainsKey($card.id)) {
                    $seen[$card.id]=$true; $value+=$card.value; $count++
                }
            }
            if(($friendly -and ($value -gt $bestValue -or ($value -eq $bestValue -and $count -gt $bestCount))) -or
                (!$friendly -and ($value -lt $bestValue -or ($value -eq $bestValue -and $count -lt $bestCount)))) {
                $bestValue=$value; $bestCount=$count
            }
        } }
    } }
    return @{value=$bestValue;count=$bestCount}
}

$runs=@(); $pairs=@()
foreach($profileMode in @('on','off')) { foreach($seed in @(96121,96122)) {
    $dir=Join-Path $PSScriptRoot "target/card-use/$profileMode-$seed"
    $summaryPath=Join-Path $dir 'summary.json'
    $summary=Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
    if($summary.nativeExit -ne 0 -or $summary.unexpectedDiagnostics.Count) { throw "Probe failure: $dir" }
    foreach($file in $summary.files) {
        if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Changed input/evidence: $($file.path)" }
    }
    $lines=@(Get-Content -LiteralPath (Join-Path $dir 'probe.log')); $rows=@(); $cutoff=$false
    foreach($line in $lines) {
        if($line -eq 'ARROGANCE_PROBE_CLOCK_STOP' -or $line.StartsWith('ARROGANCE_PROBE_DONE')) { $cutoff=$true }
        if(!$cutoff -and $line.StartsWith('ARROGANCE_WITNESS ')) { $rows+=($line.Substring(18) | ConvertFrom-Json) }
    }
    $payments=@()
    for($i=0;$i -lt $lines.Count;$i++) {
        if($lines[$i] -match '^Add To Stack: ArroganceProbe-0 cast Tragic Arrogance\s*$') {
            $mana=@()
            for($j=$i-1;$j -ge 0 -and $lines[$j] -match '^Mana: ';$j--) { $mana+=$lines[$j] }
            if($mana.Count -ne 5 -or @($mana | Where-Object { $_ -notmatch '^Mana: Plains .*Add \{W\}' }).Count) { throw 'Missing native 3WW payment' }
            $payments+=@{mana=$mana}
        }
    }
    if($profileMode -eq 'off' -and ($rows.Count -or $payments.Count)) { throw 'Off control used Tragic Arrogance' }
    $pending=@{}; $local=@(); $casts=@($rows | Where-Object stage -eq 'stacked')
    foreach($row in $rows) {
        if($row.player -ne 'ArroganceProbe-0') { throw 'Unexpected caster' }
        if($row.stage -eq 'stacked') { $pending[$row.abilityId]=$row; continue }
        $before=$pending[$row.abilityId]
        if($null -eq $before -or $row.fizzled -or $before.sourceId -ne $row.sourceId -or !$row.rememberedEmpty) { throw 'Unpaired/failed resolution or incomplete cleanup' }
        $pending.Remove($row.abilityId)
        $friendlyLoss=0; $enemyLoss=0; $friendlyValue=0; $enemyValue=0; $lost=@(); $playerResults=@()
        foreach($player in $before.board) {
            $after=@($row.board | Where-Object id -eq $player.id)
            if($after.Count -ne 1) { throw 'Player disappeared during native resolution' }
            $remaining=@($after[0].cards.id)
            $oracle=Get-SurvivorOptimum $player.cards (!$player.opponent)
            $saved=@($player.cards | Where-Object { !$_.land -and $_.sacrificable -and $_.id -in $remaining })
            $savedValue=($saved | Measure-Object value -Sum).Sum
            if($null -eq $savedValue) { $savedValue=0 }
            if($savedValue -ne $oracle.value -or $saved.Count -ne $oracle.count) { throw 'Actual survivors do not match independent optimum' }
            foreach($bit in @(1,2,4,8)) {
                $category=@($player.cards | Where-Object { ($_.types -band $bit) -ne 0 })
                if($category.Count -and !@($category | Where-Object { $_.id -in $remaining }).Count) { throw 'Mandatory category has no surviving chosen permanent' }
            }
            foreach($card in $player.cards) {
                if(($card.land -or !$card.sacrificable) -and $card.id -notin $remaining) { throw 'Land or unsacrificable permanent removed' }
            }
            $loss=@($player.cards | Where-Object { $_.id -notin $remaining })
            $lost+=$loss
            $lossValue=($loss | Measure-Object value -Sum).Sum
            if($player.opponent) { $enemyLoss+=$loss.Count; $enemyValue+=$lossValue }
            else { $friendlyLoss+=$loss.Count; $friendlyValue+=$lossValue }
            $playerResults+=@{player=$player.name;opponent=$player.opponent;lost=@($loss.id);savedVulnerable=@($saved.id);savedValue=$savedValue;oracle=$oracle}
        }
        if($enemyLoss -lt $friendlyLoss+2 -or $enemyValue -le $friendlyValue) { throw 'Actual sacrifice margin violates cast rule' }
        if(@($row.nativeSacrifices).Count -ne $lost.Count -or @($lost | Where-Object { $_.id -notin $row.nativeSacrifices }).Count) { throw 'Native sacrifice events do not match removed permanents' }
        $local+=@{seed=$seed;turn=$row.turn;friendlyLost=$friendlyLoss;enemyLost=$enemyLoss;friendlyValue=$friendlyValue;enemyValue=$enemyValue;
            indestructibleSacrificed=@($lost | Where-Object indestructible).Count;players=$playerResults;before=$before;after=$row}
    }
    if($profileMode -eq 'on') {
        if(!$local.Count -or $payments.Count -lt $casts.Count) { throw 'Missing native paid/resolved card-use witness' }
        if(!$summary.clockReached -and $pending.Count) { throw 'Pending action without recorded clock stop' }
    }
    $pairs+=$local
    $runs+=@{profile=$profileMode;seed=$seed;casts=$casts.Count;resolved=$local.Count;friendlySacrifices=($local | Measure-Object friendlyLost -Sum).Sum;
        enemySacrifices=($local | Measure-Object enemyLost -Sum).Sum;indestructibleSacrifices=($local | Measure-Object indestructibleSacrificed -Sum).Sum;
        clockReached=$summary.clockReached;diagnostics=$summary.diagnostics;pendingAtClockStop=$pending.Count;
        lateObserverEventsExcluded=($summary.observations.Count-$rows.Count);payments=$payments;summary=$summaryPath;sha256=(Get-FileHash -LiteralPath $summaryPath).Hash}
} }
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw | ConvertFrom-Json
$inputs=@($gate.files)+@('TragicArroganceProbe.java','Run-CardUseProbe.ps1','Check-CardUseEvidence.ps1' | ForEach-Object {
    $path=Join-Path $PSScriptRoot $_; @{path=$path;sha256=(Get-FileHash -LiteralPath $path).Hash}
})
@{date='2026-10-17';scope='Bounded survivor/cast policy and autonomous custom-deck use; no Commander-precon or strength claim';nativeChecks=132;
    oracle='Independent exhaustive category combinations on before-state snapshots, compared with actual native survivors and sacrifice events';
    observer='Read-only subscriber; no forced actions, hands, draws, phases, choices or payment';inputs=$inputs;runs=$runs;resolvedWitnesses=$pairs} |
    ConvertTo-Json -Depth 17 | Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs | ForEach-Object { Write-Output "$($_.profile) seed $($_.seed): casts=$($_.casts), resolved=$($_.resolved), friendly lost=$($_.friendlySacrifices), enemy lost=$($_.enemySacrifices)" }
Write-Output "Card-use evidence PASS: $($pairs.Count) paid native resolutions with optimal survivor sets"
