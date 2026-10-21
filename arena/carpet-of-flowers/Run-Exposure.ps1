$ErrorActionPreference = 'Stop'
$decks = @((Join-Path $PSScriptRoot 'decks/Actor.dck'), (Join-Path $PSScriptRoot 'decks/Opponent.dck'))
foreach ($seed in @(125011, 125012)) {
    foreach ($mode in @('off', 'on')) {
        & "$PSScriptRoot/../lib/Run-Smoke.ps1" -Gate 'arena/carpet-of-flowers/target/final-1.0.27' `
            -Profile carpet-of-flowers -ProfileMode $mode -Seed $seed -ClockSeconds 120 -Decks $decks `
            -Count @{casts='^Add To Stack: .*cast Carpet of Flowers'; triggers='^Add To Stack: .*triggered Carpet of Flowers'; resolved='^Resolve Stack: Carpet of Flowers'}
    }
}
