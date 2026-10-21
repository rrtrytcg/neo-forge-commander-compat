$ErrorActionPreference = 'Stop'
$decks = @((Join-Path $PSScriptRoot 'decks/Actor.dck'), (Join-Path $PSScriptRoot 'decks/Opponent.dck'))
foreach ($seed in @(137011, 137012)) {
    foreach ($mode in @('off', 'on')) {
        & "$PSScriptRoot/../version-compat-1.0.28/Run-Smoke.ps1" -Gate 'arena/divine-reckoning/target/final-divine-r1' `
            -Profile divine-reckoning -ProfileMode $mode -Seed $seed -ClockSeconds 120 -Decks $decks `
            -Count @{casts='^Add To Stack: .*cast Divine Reckoning'; resolutions='^Resolve Stack: Divine Reckoning'}
    }
}
