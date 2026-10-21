$ErrorActionPreference='Stop'
$ai='SuitProbe-0'
$onSeeds=@(96801,96802)
$offSeeds=@(96801,96802)
$checks=0
function Check($condition,$label) {
    if(-not $condition) { throw $label }
    $script:checks++; Write-Output "PASS $label"
}
function Tier($c) {
    if($c.trample) { return 0 }
    if($c.flying) { return 1 }
    if($c.firstStrike) { return 2 }
    return 3
}
$accepted=0; $declined=0
foreach($seed in $onSeeds) {
    $dir=Join-Path $PSScriptRoot "target/card-use/on-$seed"
    $summary=Get-Content (Join-Path $dir 'summary.json') -Raw | ConvertFrom-Json
    Check ($summary.nativeExit -eq 0) "seed $seed native exit"
    Check (@($summary.unexpectedDiagnostics).Count -eq 0) "seed $seed no unexpected diagnostics"
    Check ($summary.files.Count -gt 0) "seed $seed inputs hashed"
    $rows=@($summary.observations)
    $casts=@($rows | Where-Object { $_.kind -eq 'cast' })
    $equips=@($rows | Where-Object { $_.kind -eq 'equip' })
    $attachments=@($rows | Where-Object { $_.kind -eq 'attached' })
    $triggers=@($rows | Where-Object { $_.kind -eq 'trigger' })
    $changes=@($rows | Where-Object { $_.kind -eq 'resolved' -and $_.source -eq 'trigger' -and $_.controllerBefore -ne $_.equippedController })
    Check ($casts.Count -ge 1) "seed $seed Assault Suit cast"
    Check ($equips.Count -ge 1) "seed $seed equip activated"
    Check ($attachments.Count -ge 1) "seed $seed equipment attached"
    foreach($e in $equips) {
        $own=@($e.ownCreatures | Where-Object { $_.controller -eq $ai -and $_.power -gt 0 })
        Check ($own.Count -gt 0) "seed $seed turn $($e.turn) equip has eligible own creatures"
        $bestTier=99; $bestPower=[int]::MinValue; $bestToughness=[int]::MinValue
        foreach($c in $own) {
            $tier=Tier $c
            if($tier -lt $bestTier -or ($tier -eq $bestTier -and $c.power -gt $bestPower) -or ($tier -eq $bestTier -and $c.power -eq $bestPower -and $c.toughness -gt $bestToughness)) {
                $bestTier=$tier; $bestPower=$c.power; $bestToughness=$c.toughness
            }
        }
        $targets=@($e.targets)
        Check ($targets.Count -ge 1) "seed $seed turn $($e.turn) equip target recorded"
        $target=$targets[0]
        Check ($target.controller -eq $ai) "seed $seed turn $($e.turn) equip target is ours"
        Check ((Tier $target) -eq $bestTier -and $target.power -eq $bestPower -and $target.toughness -eq $bestToughness) "seed $seed turn $($e.turn) priority target $($target.name)"
    }
    foreach($change in $changes) {
        $trigger=@($triggers | Where-Object { $_.abilityId -eq $change.abilityId })
        Check ($trigger.Count -ge 1) "seed $seed ability $($change.abilityId) handoff was stacked"
        $t=$trigger[0]
        Check ($t.availableMana -ge 3) "seed $seed ability $($change.abilityId) had three or more mana"
        Check ($t.equippedId -ge 0) "seed $seed ability $($change.abilityId) had an equipped creature"
        Check ($change.controllerBefore -eq $ai -and $change.equippedController -ne $ai) "seed $seed ability $($change.abilityId) control moved to an opponent"
        Check ($change.equippedName -eq $t.equippedName) "seed $seed ability $($change.abilityId) handoff kept the equipped creature"
        $accepted++
    }
    foreach($t in $triggers) {
        if($t.availableMana -lt 3 -or $t.equippedId -lt 0) {
            $resolved=@($changes | Where-Object { $_.abilityId -eq $t.abilityId })
            Check ($resolved.Count -eq 0) "seed $seed ability $($t.abilityId) declined without mana or equipment"
            $declined++
        }
    }
}
Check ($accepted -ge 1) "at least one handoff accepted"
Check ($declined -ge 1) "at least one handoff declined by the gate"
foreach($seed in $offSeeds) {
    $dir=Join-Path $PSScriptRoot "target/card-use/off-$seed"
    $summary=Get-Content (Join-Path $dir 'summary.json') -Raw | ConvertFrom-Json
    Check ($summary.nativeExit -eq 0) "off seed $seed native exit"
    Check (@($summary.unexpectedDiagnostics).Count -eq 0) "off seed $seed no unexpected diagnostics"
    $rows=@($summary.observations)
    Check (@($rows).Count -eq 0) "off seed $seed no Assault Suit cast, equip or handoff"
}
Write-Output "DONE $checks Assault Suit card-use checks (accepted=$accepted declined=$declined)"
