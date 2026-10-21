$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label){if(!$ok){throw $label};$script:checks++;Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 35 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 3502) 'complete native gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'native candidate hash'
foreach($f in $gate.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input $($f.path)"}
$runs=@()
foreach($scenario in @('song','cheap')){foreach($profile in @('on','off')){foreach($seed in @(108011,108012)){
    $label="$scenario-$profile-$seed";$dir=Join-Path $PSScriptRoot "target/card-use/$label";$path=Join-Path $dir 'summary.json';$s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.engine -eq $gate.engine -and $s.candidateSha256 -eq $gate.candidateSha256) "$label tested engine"
    Check ($s.scenario -eq $scenario -and $s.profile -eq $profile -and $s.seed -eq $seed) "$label requested run"
    Check ($s.nativeExit -eq 0 -and !$s.unexpectedDiagnostics.Count) "$label native exit and diagnostics"
    foreach($f in $s.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label artifact $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $lines=@(Get-Content (Join-Path $dir 'probe.log'));$rows=@();$cutoff=$false
    foreach($line in $lines){if($line -eq 'SONG_PROBE_CLOCK_STOP' -or $line.StartsWith('SONG_PROBE_DONE')){$cutoff=$true};if(!$cutoff -and $line.StartsWith('SONG_WITNESS ')){$rows+=($line.Substring(13)|ConvertFrom-Json)}}
    Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch "Couldn't add to stack" -Quiet)) "$label no failed native stack insertion"
    $casts=@($rows|Where-Object kind -eq 'cast');$resolved=@($rows|Where-Object kind -eq 'resolved');$drawCasts=@($rows|Where-Object kind -eq 'draw-cast');$draws=@($rows|Where-Object kind -eq 'draw');$fizzles=0;$countered=@();$recipients=@()
    foreach($r in $resolved){
        $b=$r.before;$t=$b.target
        Check ($null -ne $b -and @($casts|Where-Object witnessId -eq $b.witnessId).Count -eq 1 -and $r.sourceId -eq $b.sourceId -and $r.abilityId -eq $b.abilityId) "$label unique Song cast pairing"
        if($r.fizzled){$fizzles++;continue}
        Check ($b.name -eq 'Swan Song' -and $b.spell -and $b.player -eq 'SongProbe-0' -and $b.mana.Count -eq 1 -and $b.mana[0] -eq 'U') "$label original Song and exact native blue payment"
        Check ($null -ne $t -and $t.controller -ne $b.player -and $t.counterable -and $b.stack[0] -eq $b.abilityId -and $b.stack[1] -eq $t.abilityId) "$label counterable opposing spell was immediately below Song"
        Check (@($drawCasts|Where-Object {$_.abilityId -eq $t.abilityId -and $_.sourceId -eq $t.sourceId -and $_.player -eq $t.controller}).Count -eq 1) "$label target was originally cast natively"
        if($profile -eq 'on'){Check ($scenario -eq 'song' -and $t.manaValue -ge 4) "$label opt-in value threshold witnessed"}
        else{Check ($t.manaValue -eq $(if($scenario -eq 'cheap'){3}else{if($t.name -eq 'Tidings'){5}else{4}})) "$label original native off-control target value"}
        Check ($r.target.abilityId -eq $t.abilityId -and $r.target.inGraveyard -and !$r.target.resolved -and $t.abilityId -notin $r.stack -and !@($draws|Where-Object abilityId -eq $t.abilityId).Count) "$label target countered to graveyard without resolving"
        foreach($p in $b.players){
            $after=@($r.players|Where-Object player -eq $p.player)[0]
            Check ($after.librarySize -eq $p.librarySize -and $after.life -eq $p.life -and ($after.handIds -join ',') -eq ($p.handIds -join ',')) "$label counter prevents draw and preserves other resources for $($p.player)"
            $new=@($after.birds|Where-Object {$_.id -notin @($p.birds|ForEach-Object id)})
            $expected=if($p.player -eq $t.controller){1}else{0}
            Check ($new.Count -eq $expected -and $after.birds.Count -eq $p.birds.Count+$expected) "$label exact Bird recipient $($p.player)"
            foreach($bird in $new){Check ($bird.owner -eq $t.controller -and $bird.controller -eq $t.controller -and $bird.power -eq 2 -and $bird.toughness -eq 2 -and $bird.blue -and $bird.flying -and !$bird.haste -and !$bird.tapped) "$label original owned blue 2/2 flying Bird"}
        }
        Check (@($rows|Where-Object {$_.kind -eq 'destination' -and $_.sourceId -eq $b.sourceId -and $_.witnessId -eq $b.witnessId -and $_.from -eq 'Stack' -and $_.to -eq 'Graveyard'}).Count -eq 1) "$label Song finishes in graveyard"
        $countered+=$t.abilityId;$recipients+=$t.controller
    }
    foreach($r in $draws){
        $b=$r.before;$p=@($b.players|Where-Object player -eq $b.player)[0];$after=@($r.players|Where-Object player -eq $b.player)[0]
        $n=@{'Tidings'=4;'Concentrate'=3;'Divination'=2;'Counsel of the Soratami'=2}[$b.name]
        Check ($null -ne $b -and @($drawCasts|Where-Object witnessId -eq $b.witnessId).Count -eq 1 -and !$r.fizzled -and $r.abilityId -eq $b.abilityId -and $n -gt 0) "$label original draw pairing"
        Check ($after.librarySize -eq $p.librarySize-$n -and $after.handIds.Count -eq $p.handIds.Count+$n -and $after.life -eq $p.life) "$label uncountered original draw resolves exactly"
    }
    if($scenario -eq 'cheap' -and $profile -eq 'on'){Check ($casts.Count -eq 0 -and $resolved.Count -eq 0) "$label opt-in holds cheap nonemergency spells"}
    $runs+=@{scenario=$scenario;profile=$profile;seed=$seed;casts=$casts.Count;resolved=($resolved.Count-$fizzles);birds=$countered.Count;recipientPlayers=@($recipients|Sort-Object -Unique);originalDrawCasts=$drawCasts.Count;originalDrawsResolved=$draws.Count;fizzles=$fizzles;pendingAtStop=($casts.Count-$resolved.Count);pendingDraws=($drawCasts.Count-$draws.Count-$countered.Count);clockReached=$s.clockReached;diagnostics=$s.diagnostics;lateObserverEventsExcluded=(@($lines|Where-Object {$_.StartsWith('SONG_WITNESS ')}).Count-$rows.Count);summary=$path;sha256=(Get-FileHash $path).Hash}
}}}
foreach($mode in @('on','off')){Check (($runs|Where-Object {$_.scenario -eq 'song' -and $_.profile -eq $mode}|Measure-Object resolved -Sum).Sum -gt 0) "value scenario $mode has autonomous counter and Bird evidence"}
Check (($runs|Where-Object {$_.scenario -eq 'cheap' -and $_.profile -eq 'on'}|Measure-Object originalDrawsResolved -Sum).Sum -gt 0) 'opt-in cheap control contains actual resolved opposing draws'
Check (($runs|Where-Object {$_.scenario -eq 'cheap' -and $_.profile -eq 'off'}|Measure-Object resolved -Sum).Sum -gt 0) 'off cheap controls preserve native counter and Bird behavior'
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$runs;scope='Autonomous native four-player Commander custom decks; off controls preserve native counter-path admission quirk. Card-use evidence only, no completion/winner/strength claim'}|ConvertTo-Json -Depth 14|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs|ForEach-Object {Write-Output "$($_.scenario)/$($_.profile)/$($_.seed): casts=$($_.casts), counters/Birds=$($_.resolved), original draws=$($_.originalDrawsResolved)"}
Write-Output "DONE $checks Song card-use checks"
