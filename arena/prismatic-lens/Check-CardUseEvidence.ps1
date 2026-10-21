$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label){if(!$ok){throw $label};$script:checks++;Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 32 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 2886) 'complete native gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'native candidate hash'
foreach($f in $gate.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input $($f.path)"}
$runs=@()
foreach($profile in @('on','off')){foreach($seed in @(105011,105012)){
    $label="lens-$profile-$seed";$dir=Join-Path $PSScriptRoot "target/card-use/$label";$path=Join-Path $dir 'summary.json';$s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.engine -eq $gate.engine -and $s.candidateSha256 -eq $gate.candidateSha256) "$label tested engine"
    Check ($s.scenario -eq 'lens' -and $s.profile -eq $profile -and $s.seed -eq $seed) "$label requested run"
    Check ($s.nativeExit -eq 0 -and !$s.unexpectedDiagnostics.Count) "$label native exit and diagnostics"
    foreach($f in $s.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label artifact $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $lines=@(Get-Content (Join-Path $dir 'probe.log'));$rows=@();$cutoff=$false
    foreach($line in $lines){if($line -eq 'LENS_PROBE_CLOCK_STOP' -or $line.StartsWith('LENS_PROBE_DONE')){$cutoff=$true};if(!$cutoff -and $line.StartsWith('LENS_WITNESS ')){$rows+=($line.Substring(13)|ConvertFrom-Json)}}
    Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch "Couldn't add to stack" -Quiet)) "$label no failed native stack insertion"
    $casts=@($rows|Where-Object kind -eq 'cast');$resolved=@($rows|Where-Object kind -eq 'resolved');$filtered=0;$free=0;$lensCasts=0;$colors=@();$fizzles=0;$uses=@{}
    foreach($r in $resolved){
        $b=$r.before
        Check ($null -ne $b -and @($casts|Where-Object witnessId -eq $b.witnessId).Count -eq 1 -and $r.sourceId -eq $b.sourceId) "$label unique native cast pairing"
        if($r.fizzled){$fizzles++;continue}
        $mana=@($b.mana|Where-Object source -eq 'Prismatic Lens')
        if($b.name -eq 'Prismatic Lens'){$lensCasts++;Check ($r.onBattlefield -and $b.mana.Count -eq 2) "$label original Lens cast resolves with two native mana"}
        if($profile -eq 'off'){Check ($b.name -ne 'Prismatic Lens' -and $mana.Count -eq 0) "$label original Lens prohibition and payment behavior retained"}
        if(!$mana.Count){continue}
        Check ($b.player -eq 'LensProbe-0' -and $b.spell -and $r.onBattlefield) "$label paid spell resolves natively"
        $required=@{'Isamaru, Hound of Konda'='W';'Storm Crow'='U';'Walking Corpse'='B';'Goblin Piker'='R';'Grizzly Bears'='G';'Matter Reshaper'='C'}[$b.name]
        if($required){Check ($required -in @($b.mana.color)) "$label original spell's color-sensitive requirement paid"}
        Check (@($mana.sourceId|Select-Object -Unique).Count -eq $mana.Count) "$label no Lens tap used twice for one spell"
        foreach($m in $mana){
            $uses[[string]$m.sourceId]=@($uses[[string]$m.sourceId])+$b.turn
            Check (@($b.lenses|Where-Object {$_.id -eq $m.sourceId -and $_.tapped}).Count -eq 1) "$label actual Lens tapped"
            if($m.produced -eq 'Any'){
                Check ($m.color -in @('W','U','B','R','G')) "$label filter pays actual colored mana"
                $activation=@($b.paidAbilities|Where-Object {$_.sourceId -eq $m.sourceId -and $_.produced -eq 'Any'})
                Check ($activation.Count -eq 1 -and $activation[0].paidColors.Count -eq 1 -and $activation[0].paidColors[0] -eq 'C') "$label original filter paid its separate one-mana activation cost"
                $colors+=$m.color;$filtered++
            }else{Check ($m.produced -eq 'C' -and $m.color -eq 'C') "$label free mode pays actual colorless mana";$free++}
        }
    }
    $reused=@($uses.Values|Where-Object {@($_|Where-Object {$null -ne $_}|Select-Object -Unique).Count -gt 1}).Count
    if($profile -eq 'on'){Check ($lensCasts -gt 0 -and ($filtered+$free) -gt 0) "$label autonomous Lens casting and payment";Check ($reused -gt 0) "$label same Lens reused after native turn untapping"}
    $runs+=@{scenario='lens';profile=$profile;seed=$seed;lensCasts=$lensCasts;resolved=$resolved.Count;filteredMana=$filtered;freeColorlessMana=$free;colors=@($colors|Sort-Object -Unique);reusedLenses=$reused;fizzles=$fizzles;pendingAtStop=($casts.Count-$resolved.Count);clockReached=$s.clockReached;diagnostics=$s.diagnostics;lateObserverEventsExcluded=(@($lines|Where-Object {$_.StartsWith('LENS_WITNESS ')}).Count-$rows.Count);summary=$path;sha256=(Get-FileHash $path).Hash}
}}
Check (@($runs.colors|Sort-Object -Unique).Count -ge 2) 'several observed color-sensitive payments'
Check (($runs|Measure-Object filteredMana -Sum).Sum -gt 0 -and ($runs|Measure-Object freeColorlessMana -Sum).Sum -gt 0) 'both native Lens payment modes observed across seeds'
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$runs;scope='Autonomous native four-player Commander custom decks; card-use evidence only, no completion/winner/strength claim'}|ConvertTo-Json -Depth 12|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
$runs|ForEach-Object {Write-Output "$($_.profile)/$($_.seed): Lens casts=$($_.lensCasts), filtered=$($_.filteredMana), colorless=$($_.freeColorlessMana)"}
Write-Output "DONE $checks Lens card-use checks"
