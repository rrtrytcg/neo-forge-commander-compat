$ErrorActionPreference = 'Stop'
$decks = @((Join-Path $PSScriptRoot 'decks/Actor.dck'), (Join-Path $PSScriptRoot 'decks/Opponent.dck'))
foreach ($seed in @(138011, 138012)) {
    foreach ($mode in @('off', 'on')) {
        & "$PSScriptRoot/../version-compat-1.0.28/Run-Smoke.ps1" -Gate 'arena/daretti/target/final-daretti-r1' `
            -Profile daretti -ProfileMode $mode -Seed $seed -ClockSeconds 120 -Decks $decks -Label smoke-r2 `
            -Count @{casts='^Add To Stack: .*cast Daretti, Scrap Savant'; activations='^Add To Stack: .* activated Daretti, Scrap Savant'; resolutions='^Resolve Stack: Daretti, Scrap Savant \(\d+\) -'}
    }
}
