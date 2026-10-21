$ErrorActionPreference='Stop'
$ai='SelvalaProbe-0'
$onSeeds=@(96901,96902)
$offSeeds=@(96901,96902)
$checks=0; $parleyTotal=0
function Check($condition,$label) {
    if(-not $condition) { throw $label }
    $script:checks++; Write-Output "PASS $label"
}
foreach($seed in $onSeeds) {
    $dir=Join-Path $PSScriptRoot "target/card-use/on-$seed"
    $summary=Get-Content (Join-Path $dir 'summary.json') -Raw | ConvertFrom-Json
    Check ($summary.nativeExit -eq 0) "seed $seed native exit"
    Check (@($summary.unexpectedDiagnostics).Count -eq 0) "seed $seed no unexpected diagnostics"
    Check ($summary.engine -eq 'NeoForge 1.0.25') "seed $seed engine 1.0.25"
    $rows=@(Get-Content (Join-Path $dir 'probe.log') | Where-Object { $_.StartsWith('SELVALA_WITNESS ') } | ForEach-Object { $_.Substring(16) | ConvertFrom-Json })
    $casts=@($rows | Where-Object { $_.kind -eq 'cast' })
    $parleys=@($rows | Where-Object { $_.kind -eq 'parley' })
    $resolved=@($rows | Where-Object { $_.kind -eq 'resolved' -and $_.source -eq 'parley' })
    Check ($casts.Count -ge 1) "seed $seed Selvala cast"
    Check ($parleys.Count -ge 1 -and $resolved.Count -ge 1) "seed $seed parley activated and resolved"
    foreach($row in $resolved) {
        $b=$row.before; $a=@($row.players)
        Check ($b -ne $null -and $a.Count -eq @($b.players).Count -and $a.Count -ge 1) "seed $seed turn $($row.turn) parley before/after state recorded"
        Check ($b.tapped -eq $true) "seed $seed turn $($row.turn) tap cost paid"
        Check ($b.manaAbility -eq $false) "seed $seed turn $($row.turn) native non-mana classification retained"
        $beforePlayers=@{}; foreach($p in $b.players){ $beforePlayers[$p.player]=$p }
        $afterPlayers=@{}; foreach($p in $a){ $afterPlayers[$p.player]=$p }
        $x=@($b.players | Where-Object { $_.topLand -eq $false }).Count
        $beforeAi=$beforePlayers[$ai]; $afterAi=$afterPlayers[$ai]
        Check ($beforeAi -ne $null -and $afterAi -ne $null) "seed $seed turn $($row.turn) controller remains in the parley"
        Check (($afterAi.life - $beforeAi.life) -eq $x) "seed $seed turn $($row.turn) life gain equals $x revealed nonlands"
        $drawsOk=$true
        foreach($name in $afterPlayers.Keys) {
            if(($afterPlayers[$name].hand - $beforePlayers[$name].hand) -ne 1 -or ($beforePlayers[$name].library - $afterPlayers[$name].library) -ne 1) { $drawsOk=$false }
        }
        Check $drawsOk "seed $seed turn $($row.turn) every remaining player draws exactly one revealed card"
        $parleyTotal++
    }
}
Check ($parleyTotal -ge 1) "at least one resolved parley"
foreach($seed in $offSeeds) {
    $dir=Join-Path $PSScriptRoot "target/card-use/off-$seed"
    $summary=Get-Content (Join-Path $dir 'summary.json') -Raw | ConvertFrom-Json
    Check ($summary.nativeExit -eq 0) "off seed $seed native exit"
    Check (@($summary.unexpectedDiagnostics).Count -eq 0) "off seed $seed no unexpected diagnostics"
    $rows=@(Get-Content (Join-Path $dir 'probe.log') | Where-Object { $_.StartsWith('SELVALA_WITNESS ') })
    Check ($rows.Count -eq 0) "off seed $seed no Selvala cast or parley"
}
Write-Output "DONE $checks Selvala card-use checks (resolvedParleys=$parleyTotal)"
