$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label){if(!$ok){throw $label};$script:checks++;Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 36 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 3671) 'complete native gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'native candidate hash'
foreach($f in $gate.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input $($f.path)"}
$runs=@()
foreach($profile in @('on','off')){foreach($seed in @(109011,109012)){
    $label="signpost-$profile-$seed";$dir=Join-Path $PSScriptRoot "target/card-use/$label";$path=Join-Path $dir 'summary.json';$s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.engine -eq $gate.engine -and $s.candidateSha256 -eq $gate.candidateSha256) "$label tested engine"
    Check ($s.scenario -eq 'signpost' -and $s.profile -eq $profile -and $s.seed -eq $seed) "$label requested run"
    Check ($s.nativeExit -eq 0 -and !$s.unexpectedDiagnostics.Count) "$label native exit and diagnostics"
    foreach($f in $s.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label artifact $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $lines=@(Get-Content (Join-Path $dir 'probe.log'));$rows=@();$cutoff=$false
    foreach($line in $lines){if($line -eq 'SIGNPOST_PROBE_CLOCK_STOP' -or $line.StartsWith('SIGNPOST_PROBE_DONE')){$cutoff=$true};if(!$cutoff -and $line.StartsWith('SIGNPOST_WITNESS ')){$rows+=($line.Substring(17)|ConvertFrom-Json)}}
    Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch "Couldn't add to stack" -Quiet)) "$label no failed native stack insertion"
    $casts=@($rows|Where-Object kind -eq 'cast');$resolved=@($rows|Where-Object kind -eq 'resolved');$triggers=@($rows|Where-Object kind -eq 'redirect-cast');$redirects=@($rows|Where-Object kind -eq 'redirect');$entries=@($resolved|Where-Object {$_.name -eq 'Misleading Signpost' -and !$_.fizzled});$fallback=0;$flash=0;$manaUses=0;$reuse=0;$fizzles=0;$uses=@{}
    foreach($r in $resolved){
        $b=$r.before
        Check ($null -ne $b -and @($casts|Where-Object witnessId -eq $b.witnessId).Count -eq 1 -and $r.sourceId -eq $b.sourceId -and $r.abilityId -eq $b.abilityId) "$label unique native spell pairing"
        if($r.fizzled){$fizzles++;continue}
        Check ($b.player -eq 'SignpostProbe-0' -and $b.spell -and $r.onBattlefield) "$label original permanent spell resolves"
        $required=@{'Misleading Signpost'=3;'Storm Crow'=2;'Air Elemental'=5}[$b.name];$blue=if($b.name -eq 'Air Elemental'){2}else{1}
        Check ($required -gt 0 -and $b.mana.Count -eq $required -and @($b.mana|Where-Object color -eq 'U').Count -ge $blue) "$label exact native cost and blue requirements paid"
        if($b.name -eq 'Misleading Signpost'){
            Check ($profile -eq 'on' -and $b.stackSize -eq 1) "$label opted-in cast with previously empty stack"
            if($b.phase -eq 'END_OF_TURN'){Check ($b.turnPlayer -ne $b.player -and $b.nextPlayer -eq $b.player) "$label last-opponent end-step fallback";$fallback++}
            else{Check ($b.phase -eq 'COMBAT_DECLARE_ATTACKERS' -and $b.turnPlayer -ne $b.player -and @($b.attackers|Where-Object {$_.power -ge 3 -and $_.bandSize -eq 1 -and $_.defender.type -eq 'player' -and $_.defender.name -eq $b.player}).Count -gt 0) "$label actual incoming attacker prompts native flash";$flash++}
        }
        $paid=@($b.mana|Where-Object source -eq 'Misleading Signpost')
        Check (@($paid|Select-Object -ExpandProperty sourceId -Unique).Count -eq $paid.Count) "$label no artifact tap counted twice for one spell"
        foreach($m in $paid){
            Check ($m.color -eq 'U' -and $m.sourceId -ne $b.sourceId -and @($entries|Where-Object {$_.sourceId -eq $m.sourceId -and $_.event -lt $b.event}).Count -eq 1) "$label original previously cast Signpost produces native blue"
            Check (@($b.rocks|Where-Object {$_.id -eq $m.sourceId -and $_.controller -eq $b.player -and $_.tapped}).Count -eq 1 -and @($rows|Where-Object {$_.kind -eq 'tap' -and $_.sourceId -eq $m.sourceId -and $_.event -lt $b.event}).Count -gt 0) "$label actual artifact tap cost observed"
            $prior=$uses[[string]$m.sourceId]
            if($null -ne $prior -and $b.turn -gt $prior.turn -and @($rows|Where-Object {$_.kind -eq 'untap' -and $_.sourceId -eq $m.sourceId -and $_.phase -eq 'UNTAP' -and $_.event -gt $prior.event -and $_.event -lt $b.event}).Count -gt 0){$reuse++}
            $uses[[string]$m.sourceId]=@{turn=$b.turn;event=$b.event};$manaUses++
        }
    }
    $changed=0
    foreach($r in $redirects){
        $b=$r.before
        Check ($null -ne $b -and @($triggers|Where-Object witnessId -eq $b.witnessId).Count -eq 1 -and $r.sourceId -eq $b.sourceId -and $r.abilityId -eq $b.abilityId) "$label unique original entry-trigger pairing"
        if($r.fizzled){$fizzles++;continue}
        Check ($profile -eq 'on' -and !$b.spell -and $b.api -eq 'ChangeCombatants' -and $b.phase -eq 'COMBAT_DECLARE_ATTACKERS' -and @($entries|Where-Object {$_.sourceId -eq $b.sourceId -and $_.before.phase -eq 'COMBAT_DECLARE_ATTACKERS' -and $_.event -lt $b.event}).Count -eq 1) "$label original optional trigger belongs to native flash entry"
        $eligible=@($b.attackers|Where-Object {$_.power -ge 3 -and $_.bandSize -eq 1 -and (($_.defender.name -eq $b.player -and $_.defender.type -eq 'player') -or ($_.defender.type -eq 'planeswalker' -and $_.defender.controller -eq $b.player))}|Sort-Object @{Expression='power';Descending=$true},id)
        Check ($eligible.Count -gt 0 -and $b.targetId -eq $eligible[0].id) "$label independently ranked largest incoming attacker"
        $attacker=$eligible[0];$actor=@($b.players|Where-Object name -eq $b.player)[0]
        $options=@($b.defenders|Where-Object {$_.type -eq 'player' -and $_.name -ne $b.player -and $_.name -ne $attacker.controller -and !($_.team -ge 0 -and $actor.team -ge 0 -and $_.team -eq $actor.team)}|Sort-Object life,id)
        $after=@($r.attackers|Where-Object id -eq $b.targetId)
        Check ($options.Count -gt 0 -and $after.Count -eq 1 -and $after[0].defender.type -eq 'player' -and $after[0].defender.id -eq $options[0].id) "$label actual defender changes to lowest-life legal other opponent"
        Check ($after[0].controller -eq $attacker.controller -and $after[0].power -eq $attacker.power -and $after[0].tapped -eq $attacker.tapped -and $after[0].bandSize -eq 1 -and $r.onBattlefield) "$label attacker and original artifact remain in native combat/battlefield"
        foreach($other in @($b.attackers|Where-Object id -ne $b.targetId)){$now=@($r.attackers|Where-Object id -eq $other.id);Check ($now.Count -eq 1 -and $now[0].defender.id -eq $other.defender.id) "$label other attacker defender unchanged"}
        foreach($p in $b.players){Check (@($r.players|Where-Object {$_.id -eq $p.id -and $_.life -eq $p.life}).Count -eq 1) "$label redirection itself does not deal damage"}
        $changed++
    }
    if($profile -eq 'off'){Check ($entries.Count -eq 0 -and $triggers.Count -eq 0 -and $redirects.Count -eq 0 -and $manaUses -eq 0) "$label original Signpost admission retained"}
    $runs+=@{profile=$profile;seed=$seed;casts=$entries.Count;defensiveCasts=$flash;fallbackCasts=$fallback;redirects=$changed;blueManaUses=$manaUses;naturalUntapReuses=$reuse;fizzles=$fizzles;pendingSpells=($casts.Count-$resolved.Count);pendingRedirects=($triggers.Count-$redirects.Count);clockReached=$s.clockReached;diagnostics=$s.diagnostics;lateObserverEventsExcluded=(@($lines|Where-Object {$_.StartsWith('SIGNPOST_WITNESS ')}).Count-$rows.Count);summary=$path;sha256=(Get-FileHash $path).Hash}
}}
foreach($field in @('defensiveCasts','fallbackCasts','redirects','blueManaUses','naturalUntapReuses')){Check (($runs|Where-Object profile -eq 'on'|Measure-Object $field -Sum).Sum -gt 0) "autonomous $field evidence across on-profile observations"}
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$runs;scope='Autonomous native four-player Commander custom decks; original flash/entry/redirect/mana/untapping only, no completion/winner/strength claim'}|ConvertTo-Json -Depth 14|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs|ForEach-Object {Write-Output "$($_.profile)/$($_.seed): casts=$($_.casts), flash=$($_.defensiveCasts), fallback=$($_.fallbackCasts), redirects=$($_.redirects), blue=$($_.blueManaUses), natural reuse=$($_.naturalUntapReuses)"}
Write-Output "DONE $checks Signpost card-use checks"
