$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label) {if(!$ok) {throw $label}; $script:checks++; Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 26 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 2007) 'complete 1.0.25 native gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'native candidate hash'
foreach($f in $gate.files) {Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input hash $($f.path)"}
$runs=@()
foreach($run in @(@{mode='on';seed=99011},@{mode='on';seed=99012},@{mode='off';seed=99011})) {
    $label="air-$($run.mode)-$($run.seed)"; $dir=Join-Path $PSScriptRoot "target/card-use/$label"; $path=Join-Path $dir 'summary.json'
    $s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.nativeExit -eq 0 -and @($s.unexpectedDiagnostics).Count -eq 0) "$label native execution"
    Check ($s.engine -eq $gate.engine -and $s.candidateSha256 -eq $gate.candidateSha256) "$label correct engine"
    foreach($f in $s.files) {Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label input hash $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $lines=Get-Content (Join-Path $dir 'probe.log')
    $rows=@($lines|Where-Object {$_.StartsWith('CARD_WITNESS ')}|ForEach-Object {$_.Substring(13)|ConvertFrom-Json})
    $damage=@($lines|Where-Object {$_.StartsWith('COMBAT_DAMAGE ')}|ForEach-Object {$_.Substring(14)|ConvertFrom-Json})
    $casts=@($rows|Where-Object {$_.kind -eq 'cast' -and $_.spell -and $_.api -eq 'Effect'})
    $draws=@($rows|Where-Object {$_.kind -eq 'resolved' -and $_.api -eq 'Draw'})
    $cardsDrawn=0
    if($run.mode -eq 'off') {Check ($rows.Count -eq 0) "$label default still blocks Insight"}
    else {
        Check ($casts.Count -gt 0 -and $draws.Count -gt 0) "$label actual casts and delayed draws"
        foreach($group in @($casts|Group-Object combatId)) {Check ($group.Count -eq 1) "$label one Insight per combat $($group.Name)"}
        foreach($row in $casts) {
            Check ($row.phase -eq 'COMBAT_DECLARE_BLOCKERS' -and @($row.targets).Count -eq 1) "$label targets after blockers before damage"
            $t=$row.targets[0]; $p=@($row.players|Where-Object player -eq "Hunter's Insight-0")[0]
            $c=@($p.battlefield|Where-Object id -eq $t.id)[0]
            Check ($c -ne $null -and $c.attacking -and $t.unblocked -and !$c.firstStrike -and !$c.doubleStrike) "$label own unblocked single-step attacker"
            Check ($t.predicted -ge 3 -and ($p.librarySize-$t.predicted) -ge 4) "$label minimum three draws and four-card reserve"
        }
        foreach($row in $draws) {
            $b=$row.before
            Check ($b -ne $null -and $b.trigger -and $row.fizzled -eq $false) "$label original delayed trigger resolves"
            $cast=@($casts|Where-Object combatId -eq $b.combatId)
            Check ($cast.Count -eq 1) "$label draw matches this combat's single spell"
            $target=$cast[0].targets[0]
            $hits=@($damage|Where-Object {$_.combatId -eq $b.combatId -and $_.sourceId -eq $target.id})
            $amount=($hits|Measure-Object amount -Sum).Sum
            Check ($hits.Count -gt 0 -and $amount -eq $b.draw -and $amount -eq $target.predicted) "$label original trigger equals observed combat damage"
            $before=@($b.players|Where-Object player -eq "Hunter's Insight-0")[0]
            $after=@($row.players|Where-Object player -eq "Hunter's Insight-0")[0]
            Check (($before.librarySize-$after.librarySize) -eq $amount -and ($after.handSize-$before.handSize) -eq $amount) "$label native draw moves exact cards into hand"
            Check ($after.librarySize -ge 4) "$label library reserve survives actual draw"
            $cardsDrawn+=$amount
        }
    }
    $runs+=@{profile=$run.mode;seed=$run.seed;casts=$casts.Count;resolvedDraws=$draws.Count;cardsDrawn=$cardsDrawn;clockReached=$s.clockReached;summary=$path;sha256=(Get-FileHash $path).Hash}
}
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$runs;scope='Autonomous custom-deck cast/combat/draw evidence only; no winner, game-completion or strength claim'}|ConvertTo-Json -Depth 12|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
Write-Output "DONE $checks Hunter's Insight card-use checks"
