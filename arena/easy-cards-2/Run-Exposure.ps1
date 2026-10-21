# Two-player exposure games for the easy-cards-2 profile: actor deck full of the card vs an opponent that
# creates the situation each rule reacts to. Same seed with the profile off and on; card-use evidence only.
param([int[]]$Seeds = @(119011, 119012, 119013), [int]$ClockSeconds = 120, [string[]]$Only = @(),
      [string]$Gate = 'arena/easy-cards-2/target/final-1.0.27')
$ErrorActionPreference = 'Stop'
$decks = Join-Path $PSScriptRoot 'decks'
$runs = [ordered]@{
    Willow   = @{cast = '^Add To Stack: .*cast Autumn Willow'; activated = 'activated Autumn Willow'}
    Edge     = @{cast = '^Add To Stack: .*cast Edge of Autumn'; resolved = '^Resolve Stack: Edge of Autumn'; cycled = '(?i)cycl'}
    Dragon   = @{cast = '^Add To Stack: .*cast Knollspine Dragon'; trigger = '^Resolve Stack: Knollspine Dragon'}
    Bosh     = @{activated = 'activated Bosh, Iron Golem'; resolved = '^Resolve Stack: Bosh, Iron Golem'}
    Ashling  = @{activated = 'activated Ashling the Pilgrim'; resolved = '^Resolve Stack: Ashling the Pilgrim'}
    Offering = @{cast = '^Add To Stack: .*cast Benevolent Offering'; resolved = '^Resolve Stack: Benevolent Offering'}
    Peel     = @{cast = '^Add To Stack: .*cast Peel from Reality'; resolved = '^Resolve Stack: Peel from Reality'}
}
foreach ($name in $runs.Keys) {
    if ($Only.Count -and $name -notin $Only) { continue }
    foreach ($seed in $Seeds) {
        foreach ($mode in 'off', 'on') {
            & (Join-Path $PSScriptRoot '../lib/Run-Smoke.ps1') -Gate $Gate -Profile easy-cards-2 -ProfileMode $mode -Seed $seed -ClockSeconds $ClockSeconds `
                -Decks (Join-Path $decks "$name-Actor.dck"), (Join-Path $decks "$name-Opponent.dck") -Count $runs[$name] -Label "exposure-$name"
        }
    }
}
