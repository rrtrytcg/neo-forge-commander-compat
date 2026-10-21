$ErrorActionPreference='Stop'
$runs=@(); $witnesses=@()
foreach($mode in @('threats','utility')) { foreach($profileMode in @('on','off')) { foreach($seed in @(96341,96342)) {
    $dir=Join-Path $PSScriptRoot "target/card-use/$mode-$profileMode-$seed"
    $summaryPath=Join-Path $dir 'summary.json'
    $summary=Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
    if($summary.nativeExit -ne 0 -or $summary.unexpectedDiagnostics.Count) { throw "Probe failure: $dir" }
    foreach($file in $summary.files) {
        if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Changed input/evidence: $($file.path)" }
    }
    $lines=@(Get-Content -LiteralPath (Join-Path $dir 'probe.log')); $rows=@(); $cutoff=$false
    foreach($line in $lines) {
        if($line -eq 'MUTATION_PROBE_CLOCK_STOP' -or $line.StartsWith('MUTATION_PROBE_DONE')) { $cutoff=$true }
        if(!$cutoff -and $line.StartsWith('MUTATION_WITNESS ')) { $rows+=($line.Substring(17) | ConvertFrom-Json) }
    }
    $payments=@()
    for($i=0;$i -lt $lines.Count;$i++) {
        if($lines[$i] -match '^Add To Stack: MutationProbe-0 cast Darksteel Mutation targeting ') {
            $mana=@()
            for($j=$i-1;$j -ge 0 -and $lines[$j] -match '^Mana: ';$j--) { $mana+=$lines[$j] }
            if($mana.Count -ne 2 -or @($mana | Where-Object { $_ -notmatch '^Mana: Plains .*Add \{W\}' }).Count) { throw 'Native 1W payment missing' }
            $payments+=@{mana=$mana}
        }
    }
    if($profileMode -eq 'off' -and ($rows.Count -or $payments.Count)) { throw 'Off control used Mutation' }
    $pending=@{}; $casts=@(); $seenTargets=@{}
    foreach($row in $rows) {
        if($row.player -ne 'MutationProbe-0' -or !$row.spell -or $row.trigger -or $row.targets.Count -ne 1) { throw 'Unexpected actor, ability or target count' }
        if($row.stage -eq 'stacked') {
            $t=$row.targets[0]
            if($t.controller -eq $row.player -or !$t.creature -or $seenTargets.ContainsKey($t.id)) { throw 'Friendly, repeated or noncreature target' }
            $seenTargets[$t.id]=$true; $pending[$row.abilityId]=$row; continue
        }
        $before=$pending[$row.abilityId]; $t=$row.targets[0]
        if($null -eq $before -or $row.fizzled -or $row.sourceId -ne $before.sourceId) { throw 'Unpaired or failed native resolution' }
        $pending.Remove($row.abilityId)
        if(!$row.auraOnBattlefield -or $row.attachedTo -ne $t.id -or $t.id -ne $before.targets[0].id) { throw 'Native Aura did not attach to selected creature' }
        if($t.basePower -ne 0 -or $t.baseToughness -ne 1 -or $t.power -ne 0 -or $t.toughness -ne 1 -or !$t.insect -or !$t.artifact -or !$t.indestructible) { throw 'Native transformation missing' }
        if($t.abilities -ne 0 -or $t.manaAbilities -ne 0) { throw 'Native ability removal missing' }
        if($mode -eq 'threats' -and (!$before.targets[0].indestructible -or $before.targets[0].power -lt 2)) { throw 'Expected indestructible combat threat' }
        if($mode -eq 'utility' -and ($before.targets[0].power -gt 1 -or $before.targets[0].abilities -lt 1)) { throw 'Expected small utility creature' }
        $casts+=@{mode=$mode;seed=$seed;turn=$row.turn;sourceId=$row.sourceId;before=$before;after=$row}
    }
    if($profileMode -eq 'on' -and !$casts.Count) { throw 'No paid/resolved cast witness' }
    if(!$summary.clockReached -and $pending.Count) { throw 'Incomplete cast without clock stop' }
    if($payments.Count -lt ($casts.Count+$pending.Count)) { throw 'Missing native payment log' }
    $witnesses+=@{mode=$mode;profile=$profileMode;seed=$seed;casts=$casts}
    $runs+=@{mode=$mode;profile=$profileMode;seed=$seed;casts=$casts.Count;targets=@($casts | ForEach-Object { $_.before.targets[0].name });
        clockReached=$summary.clockReached;diagnostics=$summary.diagnostics;pendingAtClockStop=$pending.Count;
        lateObserverEventsExcluded=($summary.observations.Count-$rows.Count);payments=$payments;
        summary=$summaryPath;sha256=(Get-FileHash -LiteralPath $summaryPath).Hash}
} } }
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw | ConvertFrom-Json
$candidateHash=(Get-FileHash (Join-Path $PSScriptRoot 'target/final/engine.jar')).Hash
foreach($run in $runs) {
    $s=Get-Content $run.summary -Raw | ConvertFrom-Json
    if(@($s.files | Where-Object { $_.path -like '*candidate.jar' -and $_.sha256 -eq $candidateHash }).Count -ne 1) { throw 'Probe candidate differs from native-tested jar' }
}
$inputs=@($gate.files)+@('DarksteelMutationProbe.java','Run-CardUseProbe.ps1','Check-CardUseEvidence.ps1' | ForEach-Object {
    $path=Join-Path $PSScriptRoot $_; @{path=$path;sha256=(Get-FileHash -LiteralPath $path).Hash}
})
@{date='2026-10-17';scope='Bounded neutralization policy and autonomous native card use; no Commander-precon or strength claim';nativeChecks=87;
    observer='Read-only event subscriber; no forced hands, draws, actions, choices, phases, targets or payment';inputs=$inputs;runs=$runs;witnesses=$witnesses} |
    ConvertTo-Json -Depth 18 | Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs | ForEach-Object { Write-Output "$($_.mode)/$($_.profile) seed $($_.seed): casts=$($_.casts), targets=$($_.targets -join ',')" }
Write-Output 'Card-use evidence PASS'
