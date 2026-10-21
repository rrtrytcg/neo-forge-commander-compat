$ErrorActionPreference='Stop'
$checks=0
function Check([bool]$ok,[string]$label) {if(!$ok) {throw $label};$script:checks++;Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 27 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 2090) 'complete native gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'native candidate hash'
foreach($f in $gate.files) {Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input hash $($f.path)"}
$runs=@()
foreach($run in @(@{mode='on';seed=100011},@{mode='on';seed=100012},@{mode='off';seed=100011})) {
    $label="commander-$($run.mode)-$($run.seed)";$dir=Join-Path $PSScriptRoot "target/card-use/$label";$path=Join-Path $dir 'summary.json'
    $s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.nativeExit -eq 0 -and @($s.unexpectedDiagnostics).Count -eq 0) "$label native execution"
    Check ($s.profile -eq $run.mode -and $s.seed -eq $run.seed -and $s.scenario -eq 'commander') "$label requested run"
    Check ($s.engine -eq $gate.engine -and $s.candidateSha256 -eq $gate.candidateSha256) "$label correct engine"
    foreach($f in $s.files) {Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label input hash $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $lines=Get-Content (Join-Path $dir 'probe.log')
    Check (($lines -join "`n") -match 'CARD_PROBE Commander') "$label native Commander rules"
    $rows=@($lines|Where-Object {$_.StartsWith('CARD_WITNESS ')}|ForEach-Object {$_.Substring(13)|ConvertFrom-Json})
    $beacons=@($rows|Where-Object {$_.kind -eq 'cast' -and $_.source.name -eq 'Command Beacon'})
    $returns=@($rows|Where-Object {$_.kind -eq 'resolved' -and $_.source.name -eq 'Command Beacon'})
    $handCasts=0
    Check (@($rows|Where-Object {$_.kind -eq 'cast' -and $_.spell -and $_.player -eq 'Beacon-0' -and $_.from -eq 'Command'}).Count -ge 2) "$label actor naturally recasts commander from command zone"
    if($run.mode -eq 'off') {Check ($beacons.Count -eq 0 -and $returns.Count -eq 0) "$label default blocks Beacon return ability"}
    else {
        Check ($beacons.Count -gt 0 -and $returns.Count -eq $beacons.Count) "$label actual activations and matched returns"
        foreach($b in $beacons) {
            Check (!$b.spell -and $b.api -eq 'ChangeZone' -and $b.player -eq 'Beacon-0' -and $b.turnPlayer -eq $b.player -and $b.phase -in @('MAIN1','MAIN2')) "$label own-main native activation"
            $before=@($b.players|Where-Object player -eq $b.player)[0]
            Check (@($before.lands).Count -ge 4) "$label at least four lands survive sacrifice"
            Check (@($before.graveyard|Where-Object {$_.id -eq $b.source.id -and $_.name -eq 'Command Beacon'}).Count -eq 1) "$label exact Beacon paid as native sacrifice"
            $r=@($returns|Where-Object abilityId -eq $b.abilityId)
            Check ($r.Count -eq 1 -and !$r[0].fizzled -and $r[0].before.abilityId -eq $b.abilityId) "$label matching native ability resolution"
            $after=@($r[0].players|Where-Object player -eq $b.player)[0]
            $returned=@($after.hand|Where-Object {$_.commander -and $_.id -in @($before.command.id)})
            Check ($returned.Count -eq 1 -and $returned[0].commandCasts -ge 2) "$label owned commander with four or more tax returned to hand"
            $c=$returned[0]
            Check (@($after.command|Where-Object id -eq $c.id).Count -eq 0) "$label exact commander leaves command zone"
            $cast=@($rows|Where-Object {$_.kind -eq 'cast' -and $_.spell -and $_.source.id -eq $c.id -and $_.player -eq $b.player -and $_.from -eq 'Hand' -and $_.turn -eq $b.turn})
            Check ($cast.Count -eq 1) "$label returned commander naturally cast from hand this turn"
            Check ($cast[0].manaPaid -eq 1 -and $cast[0].source.commandCasts -eq $c.commandCasts) "$label native white-only hand payment preserves command cast history"
            $settled=@($rows|Where-Object {$_.kind -eq 'resolved' -and $_.abilityId -eq $cast[0].abilityId})
            Check ($settled.Count -eq 1 -and !$settled[0].fizzled) "$label hand spell resolves natively"
            $field=@($settled[0].players|Where-Object player -eq $b.player)[0].battlefield
            Check (@($field|Where-Object {$_.id -eq $c.id -and $_.commander}).Count -eq 1) "$label returned commander actually enters battlefield"
            $handCasts++
        }
    }
    $runs+=@{profile=$run.mode;seed=$run.seed;activations=$beacons.Count;returns=$returns.Count;matchedHandCasts=$handCasts;clockReached=$s.clockReached;summary=$path;sha256=(Get-FileHash $path).Hash}
}
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$runs;scope='Autonomous native Commander card use with deliberately nonstandard decks; no completion, winner or strength claim'}|ConvertTo-Json -Depth 12|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
Write-Output "DONE $checks Command Beacon card-use checks"
