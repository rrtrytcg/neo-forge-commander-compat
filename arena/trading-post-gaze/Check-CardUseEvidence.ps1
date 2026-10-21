$ErrorActionPreference='Stop'
$checks=0
function Check($condition,$label) {
    if(-not $condition) { throw $label }
    $script:checks++; Write-Output "PASS $label"
}
function Rows($dir) {
    return @(Get-Content (Join-Path $dir 'probe.log') | Where-Object { $_.StartsWith('CARD_WITNESS ') } | ForEach-Object { $_.Substring(13) | ConvertFrom-Json })
}
function PlayerMap($players) {
    $map=@{}
    foreach($p in $players) { $map[$p.player]=$p }
    return $map
}
function NonlandMap($players) {
    $map=@{}
    foreach($p in $players) {
        foreach($c in $p.battlefield) {
            if($c.types -notmatch 'Land') { $map[[string]$c.id]=$c }
        }
    }
    return $map
}
$tpOn=@(); $gazeOn=@()
$native=Get-Content (Join-Path $PSScriptRoot 'target/repair-1.0.25/provenance.json') -Raw|ConvertFrom-Json
foreach($run in @(@{seed=97021;label='trading-post';return=$false},@{seed=97022;label='trading-post';return=$false},@{seed=97041;label='trading-post-return';return=$true})) {
    $seed=$run.seed
    $dir=Join-Path $PSScriptRoot "target/repair-card-use/$($run.label)-on-$seed"
    $summary=Get-Content (Join-Path $dir 'summary.json') -Raw | ConvertFrom-Json
    Check ($summary.nativeExit -eq 0) "trading post seed $seed native exit"
    Check (@($summary.unexpectedDiagnostics).Count -eq 0) "trading post seed $seed no unexpected diagnostics"
    Check ($summary.engine -eq 'NeoForge 1.0.25') "trading post seed $seed engine 1.0.25"
    foreach($file in $summary.files) { Check ((Get-FileHash -LiteralPath $file.path).Hash -eq $file.sha256) "probe input hash: $($file.path)" }
    Check (($summary.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $native.candidateSha256) "trading post seed $seed uses repaired native candidate"
    Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch "Couldn't add to stack" -Quiet)) "trading post seed $seed has no failed targeting/payment stack insertion"
    $rows=Rows $dir
    $casts=@($rows | Where-Object { $_.kind -eq 'cast' -and $_.api -eq 'PermanentNoncreature' })
    $resolved=@($rows | Where-Object { $_.kind -eq 'resolved' })
    Check ($casts.Count -ge 1) "trading post seed $seed cast"
    Check ($resolved.Count -ge 1) "trading post seed $seed activated and resolved"
    $modes=@{}
    foreach($row in $resolved) { $modes[$row.api]=1 }
    if(!$run.return) { Check ($modes.ContainsKey('Draw')) "trading post seed $seed uses draw mode" }
    if(!$run.return) { Check ($modes.ContainsKey('Token')) "trading post seed $seed uses Goat mode" } else { Check ($modes.ContainsKey('ChangeZone')) "trading post return scenario seed $seed returns artifact" }
    foreach($row in $resolved) {
        Check ($row.fizzled -eq $false) "trading post seed $seed $($row.api) resolves without fizzle"
        $b=$row.before
        Check ($b -ne $null) "trading post seed $seed turn $($row.turn) $($row.api) has before state"
        $before=PlayerMap $b.players; $after=PlayerMap $row.players
        $aiBefore=$before['Trading Post-0']; $aiAfter=$after['Trading Post-0']
        Check ($aiBefore -ne $null -and $aiAfter -ne $null) "trading post seed $seed turn $($row.turn) $($row.api) controller present"
        if($row.api -eq 'Draw') {
            Check ($aiBefore.librarySize -gt 4) "trading post seed $seed preserves native library reserve"
            Check (($aiAfter.handSize - $aiBefore.handSize) -eq 1) "trading post seed $seed turn $($row.turn) draw adds one card"
        }
        if($row.api -eq 'Token') {
            $tokensBefore=@($aiBefore.battlefield | Where-Object { $_.token -and $_.types -match 'Creature' }).Count
            $tokensAfter=@($aiAfter.battlefield | Where-Object { $_.token -and $_.types -match 'Creature' }).Count
            Check ($tokensAfter -eq $tokensBefore + 1) "trading post seed $seed turn $($row.turn) Goat token created"
        }
        if($row.api -eq 'GainLife') {
            Check (($aiAfter.life - $aiBefore.life) -eq 4) "trading post seed $seed turn $($row.turn) life mode gains four"
        }
        if($row.api -eq 'ChangeZone') {
            Check (@($b.targets).Count -eq 1) "trading post seed $seed return has AI-chosen target"
            $target=$b.targets[0].name
            Check ($target -in $aiBefore.graveyard -and $target -in $aiAfter.hand) "trading post seed $seed targeted graveyard artifact reaches hand"
            Check (($aiAfter.handSize - $aiBefore.handSize) -eq 1) "trading post seed $seed turn $($row.turn) return mode adds artifact to hand"
        }
    }
    $tpOn+=@{seed=$seed;casts=$casts.Count;resolved=$resolved.Count;modes=@($modes.Keys)}
}
foreach($seed in @(97031,97032)) {
    $dir=Join-Path $PSScriptRoot "target/repair-card-use/gaze-of-granite-on-$seed"
    $summary=Get-Content (Join-Path $dir 'summary.json') -Raw | ConvertFrom-Json
    Check ($summary.nativeExit -eq 0) "gaze seed $seed native exit"
    Check (@($summary.unexpectedDiagnostics).Count -eq 0) "gaze seed $seed no unexpected diagnostics"
    Check ($summary.engine -eq 'NeoForge 1.0.25') "gaze seed $seed engine 1.0.25"
    foreach($file in $summary.files) { Check ((Get-FileHash -LiteralPath $file.path).Hash -eq $file.sha256) "probe input hash: $($file.path)" }
    Check (($summary.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $native.candidateSha256) "gaze seed $seed uses repaired native candidate"
    $rows=Rows $dir
    $casts=@($rows | Where-Object { $_.kind -eq 'cast' -and $_.api -eq 'DestroyAll' })
    $resolved=@($rows | Where-Object { $_.kind -eq 'resolved' -and $_.api -eq 'DestroyAll' })
    Check ($casts.Count -ge 1) "gaze seed $seed cast"
    Check ($resolved.Count -ge 1) "gaze seed $seed resolved"
    $destroyed=0
    foreach($row in $resolved) {
        $x=[int]$row.x
        Check ($x -ge 1) "gaze seed $seed turn $($row.turn) paid X>=1"
        Check ($row.fizzled -eq $false) "gaze seed $seed turn $($row.turn) resolved without fizzle"
        $b=$row.before
        Check ($b -ne $null) "gaze seed $seed turn $($row.turn) before state recorded"
        $beforeNonland=NonlandMap $b.players
        $afterNonland=NonlandMap $row.players
        $enemyDestroyed=0
        foreach($key in $beforeNonland.Keys) {
            $card=$beforeNonland[$key]
            if($card.cmc -le $x -and $card.controller -ne 'Gaze of Granite-0' -and -not $afterNonland.ContainsKey($key)) { $enemyDestroyed++ }
        }
        Check ($enemyDestroyed -ge 1) "gaze seed $seed turn $($row.turn) destroys at least one opposing permanent at X=$x"
        $destroyed+=$enemyDestroyed
        foreach($p in $row.players) {
            $beforeP=@($b.players | Where-Object { $_.player -eq $p.player })[0]
            $landsBefore=@($beforeP.battlefield | Where-Object { $_.types -match 'Land' }).Count
            $landsAfter=@($p.battlefield | Where-Object { $_.types -match 'Land' }).Count
            Check ($landsBefore -eq $landsAfter) "gaze seed $seed turn $($row.turn) $($p.player) lands survive"
        }
        Check ($afterNonland.Count -le $beforeNonland.Count) "gaze seed $seed turn $($row.turn) wipe only removes permanents"
    }
    $gazeOn+=@{seed=$seed;casts=$casts.Count;resolved=$resolved.Count;destroyed=$destroyed}
}
foreach($pair in @(@('trading-post',97021),@('gaze-of-granite',97031))) {
    $dir=Join-Path $PSScriptRoot "target/repair-card-use/$($pair[0])-off-$($pair[1])"
    $summary=Get-Content (Join-Path $dir 'summary.json') -Raw | ConvertFrom-Json
    Check ($summary.nativeExit -eq 0) "$($pair[0]) off native exit"
    Check (@($summary.unexpectedDiagnostics).Count -eq 0) "$($pair[0]) off no unexpected diagnostics"
    foreach($file in $summary.files) { Check ((Get-FileHash -LiteralPath $file.path).Hash -eq $file.sha256) "off probe input hash: $($file.path)" }
    Check (($summary.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $native.candidateSha256) "$($pair[0]) off uses repaired native candidate"
    $rows=Rows $dir
    Check ($rows.Count -eq 0) "$($pair[0]) off control produced no card use"
}
Check ($tpOn.Count -eq 3 -and $gazeOn.Count -eq 2) "both on-profile seeds recorded"
Write-Output "DONE $checks Trading Post and Gaze of Granite card-use checks"
Write-Output ("TP="+($tpOn|ConvertTo-Json -Compress))
Write-Output ("GAZE="+($gazeOn|ConvertTo-Json -Compress))
