$ErrorActionPreference = 'Stop'
$decks = @((Join-Path $PSScriptRoot 'decks/Actor.dck'), (Join-Path $PSScriptRoot 'decks/Opponent.dck'))
foreach ($seed in @(121011, 121012)) {
    foreach ($mode in @('off', 'on')) {
        & "$PSScriptRoot/../lib/Run-Smoke.ps1" -Gate 'arena/jolrael/target/final-1.0.27' `
            -Profile jolrael -ProfileMode $mode -Seed $seed -ClockSeconds 120 -Decks $decks `
            -Count @{casts='^Add To Stack: .*cast Jolrael, Mwonvuli Recluse';
                activations='^Add To Stack: .*activated Jolrael, Mwonvuli Recluse';
                cats='^Resolve Stack: Whenever you draw your second card each turn, create a 2/2 green Cat creature token\.'}
    }
}
