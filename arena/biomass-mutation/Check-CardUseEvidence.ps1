$ErrorActionPreference='Stop'
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw|ConvertFrom-Json
if(!$gate.completeRegressionGate -or $gate.results.Count -ne 20) { throw 'Full native/regression gate required' }
foreach($f in $gate.files) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Changed native input: $($f.path)" } }
if((Get-FileHash $gate.candidate).Hash -ne $gate.candidateSha256) { throw 'Candidate changed' }
$runs=@(); $witnesses=@()
foreach($mode in @('offense','defense')) { foreach($profileMode in @('on','off')) { foreach($seed in @(96781,96782)) {
    $path=Join-Path $PSScriptRoot "target/card-use/$mode-$profileMode-$seed/summary.json"
    $s=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json
    if($s.nativeExit -ne 0 -or $s.unexpectedDiagnostics.Count -or $s.mode -ne $mode -or $s.seed -ne $seed -or $s.profile -ne $profileMode) { throw 'Probe failed or mislabeled' }
    foreach($f in $s.files) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Changed probe input: $($f.path)" } }
    if(@($s.files|Where-Object {$_.path -like '*candidate.jar' -and $_.sha256 -eq $gate.candidateSha256}).Count -ne 1) { throw 'Native/probe candidate mismatch' }
    $lines=@(Get-Content -LiteralPath (Join-Path (Split-Path $path) 'probe.log')); $payments=@()
    for($i=0;$i -lt $lines.Count;$i++) {
        if($lines[$i] -match '^Add To Stack: BiomassProbe-0 cast Biomass Mutation$') {
            $mana=@(); for($j=$i-1;$j -ge 0 -and $lines[$j] -match '^Mana: ';$j--) { $mana+=$lines[$j] }
            $expected=if($mode -eq 'offense') { '^Mana: Forest \(\d+\) - \{T\}: Add \{G\}\.$' } else { '^Mana: Island \(\d+\) - \{T\}: Add \{U\}\.$' }
            if($mana.Count -lt 5 -or @($mana|Where-Object {$_ -notmatch $expected}).Count) { throw 'Native X plus two hybrid payment missing' }
            $payments+=@{lines=$mana;mana=$mana.Count;x=$mana.Count-2}
        }
    }
    $pending=@{}; $casts=@(); $paymentIndex=0
    foreach($row in $s.observations) {
        $key=[string]$row.abilityId
        if($row.stage -eq 'stacked') {
            $offense=$mode -eq 'offense'
            if($row.player -ne 'BiomassProbe-0' -or !$row.spell -or $row.trigger -or $row.x -lt 3 -or $row.untappedLands -ne 0) { throw 'Wrong actor, spell, X or unspent plain mana' }
            if(($offense -and ($row.phase -ne 'COMBAT_BEGIN' -or $row.turnPlayer -ne $row.player)) -or (!$offense -and ($row.phase -ne 'COMBAT_DECLARE_BLOCKERS' -or $row.turnPlayer -eq $row.player))) { throw 'Wrong combat timing' }
            $payment=$payments[$paymentIndex]; $paymentIndex++
            if(!$payment -or $payment.x -ne $row.x) { throw 'Observed X differs from native payment after hybrid symbols' }
            $improved=0
            foreach($c in $row.creatures) {
                if($c.plus -ne 0 -or $c.minus -ne 0 -or $c.name -notin @('Grizzly Bears','Wall of Air')) { throw 'Probe arithmetic assumes plain original creatures' }
                if($c.power -gt $row.x -or $c.toughness -gt $row.x -or $c.damage -ge $row.x) { throw 'Cast shrinks or kills friendly creature' }
                if((($offense -and $c.canAttack) -or (!$offense -and $c.blocking)) -and $c.power -lt $row.x) { $improved++ }
            }
            if($improved -lt 2 -or $pending.ContainsKey($key)) { throw 'Too few improving combat participants or duplicate observation' }
            $pending[$key]=@{before=$row;payment=$payment;improved=$improved;resolved=$null}; continue
        }
        $entry=$pending[$key]; if(!$entry) { throw 'Unpaired observation' }
        $before=$entry.before
        if($row.fizzled -or $row.sourceId -ne $before.sourceId -or $row.x -ne $before.x) { throw 'Fizzle or changed spell identity/X' }
        if($row.stage -eq 'resolved' -or $row.stage -eq 'settled') {
            foreach($c in $before.creatures) {
                $after=@($row.creatures|Where-Object id -eq $c.id)
                if($after.Count -ne 1 -or $after[0].power -ne $before.x -or $after[0].toughness -ne $before.x -or $after[0].types -cne $c.types -or $after[0].plus -ne 0 -or $after[0].minus -ne 0) { throw 'Actual native base stats, survival or creature types differ from oracle' }
                if($mode -eq 'defense' -and $row.stage -eq 'resolved' -and $after[0].blocking -ne $c.blocking) { throw 'Block assignments changed at resolution' }
            }
            if($row.stage -eq 'resolved') { $entry.resolved=$row; continue }
            if(!$entry.resolved) { throw 'Settlement before resolution' }
            $casts+=@{mode=$mode;seed=$seed;before=$before;payment=$entry.payment;improvedParticipants=$entry.improved;resolved=$entry.resolved;after=$row}
            $pending.Remove($key); continue
        }
        throw 'Unexpected observation stage'
    }
    if($profileMode -eq 'off' -and ($casts.Count -or $pending.Count -or $payments.Count -or $s.observations.Count)) { throw 'Off control cast Biomass Mutation' }
    if($profileMode -eq 'on' -and !$casts.Count) { throw 'No paid/resolved/settled cast witness' }
    if($payments.Count -ne $casts.Count+$pending.Count) { throw 'Native payments and stack observations differ' }
    if(!$s.clockReached -and $pending.Count) { throw 'Pending observation without clock stop' }
    $runs+=@{mode=$mode;profile=$profileMode;seed=$seed;casts=$casts.Count;xs=@($casts|ForEach-Object {$_.before.x});improvedParticipants=($casts|Measure-Object improvedParticipants -Sum).Sum;
        clockReached=$s.clockReached;pendingAtClockStop=$pending.Count;lateObserverEventsExcluded=$s.lateObserverEventsExcluded;diagnostics=$s.diagnostics;payments=$payments;summary=$path;sha256=(Get-FileHash $path).Hash}
    $witnesses+=@{mode=$mode;profile=$profileMode;seed=$seed;casts=$casts}
} } }
$inputs=@($gate.files)+@('BiomassMutationProbe.java','Run-CardUseProbe.ps1','Check-CardUseEvidence.ps1'|ForEach-Object {$p=Join-Path $PSScriptRoot $_; @{path=$p;sha256=(Get-FileHash $p).Hash}})
@{date='2026-10-17';engine=$gate.engine;candidateSha256=$gate.candidateSha256;nativeResults=$gate.results;inputs=$inputs;runs=$runs;witnesses=$witnesses;
 scope='Autonomous original-script combat casting with native hybrid payment, actual P/T and types; bounded custom decks, no completion/winner/strength claim'}|ConvertTo-Json -Depth 20|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs|ForEach-Object {Write-Output "$($_.mode)/$($_.profile)/$($_.seed): $($_.casts) settled casts, X=$($_.xs -join ',')"}
Write-Output 'Card-use evidence PASS'
