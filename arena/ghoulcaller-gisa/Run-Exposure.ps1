$ErrorActionPreference = 'Stop'
$decks = @((Join-Path $PSScriptRoot 'decks/Actor.dck'), (Join-Path $PSScriptRoot 'decks/Opponent.dck'))
foreach ($seed in @(126011, 126012)) {
    foreach ($mode in @('off', 'on')) {
        & "$PSScriptRoot/../lib/Run-Smoke.ps1" -Gate 'arena/ghoulcaller-gisa/target/final-1.0.27' `
            -Profile ghoulcaller-gisa -ProfileMode $mode -Seed $seed -ClockSeconds 120 -Decks $decks `
            -Count @{casts='^Add To Stack: .*cast Ghoulcaller Gisa'; activations='^Add To Stack: .*activated Ghoulcaller Gisa'; tokens='^Resolve Stack: Ghoulcaller Gisa .*creates .*Zombie creature tokens'}
    }
}
