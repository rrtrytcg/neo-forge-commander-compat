$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label){if(!$ok){throw $label};$script:checks++;Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 30 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 2513) 'complete native gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'native candidate hash'
foreach($f in $gate.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input $($f.path)"}
$runs=@()
foreach($scenario in @('proactive','rescue')){foreach($profile in @('on','off')){foreach($seed in @(103011,103012)){
    $label="$scenario-$profile-$seed";$dir=Join-Path $PSScriptRoot "target/card-use/$label";$path=Join-Path $dir 'summary.json';$s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.engine -eq $gate.engine -and $s.candidateSha256 -eq $gate.candidateSha256) "$label tested engine"
    Check ($s.scenario -eq $scenario -and $s.profile -eq $profile -and $s.seed -eq $seed) "$label requested run"
    Check ($s.nativeExit -eq 0 -and !$s.unexpectedDiagnostics.Count) "$label native exit and diagnostics"
    foreach($f in $s.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label artifact $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $lines=@(Get-Content (Join-Path $dir 'probe.log'));$rows=@();$cutoff=$false
    foreach($line in $lines){if($line -eq 'WARD_PROBE_CLOCK_STOP' -or $line.StartsWith('WARD_PROBE_DONE')){$cutoff=$true};if(!$cutoff -and $line.StartsWith('WARD_WITNESS ')){$rows+=($line.Substring(13)|ConvertFrom-Json)}}
    Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch "Couldn't add to stack" -Quiet)) "$label no failed native stack insertion"
    $casts=@($rows|Where-Object kind -eq 'cast');$resolved=@($rows|Where-Object kind -eq 'resolved');$settled=@($rows|Where-Object kind -eq 'settled');$proactive=0;$reactive=0;$matchedSettled=0;$targets=@()
    if($profile -eq 'off'){Check ($rows.Count -eq 0) "$label original play prohibition retained"}
    else {
        Check ($casts.Count -gt 0 -and $resolved.Count -gt 0) "$label natural paid cast and resolution"
        foreach($r in $resolved){
            $b=$r.before
            Check ($null -ne $b -and @($casts|Where-Object witnessId -eq $b.witnessId).Count -eq 1 -and $b.sourceId -eq $r.sourceId) "$label uniquely paired native resolution"
            Check ($b.spell -and $b.player -eq 'WardProbe-0' -and $b.manaPaid -eq 3 -and $b.targets.Count -eq 1) "$label actual native 2W payment and target"
            if($r.fizzled){Check (!$r.auraOnBattlefield) "$label native fizzle does not attach Aura";continue}
            Check ($r.targets.Count -eq 1) "$label resolved target retained"
            $t=$b.targets[0];$after=$r.targets[0];$targets+=$t.name
            Check ($t.controller -eq $b.player -and $t.zone -eq 'Battlefield' -and !$t.indestructible -and !$t.ward) "$label unprotected owned creature"
            Check ($after.id -eq $t.id -and $after.incarnation -eq $t.incarnation -and $r.attachedTo -eq $t.id -and $r.auraOnBattlefield -and $after.indestructible -and $after.ward) "$label actual original attachment and indestructibility"
            Check ($after.power -eq $t.power -and $after.toughness -eq $t.toughness -and $after.commander -eq $t.commander) "$label native creature stats and commander identity preserved"
            if($b.threats.Count -eq 0){
                Check ($b.phase -eq 'MAIN2' -and $b.turnPlayer -eq $b.player) "$label proactive second-main timing";$proactive++
            }else{
                $hit=@($b.threats|Where-Object {($_.api -in @('Destroy','DealDamage') -and $t.id -in $_.targets) -or $_.api -in @('DestroyAll','DamageAll')})
                Check ($t.commander -and $hit.Count -gt 0) "$label commander-only flash responds to public threat";$reactive++
                $done=@($rows|Where-Object {$_.kind -eq 'threat-resolved' -and $_.witnessId -eq $b.witnessId});$stable=@($settled|Where-Object witnessId -eq $b.witnessId)
                if(!$done.Count -and $s.clockReached){continue}
                Check ($done.Count -ge 1 -and !@($done|Where-Object fizzled).Count) "$label protected threat actually resolves"
                foreach($d in $done){Check ($d.victim.id -eq $t.id -and $d.victim.zone -eq 'Battlefield' -and $d.victim.indestructible) "$label protected commander survives native threat effect"}
                if(!$stable.Count -and $s.clockReached){continue}
                Check ($stable.Count -ge 1) "$label later native priority or phase witness"
                foreach($d in $stable){Check ($d.victim.id -eq $t.id -and $d.victim.incarnation -eq $d.originalIncarnation -and $d.victim.zone -eq 'Battlefield') "$label same commander survives state-based handling";$matchedSettled++}
            }
        }
    }
    $runs+=@{scenario=$scenario;profile=$profile;seed=$seed;casts=$casts.Count;resolved=@($resolved|Where-Object {!$_.fizzled}).Count;fizzles=@($resolved|Where-Object fizzled).Count;pendingAtStop=($casts.Count-$resolved.Count);proactive=$proactive;reactive=$reactive;settledRescues=$matchedSettled;targets=$targets;clockReached=$s.clockReached;diagnostics=$s.diagnostics;lateObserverEventsExcluded=(@($lines|Where-Object {$_.StartsWith('WARD_WITNESS ')}).Count-$rows.Count);summary=$path;sha256=(Get-FileHash $path).Hash}
}}}
Check (($runs|Measure-Object proactive -Sum).Sum -gt 0) 'autonomous proactive use witness'
Check (($runs|Measure-Object reactive -Sum).Sum -gt 0 -and ($runs|Measure-Object settledRescues -Sum).Sum -gt 0) 'autonomous native commander flash rescue with settled survivor'
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$runs;scope='Autonomous native four-player Commander custom decks; card-use evidence only, no completion/winner/strength claim'}|ConvertTo-Json -Depth 12|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs|ForEach-Object {Write-Output "$($_.scenario)/$($_.profile)/$($_.seed): casts=$($_.casts), resolved=$($_.resolved), proactive=$($_.proactive), reactive=$($_.reactive), settled=$($_.settledRescues)"}
Write-Output "DONE $checks Timely Ward card-use checks"
