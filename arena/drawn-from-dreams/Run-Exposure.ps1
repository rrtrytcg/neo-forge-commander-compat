$ErrorActionPreference = 'Stop'
$decks = @((Join-Path $PSScriptRoot 'decks/Actor.dck'), (Join-Path $PSScriptRoot 'decks/Opponent.dck'))
foreach ($seed in @(140011, 140012)) {
    foreach ($mode in @('off', 'on')) {
        & "$PSScriptRoot/../version-compat-1.0.28/Run-Smoke.ps1" -Gate 'arena/drawn-from-dreams/target/final-drawn-from-dreams-r1' `
            -Profile drawn-from-dreams -ProfileMode $mode -Seed $seed -ClockSeconds 120 -Decks $decks -Label exposure `
            -Count @{casts='^Add To Stack: .*cast Drawn from Dreams'; resolutions='^Resolve Stack: Drawn from Dreams \(\d+\) -'}
    }
}
