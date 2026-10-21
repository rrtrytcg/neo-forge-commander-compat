$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label){if(!$ok){throw $label};$script:checks++;Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 33 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 3080) 'complete native gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'native candidate hash'
foreach($f in $gate.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input $($f.path)"}
$runs=@()
foreach($profile in @('on','off')){foreach($seed in @(106011,106012)){
    $label="prism-$profile-$seed";$dir=Join-Path $PSScriptRoot "target/card-use/$label";$path=Join-Path $dir 'summary.json';$s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.engine -eq $gate.engine -and $s.candidateSha256 -eq $gate.candidateSha256) "$label tested engine"
    Check ($s.scenario -eq 'prism' -and $s.profile -eq $profile -and $s.seed -eq $seed) "$label requested run"
    Check ($s.nativeExit -eq 0 -and !$s.unexpectedDiagnostics.Count) "$label native exit and diagnostics"
    foreach($f in $s.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label artifact $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $lines=@(Get-Content (Join-Path $dir 'probe.log'));$rows=@();$cutoff=$false
    foreach($line in $lines){if($line -eq 'PRISM_PROBE_CLOCK_STOP' -or $line.StartsWith('PRISM_PROBE_DONE')){$cutoff=$true};if(!$cutoff -and $line.StartsWith('PRISM_WITNESS ')){$rows+=($line.Substring(14)|ConvertFrom-Json)}}
    Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch "Couldn't add to stack" -Quiet)) "$label no failed native stack insertion"
    $casts=@($rows|Where-Object kind -eq 'cast');$resolved=@($rows|Where-Object kind -eq 'resolved');$draws=@($rows|Where-Object kind -eq 'draw');$drawCasts=@($rows|Where-Object kind -eq 'draw-cast')
    $entries=@($resolved|Where-Object {$_.name -eq 'Prophetic Prism' -and !$_.fizzled});$filtered=0;$colors=@();$fizzles=0;$uses=@{}
    foreach($r in $resolved){
        $b=$r.before
        Check ($null -ne $b -and @($casts|Where-Object witnessId -eq $b.witnessId).Count -eq 1 -and $r.sourceId -eq $b.sourceId) "$label unique native cast pairing"
        if($r.fizzled){$fizzles++;continue}
        $mana=@($b.mana|Where-Object source -eq 'Prophetic Prism')
        if($b.name -eq 'Prophetic Prism'){
            Check ($r.onBattlefield -and $b.mana.Count -eq 2 -and $b.api -eq 'PermanentNoncreature') "$label original Prism resolves with two native mana"
            Check ($b.librarySize -ge 5 -and $r.librarySize -eq $b.librarySize -and $r.handSize -eq $b.handSize -and $r.life -eq $b.life) "$label casting preserves reserve and does not replace entry draw"
        }
        if($profile -eq 'off'){Check ($b.name -ne 'Prophetic Prism' -and $mana.Count -eq 0) "$label original Prism admission and filtering retained"}
        if(!$mana.Count){continue}
        Check ($b.player -eq 'PrismProbe-0' -and $b.spell -and $r.onBattlefield) "$label filtered spell resolves natively"
        Check (@($mana.sourceId|Select-Object -Unique).Count -eq $mana.Count) "$label no Prism tap used twice for one spell"
        $required=@{'Isamaru, Hound of Konda'='W';'Storm Crow'='U';'Walking Corpse'='B';'Goblin Piker'='R';'Grizzly Bears'='G'}[$b.name]
        if($required){Check ($required -in @($b.mana.color)) "$label original color-sensitive requirement paid"}
        foreach($m in $mana){
            Check ($m.sourceId -in @($entries.sourceId)) "$label mana source is an originally cast and resolved Prism"
            Check (@($b.prisms|Where-Object {$_.id -eq $m.sourceId -and $_.tapped}).Count -eq 1) "$label actual Prism tapped"
            Check ($m.produced -eq 'Any' -and $m.color -in @('W','U','B','R','G')) "$label actual native colored mana"
            $activation=@($b.paidAbilities|Where-Object {$_.sourceId -eq $m.sourceId -and $_.produced -eq 'Any'})
            Check ($activation.Count -eq 1 -and $activation[0].paidColors.Count -eq 1 -and $activation[0].paidColors[0] -eq 'C') "$label separate native one-mana activation payment"
            $uses[[string]$m.sourceId]=@($uses[[string]$m.sourceId])+$b.turn;$colors+=$m.color;$filtered++
        }
    }
    foreach($r in $draws){
        $b=$r.before;$entry=$b.entry
        Check ($null -ne $entry -and @($entries|Where-Object {$_.sourceId -eq $r.sourceId -and $_.before.witnessId -eq $entry.before.witnessId}).Count -eq 1) "$label draw belongs to original resolved Prism"
        Check (!$r.spell -and $r.trigger -and $r.api -eq 'Draw' -and !$r.fizzled -and $r.sourceId -eq $b.sourceId -and @($drawCasts|Where-Object abilityId -eq $r.abilityId).Count -eq 1) "$label original mandatory entry draw trigger resolves"
        Check ($r.librarySize -eq $b.librarySize-1 -and $r.handSize -eq $b.handSize+1 -and $r.librarySize -ge 4) "$label exact one-card draw and reserve"
        Check (@($r.handIds|Where-Object {$_ -notin $b.handIds}).Count -eq 1 -and !@($b.handIds|Where-Object {$_ -notin $r.handIds}).Count) "$label actual new drawn card enters hand"
        Check ($r.life -eq $b.life -and $r.onBattlefield) "$label draw does not lose life or consume Prism"
    }
    $reused=@($uses.Values|Where-Object {@($_|Where-Object {$null -ne $_}|Select-Object -Unique).Count -gt 1}).Count
    if($profile -eq 'on'){Check ($entries.Count -gt 0 -and $draws.Count -gt 0 -and $filtered -gt 0) "$label autonomous cast, entry draw and colored payment";Check ($reused -gt 0) "$label same Prism reused after turn untapping"}
    else{Check ($entries.Count -eq 0 -and $draws.Count -eq 0 -and $filtered -eq 0) "$label zero Prism use in off control"}
    $runs+=@{scenario='prism';profile=$profile;seed=$seed;prismCasts=$entries.Count;draws=$draws.Count;filteredMana=$filtered;colors=@($colors|Sort-Object -Unique);reusedPrisms=$reused;fizzles=$fizzles;pendingAtStop=($casts.Count-$resolved.Count);pendingDraws=($drawCasts.Count-$draws.Count);clockReached=$s.clockReached;diagnostics=$s.diagnostics;lateObserverEventsExcluded=(@($lines|Where-Object {$_.StartsWith('PRISM_WITNESS ')}).Count-$rows.Count);summary=$path;sha256=(Get-FileHash $path).Hash}
}}
Check (@($runs.colors|Sort-Object -Unique).Count -ge 2) 'several observed color-sensitive native payments'
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$runs;scope='Autonomous native four-player Commander custom decks; card-use evidence only, no completion/winner/strength claim'}|ConvertTo-Json -Depth 14|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs|ForEach-Object {Write-Output "$($_.profile)/$($_.seed): Prism casts=$($_.prismCasts), draws=$($_.draws), filtered=$($_.filteredMana)"}
Write-Output "DONE $checks Prism card-use checks"
