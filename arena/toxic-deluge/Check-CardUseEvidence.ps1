$ErrorActionPreference='Stop'
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw|ConvertFrom-Json
if(!$gate.completeRegressionGate -or $gate.results.Count -ne 18) { throw 'Full native/regression gate required' }
foreach($f in $gate.files) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Changed native input: $($f.path)" } }
if((Get-FileHash $gate.candidate).Hash -ne $gate.candidateSha256) { throw 'Candidate changed' }

# Independent arithmetic oracle for the deliberately plain probe creatures, not a call to the policy.
function Get-Losses($cards,[int]$x) {
    return @($cards|Where-Object {$_.toughness -le $x -or (!$_.indestructible -and $_.damage -gt 0 -and $_.damage -ge $_.toughness-$x)})
}
function Test-Trade($cards,[int]$x) {
    $losses=@(Get-Losses $cards $x); $friendly=@($losses|Where-Object {!$_.opponent}); $enemy=@($losses|Where-Object opponent)
    $friendlyValue=($friendly|Measure-Object value -Sum).Sum; $enemyValue=($enemy|Measure-Object value -Sum).Sum
    return ($enemy.Count -ge $friendly.Count+2 -and $enemyValue -gt $friendlyValue)
}
$runs=@(); $witnesses=@()
foreach($mode in @('ordinary','indestructible')) { foreach($profileMode in @('on','off')) { foreach($seed in @(96561,96562)) {
    $path=Join-Path $PSScriptRoot "target/card-use/$mode-$profileMode-$seed/summary.json"
    $s=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json
    if($s.nativeExit -ne 0 -or $s.unexpectedDiagnostics.Count -or $s.mode -ne $mode -or $s.seed -ne $seed -or $s.profile -ne $profileMode) { throw 'Probe failed or mislabeled' }
    foreach($f in $s.files) { if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256) { throw "Changed probe evidence: $($f.path)" } }
    if(@($s.files|Where-Object {$_.path -like '*candidate.jar' -and $_.sha256 -eq $gate.candidateSha256}).Count -ne 1) { throw 'Native/probe candidate mismatch' }
    $lines=@(Get-Content -LiteralPath (Join-Path (Split-Path $path) 'probe.log')); $payments=@()
    for($i=0;$i -lt $lines.Count;$i++) {
        if($lines[$i] -match '^Add To Stack: DelugeProbe-0 cast Toxic Deluge') {
            $j=$i-1
            if($lines[$j] -notmatch '^Life: Life: DelugeProbe-0 (\d+) > (\d+)$') { throw 'Native life cost log missing' }
            $oldLife=[int]$Matches[1]; $newLife=[int]$Matches[2]; $lifeLine=$lines[$j]; $j--
            $mana=@(); for(;$j -ge 0 -and $lines[$j] -match '^Mana: ';$j--) { $mana+=$lines[$j] }
            if($mana.Count -ne 3 -or @($mana|Where-Object {$_ -notmatch '^Mana: Swamp .*Add \{B\}'}).Count) { throw 'Native 2B payment missing' }
            $payments+=@{mana=$mana;lifeLine=$lifeLine;oldLife=$oldLife;newLife=$newLife;x=$oldLife-$newLife}
        }
    }
    $pending=@{}; $casts=@(); $lastLife=$null; $paymentIndex=0
    foreach($row in $s.observations) {
        if($row.stage -eq 'life') { $lastLife=$row; continue }
        $key=[string]$row.abilityId
        if($row.stage -eq 'stacked') {
            if($row.player -ne 'DelugeProbe-0' -or !$row.spell -or $row.trigger -or $row.x -le 0 -or $row.lifePaid -ne $row.x -or $row.life -lt 5) { throw 'Wrong actor, life X or safety reserve' }
            if(!$lastLife -or $lastLife.newLife -ne $row.life -or $lastLife.oldLife-$lastLife.newLife -ne $row.x) { throw 'No matching native life payment event' }
            $payment=$payments[$paymentIndex]; $paymentIndex++
            if(!$payment -or $payment.x -ne $row.x -or $payment.newLife -ne $row.life -or $payment.oldLife -ne $lastLife.oldLife) { throw 'Native cost log and observer payment disagree' }
            if(!(Test-Trade $row.creatures $row.x)) { throw 'Cast does not satisfy multiplayer count and value rules' }
            for($x=1;$x -lt $row.x;$x++) { if(Test-Trade $row.creatures $x) { throw 'A smaller X already qualified' } }
            if($pending.ContainsKey($key)) { throw 'Duplicate stack event' }
            $pending[$key]=@{before=$row;lifePayment=$lastLife;deaths=@();resolved=$null}; continue
        }
        $entry=$pending[$key]; if(!$entry) { throw 'Unpaired observed event' }
        $before=$entry.before
        if($row.stage -eq 'resolved') {
            if($row.fizzled -or $row.sourceId -ne $before.sourceId -or $row.life -ne $before.life) { throw 'Native resolution failed or life charged twice' }
            foreach($c in $before.creatures) {
                $after=@($row.creatures|Where-Object id -eq $c.id)
                if($after.Count -ne 1 -or $after[0].power -ne $c.power-$before.x -or $after[0].toughness -ne $c.toughness-$before.x) { throw 'Native global -X/-X not applied' }
            }
            $entry.resolved=$row; continue
        }
        if($row.stage -eq 'death') { if(!$entry.resolved) { throw 'Death before resolution' }; $entry.deaths+=@($row); continue }
        if($row.stage -ne 'settled' -or !$entry.resolved) { throw 'Unexpected or premature settled event' }
        $expected=@(Get-Losses $before.creatures $before.x)
        if($entry.deaths.Count -ne $expected.Count -or @($entry.deaths.cardId|Sort-Object -Unique).Count -ne $expected.Count) { throw 'Wrong native death count' }
        foreach($c in $before.creatures) {
            $died=@($entry.deaths|Where-Object cardId -eq $c.id).Count -eq 1
            $shouldDie=@($expected|Where-Object id -eq $c.id).Count -eq 1
            if($died -ne $shouldDie -or (@($row.creatures|Where-Object id -eq $c.id).Count -eq 1) -eq $shouldDie) { throw 'Actual state-based deaths/survivors differ from oracle' }
        }
        $casts+=@{mode=$mode;seed=$seed;x=$before.x;lifePayment=$entry.lifePayment;before=$before;resolved=$entry.resolved;deaths=$entry.deaths;after=$row;
            opposingDeaths=@($expected|Where-Object opponent).Count;friendlyDeaths=@($expected|Where-Object {!$_.opponent}).Count;indestructibleDeaths=@($expected|Where-Object indestructible).Count}
        $pending.Remove($key)
    }
    if($profileMode -eq 'off' -and ($casts.Count -or $pending.Count -or $payments.Count -or @($s.observations|Where-Object stage -ne 'life').Count)) { throw 'Off control used Toxic Deluge' }
    if($profileMode -eq 'on' -and !$casts.Count) { throw 'No paid/resolved/settled cast witness' }
    if($mode -eq 'indestructible' -and $profileMode -eq 'on' -and ($casts|Measure-Object indestructibleDeaths -Sum).Sum -lt 2) { throw 'No native indestructible casualty witness' }
    if($payments.Count -ne $casts.Count+$pending.Count) { throw 'Mana payments and cast observations disagree' }
    if(!$s.clockReached -and $pending.Count) { throw 'Unsettled cast without clock stop' }
    $runs+=@{mode=$mode;profile=$profileMode;seed=$seed;casts=$casts.Count;xs=@($casts.x);lifePaid=($casts|Measure-Object x -Sum).Sum;
        opposingDeaths=($casts|Measure-Object opposingDeaths -Sum).Sum;friendlyDeaths=($casts|Measure-Object friendlyDeaths -Sum).Sum;indestructibleDeaths=($casts|Measure-Object indestructibleDeaths -Sum).Sum;
        clockReached=$s.clockReached;pendingAtClockStop=$pending.Count;lateObserverEventsExcluded=$s.lateObserverEventsExcluded;diagnostics=$s.diagnostics;payments=$payments;summary=$path;sha256=(Get-FileHash $path).Hash}
    $witnesses+=@{mode=$mode;profile=$profileMode;seed=$seed;casts=$casts}
} } }
$inputs=@($gate.files)+@('ToxicDelugeProbe.java','Run-CardUseProbe.ps1','Check-CardUseEvidence.ps1'|ForEach-Object {$p=Join-Path $PSScriptRoot $_; @{path=$p;sha256=(Get-FileHash $p).Hash}})
@{date='2026-10-17';engine=$gate.engine;candidateSha256=$gate.candidateSha256;nativeResults=$gate.results;inputs=$inputs;runs=$runs;witnesses=$witnesses;
 scope='Original-script autonomous life-paid X and state-based casualties; custom decks and bounded clocks, no completion/winner/strength claim'}|ConvertTo-Json -Depth 20|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs|ForEach-Object {Write-Output "$($_.mode)/$($_.profile)/$($_.seed): $($_.casts) settled casts, X=$($_.xs -join ','), $($_.opposingDeaths) enemy vs $($_.friendlyDeaths) friendly deaths"}
Write-Output 'Card-use evidence PASS'
