$ErrorActionPreference = 'Stop'
$decks = @((Join-Path $PSScriptRoot 'decks/Actor.dck'), (Join-Path $PSScriptRoot 'decks/Opponent.dck'))
foreach ($seed in @(129011, 129012)) {
    foreach ($mode in @('off', 'on')) {
        & 'arena/version-compat-1.0.28/Run-Smoke.ps1' -Gate 'arena/gideon-jura/target/final-1.0.28' `
            -Profile gideon-jura -ProfileMode $mode -Seed $seed -ClockSeconds 120 -Decks $decks `
            -Count @{casts='^Add To Stack: .*cast Gideon Jura'; resolvesSpell='^Resolve Stack: Gideon Jura$'; `
                activates='^Add To Stack: .* activated Gideon Jura targeting'; resolvesDestroy='^Resolve Stack: Gideon Jura \(\d+\) - Destroy'}
    }
}
