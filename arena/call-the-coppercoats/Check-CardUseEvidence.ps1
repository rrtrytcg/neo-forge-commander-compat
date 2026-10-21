$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label){if(!$ok){throw $label};$script:checks++;Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 31 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 2725) 'complete native gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'native candidate hash'
foreach($f in $gate.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input $($f.path)"}
$runs=@()
foreach($scenario in @('scarce-white','plains')){foreach($profile in @('on','off')){foreach($seed in @(104011,104012)){
    $label="$scenario-$profile-$seed";$dir=Join-Path $PSScriptRoot "target/card-use/$label";$path=Join-Path $dir 'summary.json';$s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.engine -eq $gate.engine -and $s.candidateSha256 -eq $gate.candidateSha256) "$label tested engine"
    Check ($s.scenario -eq $scenario -and $s.profile -eq $profile -and $s.seed -eq $seed) "$label requested run"
    Check ($s.nativeExit -eq 0 -and !$s.unexpectedDiagnostics.Count) "$label native exit and diagnostics"
    foreach($f in $s.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label artifact $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $lines=@(Get-Content (Join-Path $dir 'probe.log'));$rows=@();$cutoff=$false
    foreach($line in $lines){if($line -eq 'COPPER_PROBE_CLOCK_STOP' -or $line.StartsWith('COPPER_PROBE_DONE')){$cutoff=$true};if(!$cutoff -and $line.StartsWith('COPPER_WITNESS ')){$rows+=($line.Substring(15)|ConvertFrom-Json)}}
    Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch "Couldn't add to stack" -Quiet)) "$label no failed native stack insertion"
    $casts=@($rows|Where-Object kind -eq 'cast');$resolved=@($rows|Where-Object kind -eq 'resolved');$single=0;$multiple=0;$triple=0;$soldiers=0;$main2=0;$endStep=0;$fizzles=0
    if($profile -eq 'off'){Check ($rows.Count -eq 0) "$label original play prohibition retained"}
    else {
        Check ($casts.Count -gt 0 -and $resolved.Count -gt 0) "$label autonomous paid cast and resolution"
        foreach($r in $resolved){
            $b=$r.before
            Check ($null -ne $b -and @($casts|Where-Object witnessId -eq $b.witnessId).Count -eq 1 -and $r.sourceId -eq $b.sourceId) "$label unique native cast pairing"
            $n=$b.targets.Count
            Check ($b.spell -and $b.player -eq 'CopperProbe-0' -and $n -ge 1 -and $n -le 3 -and @($b.targets.id|Select-Object -Unique).Count -eq $n) "$label distinct native player targets"
            Check ($b.manaPaid -eq (3+2*($n-1)) -and @($b.manaColors|Where-Object {$_ -eq 'W'}).Count -ge $n) "$label native 2W plus colored Strive payment"
            Check (!@($b.targets|Where-Object {!$_.opponent -or $_.creatures -lt 2}).Count -and ($b.targets|Measure-Object creatures -Maximum).Maximum -ge 3) "$label primary threshold and worthwhile additional opponents"
            Check ($b.otherStack.Count -eq 0 -and (($b.phase -eq 'MAIN2' -and $b.turnPlayer -eq $b.player) -or ($b.phase -eq 'END_OF_TURN' -and $b.turnPlayer -ne $b.player -and $b.nextPlayer -eq $b.player))) "$label bounded main/end-step timing"
            if($r.fizzled){$fizzles++;continue}
            $oldIds=@($b.tokens.id);$made=@($r.tokens|Where-Object {$_.id -notin $oldIds});$expected=($r.targets|Measure-Object creatures -Sum).Sum
            Check ($r.targets.Count -eq $n -and !@($r.targets|Where-Object {!$_.opponent}).Count) "$label native resolution retains legal opponents"
            Check ($made.Count -eq $expected -and $made.Count -gt 0 -and $r.tokenEvents -ge 1) "$label exact original token count and native creation event"
            Check (!@($made|Where-Object {$_.controller -ne $b.player -or $_.power -ne 1 -or $_.toughness -ne 1 -or !$_.human -or !$_.soldier -or !$_.white}).Count) "$label actual original 1/1 white Human Soldiers"
            Check ($r.life -eq $b.life -and $r.librarySize -eq $b.librarySize -and $r.handSize -eq $b.handSize) "$label native token effect does not draw or lose life"
            if($n -eq 1){$single++}else{$multiple++};if($n -eq 3){$triple++};$soldiers+=$made.Count;if($b.phase -eq 'MAIN2'){$main2++}else{$endStep++}
        }
    }
    $runs+=@{scenario=$scenario;profile=$profile;seed=$seed;casts=$casts.Count;resolved=($resolved.Count-$fizzles);single=$single;multiple=$multiple;triple=$triple;soldiers=$soldiers;main2=$main2;endStep=$endStep;fizzles=$fizzles;pendingAtStop=($casts.Count-$resolved.Count);clockReached=$s.clockReached;diagnostics=$s.diagnostics;lateObserverEventsExcluded=(@($lines|Where-Object {$_.StartsWith('COPPER_WITNESS ')}).Count-$rows.Count);summary=$path;sha256=(Get-FileHash $path).Hash}
}}}
Check (($runs|Measure-Object single -Sum).Sum -gt 0) 'autonomous single-target witness'
Check (($runs|Measure-Object multiple -Sum).Sum -gt 0) 'autonomous multi-target Strive witness'
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$runs;scope='Autonomous native four-player Commander custom decks; card-use evidence only, no completion/winner/strength claim'}|ConvertTo-Json -Depth 12|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs|ForEach-Object {Write-Output "$($_.scenario)/$($_.profile)/$($_.seed): casts=$($_.casts), resolved=$($_.resolved), single=$($_.single), multi=$($_.multiple), triple=$($_.triple), Soldiers=$($_.soldiers)"}
Write-Output "DONE $checks Coppercoats card-use checks"
