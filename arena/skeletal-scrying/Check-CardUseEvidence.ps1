$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label){if(!$ok){throw $label};$script:checks++;Write-Output "PASS $label"}
function Safe($c){return $c.basic -or (!$c.permanent -and ($c.instant -or $c.sorcery) -and !$c.keywords.Count)}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 34 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 3314) 'complete native gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'native candidate hash'
foreach($f in $gate.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input $($f.path)"}
$runs=@()
foreach($profile in @('on','off')){foreach($seed in @(107011,107012)){
    $label="scrying-$profile-$seed";$dir=Join-Path $PSScriptRoot "target/card-use/$label";$path=Join-Path $dir 'summary.json';$s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.engine -eq $gate.engine -and $s.candidateSha256 -eq $gate.candidateSha256) "$label tested engine"
    Check ($s.scenario -eq 'scrying' -and $s.profile -eq $profile -and $s.seed -eq $seed) "$label requested run"
    Check ($s.nativeExit -eq 0 -and !$s.unexpectedDiagnostics.Count) "$label native exit and diagnostics"
    foreach($f in $s.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label artifact $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $lines=@(Get-Content (Join-Path $dir 'probe.log'));$rows=@();$cutoff=$false
    foreach($line in $lines){if($line -eq 'SCRYING_PROBE_CLOCK_STOP' -or $line.StartsWith('SCRYING_PROBE_DONE')){$cutoff=$true};if(!$cutoff -and $line.StartsWith('SCRYING_WITNESS ')){$rows+=($line.Substring(16)|ConvertFrom-Json)}}
    Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch "Couldn't add to stack" -Quiet)) "$label no failed native stack insertion"
    $casts=@($rows|Where-Object kind -eq 'cast');$resolved=@($rows|Where-Object kind -eq 'resolved');$millStarts=@($rows|Where-Object kind -eq 'mill-start');$mills=@($rows|Where-Object kind -eq 'mill')
    $fizzles=0;$drawn=0;$basic=0;$ordinary=0;$protected=0;$xs=@();$phases=@()
    foreach($r in $resolved){
        $b=$r.before;$x=[int]$b.x
        Check ($null -ne $b -and @($casts|Where-Object witnessId -eq $b.witnessId).Count -eq 1 -and $r.sourceId -eq $b.sourceId -and $r.abilityId -eq $b.abilityId) "$label unique native cast pairing"
        if($r.fizzled){$fizzles++;continue}
        Check ($profile -eq 'on' -and $b.player -eq 'ScryingProbe-0' -and $b.name -eq 'Skeletal Scrying' -and $b.spell -and $b.api -eq 'Draw') "$label original opted-in spell resolves"
        Check ($x -in @(2,3) -and $b.otherStack -eq 0 -and $b.handSize+1 -le 3) "$label bounded X, low hand and empty stack"
        Check (($b.phase -eq 'MAIN2' -and $b.turnPlayer -eq $b.player) -or ($b.phase -eq 'END_OF_TURN' -and $b.turnPlayer -ne $b.player -and $b.nextPlayer -eq $b.player)) "$label approved native timing"
        Check ($b.librarySize -ge $x+4 -and $b.life -ge $x+5) "$label pre-cast library and life reserves"
        Check ($b.mana.Count -eq $x+1 -and 'B' -in $b.mana) "$label original XB paid with native mana"
        Check ($b.paidLKI.Count -eq $x -and $b.paidExiles.Count -eq $x -and @($b.paidExiles.id|Select-Object -Unique).Count -eq $x) "$label exact distinct native exile cost"
        Check (!@($b.paidExiles|Where-Object {!$_.inExile -or $_.owner -ne $b.player}).Count -and !@($b.paidLKI|Where-Object {!(Safe $_)}).Count) "$label native exile contains only expendable owned cards"
        Check (!@($b.paidLKI.id|Where-Object {$_ -notin $b.paidExiles.id}).Count) "$label native exile LKI matches actual cards"
        $eligible=@((@($b.graveyard)+@($b.paidLKI))|Where-Object {Safe $_}|Sort-Object @{Expression={if($_.basic){0}else{1}}},id|Select-Object -First $x)
        Check (($eligible.id -join ',') -eq ($b.paidLKI.id -join ',')) "$label independent basic-first deterministic payment"
        Check ($r.librarySize -eq $b.librarySize-$x -and $r.handSize -eq $b.handSize+$x -and $r.life -eq $b.life-$x -and $r.librarySize -ge 4 -and $r.life -ge 5) "$label exact original draw and life loss with reserves"
        Check (@($r.handIds|Where-Object {$_ -notin $b.handIds}).Count -eq $x -and !@($b.handIds|Where-Object {$_ -notin $r.handIds}).Count) "$label actual new drawn cards enter hand"
        Check (@($rows|Where-Object {$_.kind -eq 'destination' -and $_.sourceId -eq $b.sourceId -and $_.witnessId -eq $b.witnessId -and $_.from -eq 'Stack' -and $_.to -eq 'Graveyard'}).Count -eq 1) "$label original spell reaches graveyard after resolution"
        $kept=@($b.graveyard|Where-Object {!(Safe $_)})
        Check (!@($kept|Where-Object {$_.id -notin $r.graveyard.id}).Count) "$label protected graveyard cards retained"
        if($kept.Count){$protected++};$basic+=@($b.paidLKI|Where-Object basic).Count;$ordinary+=@($b.paidLKI|Where-Object {!$_.basic}).Count;$drawn+=$x;$xs+=$x;$phases+=$b.phase
    }
    foreach($r in $mills){
        $b=$r.before
        Check ($null -ne $b -and @($millStarts|Where-Object witnessId -eq $b.witnessId).Count -eq 1 -and $r.sourceId -eq $b.sourceId) "$label unique native self-mill pairing"
        Check (!$r.fizzled -and !$b.spell -and $b.api -eq 'Mill' -and $b.name -eq "Stitcher's Supplier" -and $r.librarySize -eq $b.librarySize-3 -and $r.graveyard.Count -eq $b.graveyard.Count+3 -and $r.life -eq $b.life -and $r.handSize -eq $b.handSize) "$label original native Supplier mills three"
    }
    if($profile -eq 'on'){Check ($resolved.Count-$fizzles -gt 0 -and $mills.Count -gt 0 -and $protected -gt 0) "$label autonomous self-mill, Scrying payment and protected graveyard witness"}
    else{Check ($casts.Count -eq 0 -and $resolved.Count -eq 0) "$label zero Scrying use in off control"}
    $runs+=@{scenario='scrying';profile=$profile;seed=$seed;casts=$casts.Count;resolved=($resolved.Count-$fizzles);drawn=$drawn;lifeLost=$drawn;basicExiles=$basic;ordinaryExiles=$ordinary;x2=@($xs|Where-Object {$_ -eq 2}).Count;x3=@($xs|Where-Object {$_ -eq 3}).Count;phases=@($phases|Sort-Object -Unique);nativeMills=$mills.Count;protectedGraveyardWitnesses=$protected;fizzles=$fizzles;pendingAtStop=($casts.Count-$resolved.Count);pendingMills=($millStarts.Count-$mills.Count);clockReached=$s.clockReached;diagnostics=$s.diagnostics;lateObserverEventsExcluded=(@($lines|Where-Object {$_.StartsWith('SCRYING_WITNESS ')}).Count-$rows.Count);summary=$path;sha256=(Get-FileHash $path).Hash}
}}
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$runs;scope='Autonomous native four-player Commander custom decks; card-use evidence only, no completion/winner/strength claim'}|ConvertTo-Json -Depth 14|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs|ForEach-Object {Write-Output "$($_.profile)/$($_.seed): casts=$($_.casts), resolved=$($_.resolved), drawn=$($_.drawn), mills=$($_.nativeMills)"}
Write-Output "DONE $checks Scrying card-use checks"
