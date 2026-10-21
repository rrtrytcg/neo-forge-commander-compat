$ErrorActionPreference = 'Stop'
$decks = @((Join-Path $PSScriptRoot 'decks/Actor.dck'), (Join-Path $PSScriptRoot 'decks/Opponent.dck'))
foreach ($seed in @(128011, 128012)) {
    foreach ($mode in @('off', 'on')) {
        & "$PSScriptRoot/../lib/Run-Smoke.ps1" -Gate 'arena/confiscation-coup/target/final-1.0.27-current-install' `
            -Profile confiscation-coup -ProfileMode $mode -Seed $seed -ClockSeconds 120 -Decks $decks `
            -Count @{casts='^Add To Stack: .*cast Confiscation Coup'; resolutions='^Resolve Stack: Confiscation Coup'; control='gains control of (Hill Giant|Craw Wurm)'}
    }
}
