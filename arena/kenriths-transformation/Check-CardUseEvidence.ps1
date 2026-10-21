$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label){if(!$ok){throw $label};$script:checks++;Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 29 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 2366) 'complete native gate on 1.0.25'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'native candidate hash'
foreach($f in $gate.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input $($f.path)"}
$runs=@()
foreach($scenario in @('threats','utility')){foreach($profile in @('on','off')){foreach($seed in @(102011,102012)){
    $label="$scenario-$profile-$seed"; $dir=Join-Path $PSScriptRoot "target/card-use/$label"
    $path=Join-Path $dir 'summary.json';$s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.engine -eq $gate.engine -and $s.candidateSha256 -eq $gate.candidateSha256) "$label same tested engine"
    Check ($s.scenario -eq $scenario -and $s.profile -eq $profile -and $s.seed -eq $seed) "$label requested run"
    Check ($s.nativeExit -eq 0 -and !$s.unexpectedDiagnostics.Count) "$label native execution"
    foreach($f in $s.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label artifact $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $lines=@(Get-Content (Join-Path $dir 'probe.log'));$rows=@();$cutoff=$false
    foreach($line in $lines){if($line -eq 'TRANSFORM_PROBE_CLOCK_STOP' -or $line.StartsWith('TRANSFORM_PROBE_DONE')){$cutoff=$true};if(!$cutoff -and $line.StartsWith('TRANSFORM_WITNESS ')){$rows+=($line.Substring(18)|ConvertFrom-Json)}}
    Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch "Couldn't add to stack" -Quiet)) "$label no failed native stack insertion"
    $payments=0
    for($i=0;$i -lt $lines.Count;$i++){
        if($lines[$i] -match "^Add To Stack: TransformationProbe-0 cast Kenrith's Transformation targeting "){
            $mana=@();for($j=$i-1;$j -ge 0 -and $lines[$j] -match '^Mana: ';$j--){$mana+=$lines[$j]}
            Check ($mana.Count -eq 2 -and !@($mana|Where-Object {$_ -notmatch '^Mana: Forest .*Add \{G\}'}).Count) "$label native 1G payment"
            $payments++
        }
    }
    $casts=@($rows|Where-Object {$_.stage -eq 'stacked' -and $_.spell});$resolved=@($rows|Where-Object {$_.stage -eq 'resolved' -and $_.spell});$draws=@($rows|Where-Object {$_.stage -eq 'resolved' -and $_.api -eq 'Draw'});
    if($profile -eq 'off'){Check ($rows.Count -eq 0 -and $payments -eq 0) "$label default prohibition retained"}
    else {
        Check ($casts.Count -gt 0 -and $resolved.Count -gt 0 -and $draws.Count -gt 0) "$label natural cast attachment and draw"
        Check ($payments -ge $casts.Count) "$label all casts paid natively"
        $seen=@{}
        foreach($r in $resolved){
            $b=@($casts|Where-Object witnessId -eq $r.witnessId)
            Check ($b.Count -eq 1 -and $r.sourceId -eq $b[0].sourceId -and !$r.fizzled) "$label paired successful Aura resolution"
            $b=$b[0];Check ($b.targets.Count -eq 1 -and $b.player -eq 'TransformationProbe-0' -and !$b.trigger) "$label native actor and spell target"
            Check ($b.librarySize -ge 5 -and $r.librarySize -eq $b.librarySize -and $r.handSize -eq $b.handSize -and $r.life -eq $b.life) "$label Aura itself does not draw before entry trigger"
            $old=$b.targets[0];$t=$r.targets[0]
            Check ($old.controller -ne $b.player -and $old.creature -and !$seen.ContainsKey($old.id)) "$label opposing unrepeated target"
            $seen[$old.id]=$true
            Check ($r.auraOnBattlefield -and $r.attachedTo -eq $old.id -and $t.id -eq $old.id) "$label actual native attachment"
            Check ($t.basePower -eq 3 -and $t.baseToughness -eq 3 -and $t.power -eq 3 -and $t.toughness -eq 3 -and $t.elk -and $t.green -and !$t.artifact) "$label actual original 3/3 green Elk"
            Check ($t.abilities -eq 0 -and $t.manaAbilities -eq 0 -and !$t.indestructible) "$label original ability removal"
            Check ($old.power -ge $t.power -and $old.toughness -ge $t.toughness) "$label no combat-stat upgrade"
            if($scenario -eq 'utility'){Check ($old.name -eq 'Llanowar Tribe' -and $old.manaAbilities -gt 0) "$label same-sized mana-ability target"}
            else {Check ($old.power -ge 5) "$label substantial combat threat"}
            $d=@($draws|Where-Object sourceId -eq $r.sourceId)
            if(!$d.Count -and $s.clockReached){continue}
            Check ($d.Count -eq 1 -and !$d[0].fizzled -and $null -ne $d[0].beforeDraw) "$label matched original entry draw"
            $d=$d[0];$before=$d.beforeDraw
            Check ($d.trigger -and !$d.spell -and $d.player -eq $r.player) "$label original draw trigger belongs to Aura controller"
            Check ($before.witnessId -eq $r.witnessId -and $d.librarySize -eq $before.librarySize-1 -and $d.handSize -eq $before.handSize+1) "$label native one-card draw consequence"
            Check ($d.librarySize -ge 4 -and $d.life -eq $before.life) "$label library reserve and life preserved"
        }
    }
    $runs+=@{scenario=$scenario;profile=$profile;seed=$seed;casts=$casts.Count;attachments=$resolved.Count;draws=$draws.Count;payments=$payments;targets=@($casts|ForEach-Object {$_.targets[0].name});clockReached=$s.clockReached;diagnostics=$s.diagnostics;lateObserverEventsExcluded=(@($lines|Where-Object {$_.StartsWith('TRANSFORM_WITNESS ')}).Count-$rows.Count);summary=$path;sha256=(Get-FileHash $path).Hash}
}}}
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$runs;scope='Autonomous native four-player Constructed custom decks; card-use evidence only, no completion/winner/strength claim'}|ConvertTo-Json -Depth 12|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs|ForEach-Object {Write-Output "$($_.scenario)/$($_.profile)/$($_.seed): $($_.casts) casts, $($_.attachments) attachments, $($_.draws) draws"}
Write-Output "DONE $checks Kenrith Transformation card-use checks"


