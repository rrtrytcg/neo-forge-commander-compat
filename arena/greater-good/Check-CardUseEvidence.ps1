$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label) {if(!$ok) {throw $label}; $script:checks++; Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 25 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 1935) 'complete native 1.0.25 gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'tested candidate hash'
foreach($f in $gate.files) {Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input hash $($f.path)"}
$runs=@(@{scenario='removal';mode='on';seed=98011},@{scenario='removal';mode='on';seed=98012},@{scenario='combat';mode='on';seed=98021},@{scenario='removal';mode='off';seed=98011},@{scenario='combat';mode='off';seed=98021})
$results=@()
foreach($run in $runs) {
    $label="$($run.scenario)-$($run.mode)-$($run.seed)"; $dir=Join-Path $PSScriptRoot "target/card-use/$label"
    $path=Join-Path $dir 'summary.json'; $s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.nativeExit -eq 0 -and @($s.unexpectedDiagnostics).Count -eq 0) "$label native exit and diagnostics"
    Check ($s.candidateSha256 -eq $gate.candidateSha256 -and $s.engine -eq $gate.engine) "$label verified native engine"
    foreach($f in $s.files) {Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label input hash $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual probe candidate"
    $rows=@(Get-Content (Join-Path $dir 'probe.log')|Where-Object {$_.StartsWith('CARD_WITNESS ')}|ForEach-Object {$_.Substring(13)|ConvertFrom-Json})
    $cast=@($rows|Where-Object {$_.kind -eq 'cast' -and $_.spell})
    $resolved=@($rows|Where-Object {$_.kind -eq 'resolved' -and $_.api -eq 'Draw'})
    $removal=0; $combat=0
    if($run.mode -eq 'off') {Check ($rows.Count -eq 0) "$label default retains original prohibition"}
    else {
        Check ($cast.Count -gt 0 -and $resolved.Count -gt 0) "$label casts enchantment and resolves activation"
        Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch "Couldn't add to stack" -Quiet)) "$label no failed native stack insertion"
        foreach($row in $resolved) {
            $b=$row.before
            Check ($b -ne $null -and !$b.spell -and $row.fizzled -eq $false) "$label activation has matched before event and no fizzle"
            $before=@($b.players|Where-Object player -eq 'Greater Good-0')[0]
            $after=@($row.players|Where-Object player -eq 'Greater Good-0')[0]
            $power=[int]$b.draw
            Check (@($b.sacrificed).Count -eq 1 -and $power -ge 4) "$label single sacrifice and at least four predicted draws"
            $sac=$b.sacrificed[0]
            Check ($sac.power -eq $power -and $sac.name -in $before.graveyard) "$label actual paid creature matches native LKI power"
            Check (($before.librarySize-$power) -ge 4) "$label four-card library reserve before draw"
            Check (($before.librarySize-$after.librarySize) -eq $power) "$label native draws equal sacrificed power"
            Check (($after.handSize-$before.handSize) -eq ($power-3)) "$label native draw then discard three"
            # Opponents can resolve another Murder above this activation. Account for
            # those observed battlefield losses separately from the three discards.
            $afterIds=@($after.battlefield|ForEach-Object id)
            $lost=@($before.battlefield|Where-Object {$_.id -notin $afterIds})
            foreach($group in @($lost|Group-Object name)) {
                $beforeCount=@($before.graveyard|Where-Object {$_ -eq $group.Name}).Count
                $afterCount=@($after.graveyard|Where-Object {$_ -eq $group.Name}).Count
                Check (($afterCount-$beforeCount) -ge $group.Count) "$label intervening battlefield loss reaches graveyard: $($group.Name)"
            }
            Check (($after.graveyard.Count-$before.graveyard.Count) -eq (3+$lost.Count)) "$label three real discards plus observed intervening permanent deaths"
            if('Murder' -in $b.stack -or 'Doom Blade' -in $b.stack) {$removal++}
            if($b.phase -eq 'COMBAT_DECLARE_BLOCKERS' -and $sac.id -in $b.declaredBlockers) {$combat++}
        }
        if($run.scenario -eq 'removal') {Check ($removal -gt 0) "$label native removal-response witness"}
        if($run.scenario -eq 'combat') {Check ($combat -gt 0) "$label native declared-blocker rescue witness"}
    }
    $results+=@{scenario=$run.scenario;profile=$run.mode;seed=$run.seed;summary=$path;sha256=(Get-FileHash $path).Hash;casts=$cast.Count;resolved=$resolved.Count;removalResponses=$removal;combatRescues=$combat;clockReached=$s.clockReached}
}
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$results;scope='Bounded native custom-deck card use, not completion/winner/strength evidence'}|ConvertTo-Json -Depth 12|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
Write-Output "DONE $checks Greater Good card-use checks"
