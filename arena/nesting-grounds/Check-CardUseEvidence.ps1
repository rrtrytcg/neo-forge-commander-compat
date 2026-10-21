$ErrorActionPreference='Stop'
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw|ConvertFrom-Json
if(!$gate.completeRegressionGate -or $gate.results.Count -ne 19) { throw 'Full native/regression gate required' }
foreach($f in $gate.files) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Changed native input: $($f.path)" } }
if((Get-FileHash $gate.candidate).Hash -ne $gate.candidateSha256) { throw 'Candidate changed' }
$runs=@(); $witnesses=@()
foreach($mode in @('positive','negative')) { foreach($profileMode in @('on','off')) { foreach($seed in @(96671,96672)) {
    $path=Join-Path $PSScriptRoot "target/card-use/$mode-$profileMode-$seed/summary.json"
    $s=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json
    if($s.nativeExit -ne 0 -or $s.unexpectedDiagnostics.Count -or $s.mode -ne $mode -or $s.seed -ne $seed -or $s.profile -ne $profileMode) { throw 'Probe failed or mislabeled' }
    foreach($f in $s.files) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Changed probe input: $($f.path)" } }
    if(@($s.files|Where-Object {$_.path -like '*candidate.jar' -and $_.sha256 -eq $gate.candidateSha256}).Count -ne 1) { throw 'Native/probe candidate mismatch' }
    $lines=@(Get-Content -LiteralPath (Join-Path (Split-Path $path) 'probe.log')); $payments=@()
    for($i=0;$i -lt $lines.Count;$i++) {
        if($lines[$i] -match '^Add To Stack: NestingProbe-0 activated Nesting Grounds(?: targeting .*)?$') {
            $mana=@(); for($j=$i-1;$j -ge 0 -and $lines[$j] -match '^Mana: ';$j--) { $mana+=$lines[$j] }
            if($mana.Count -ne 1 -or $mana[0] -notmatch '^Mana: (Forest|Plains|Nesting Grounds) \((\d+)\) - \{T\}: Add \{[GWC]\}\.$') { throw 'Native one-mana payment missing' }
            $payments+=@{line=$mana[0];manaSourceId=[int]$Matches[2]}
        }
    }
    $pending=@{}; $uses=@(); $paymentIndex=0
    foreach($row in $s.observations) {
        $key=[string]$row.abilityId
        if($row.stage -eq 'stacked') {
            if($row.player -ne 'NestingProbe-0' -or $row.playerTurn -ne $row.player -or $row.phase -notin @('MAIN1','MAIN2') -or $row.spell -or $row.trigger -or !$row.landTapped) { throw 'Wrong actor, timing or unpaid tap' }
            $from=$row.donor; $to=$row.recipient
            if(!$from.creature -or !$to.creature -or !$from.battlefield -or !$to.battlefield -or $from.controller -ne $row.player -or $from.id -eq $to.id) { throw 'Invalid coordinated targets' }
            $payment=$payments[$paymentIndex]; $paymentIndex++
            if(!$payment -or $payment.manaSourceId -eq $row.sourceId) { throw 'Missing additional mana or land tapped for its own activation' }
            if($mode -eq 'positive') {
                if($from.plus -lt 1 -or $to.controller -ne $row.player -or $from.toughness-1 -le $from.damage) { throw 'Unsafe positive transfer' }
                if(!($from.plus -ge 2 -and $to.plus -eq 0)) { throw 'Plain positive probe expected counter spreading' }
            } else {
                if($from.minus -lt 1 -or !$to.opponent) { throw 'Unsafe negative transfer' }
            }
            if($pending.ContainsKey($key)) { throw 'Duplicate stack event' }
            $pending[$key]=@{before=$row;payment=$payment;resolved=$null}; continue
        }
        $entry=$pending[$key]; if(!$entry) { throw 'Unpaired native observation' }
        $before=$entry.before; $from=$before.donor; $to=$before.recipient
        if($row.fizzled -or $row.sourceId -ne $before.sourceId -or $row.donor.id -ne $from.id -or $row.recipient.id -ne $to.id) { throw 'Resolution changed target identity or fizzled' }
        if($row.stage -eq 'resolved') {
            $type=if($mode -eq 'positive') {'plus'} else {'minus'}
            $other=if($mode -eq 'positive') {'minus'} else {'plus'}
            $added=if($mode -eq 'positive') {1+[int]$before.scales} else {1}
            $sign=if($mode -eq 'positive') {1} else {-1}
            if($row.donor.$type -ne $from.$type-1 -or $row.recipient.$type -ne $to.$type+$added -or $row.donor.$other -ne $from.$other -or $row.recipient.$other -ne $to.$other) { throw 'Native movement/replacement counter deltas incorrect' }
            if($row.donor.power -ne $from.power-$sign -or $row.donor.toughness -ne $from.toughness-$sign -or $row.recipient.power -ne $to.power+$sign*$added -or $row.recipient.toughness -ne $to.toughness+$sign*$added) { throw 'Actual native creature stats incorrect' }
            $entry.resolved=$row; continue
        }
        if($row.stage -ne 'settled' -or !$entry.resolved -or !$row.donor.battlefield) { throw 'Missing settlement or donor died' }
        $kills=$mode -eq 'negative' -and $to.toughness -le 1
        if($row.recipient.battlefield -eq $kills) { throw 'Native settled recipient survival/death differs from arithmetic oracle' }
        $uses+=@{mode=$mode;seed=$seed;before=$before;payment=$entry.payment;resolved=$entry.resolved;after=$row;
            recipientCountersAdded=if($mode -eq 'positive') {1+[int]$before.scales} else {1};lethalNegative=$kills}
        $pending.Remove($key)
    }
    if($profileMode -eq 'off' -and ($uses.Count -or $pending.Count -or $payments.Count -or $s.observations.Count)) { throw 'Off control activated Nesting Grounds' }
    if($profileMode -eq 'on' -and !$uses.Count) { throw 'No paid/resolved/settled activation witness' }
    if($payments.Count -ne $uses.Count+$pending.Count) { throw 'Native payments and stack observations differ' }
    if(!$s.clockReached -and $pending.Count) { throw 'Pending activation without clock stop' }
    $runs+=@{mode=$mode;profile=$profileMode;seed=$seed;activations=$uses.Count;recipientCountersAdded=($uses|Measure-Object recipientCountersAdded -Sum).Sum;
        lethalNegative=@($uses|Where-Object lethalNegative).Count;scalesActivations=@($uses|Where-Object {$mode -eq 'positive' -and $_.before.scales -gt 0}).Count;
        clockReached=$s.clockReached;pendingAtClockStop=$pending.Count;lateObserverEventsExcluded=$s.lateObserverEventsExcluded;diagnostics=$s.diagnostics;payments=$payments;summary=$path;sha256=(Get-FileHash $path).Hash}
    $witnesses+=@{mode=$mode;profile=$profileMode;seed=$seed;activations=$uses}
} } }
$inputs=@($gate.files)+@('NestingGroundsProbe.java','Run-CardUseProbe.ps1','Check-CardUseEvidence.ps1'|ForEach-Object {$p=Join-Path $PSScriptRoot $_; @{path=$p;sha256=(Get-FileHash $p).Hash}})
@{date='2026-10-17';engine=$gate.engine;candidateSha256=$gate.candidateSha256;nativeResults=$gate.results;inputs=$inputs;runs=$runs;witnesses=$witnesses;
 scope='Autonomous original-script counter transfers, native payment and actual counter/stat deltas; bounded custom decks, no completion/winner/strength claim'}|ConvertTo-Json -Depth 20|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs|ForEach-Object {Write-Output "$($_.mode)/$($_.profile)/$($_.seed): $($_.activations) settled activations, $($_.recipientCountersAdded) counters added, $($_.lethalNegative) lethal negative transfers"}
Write-Output 'Card-use evidence PASS'
