$ErrorActionPreference = 'Stop'
$decks = @('Actor', 'OpponentA', 'OpponentB') | ForEach-Object { Join-Path $PSScriptRoot "decks/$_.dck" }
# Three seats are necessary to exercise the approved two-opponent discard rule.
foreach ($seed in @(123011, 123012)) {
    foreach ($mode in @('off', 'on')) {
        & "$PSScriptRoot/../lib/Run-Smoke.ps1" -Gate 'arena/doomsday-confluence/target/final-1.0.27' `
            -Profile doomsday-confluence -ProfileMode $mode -Seed $seed -ClockSeconds 120 -Decks $decks -Label bounded `
            -Count @{casts='^Add To Stack: .*cast Doomsday Confluence'; resolved='^Resolve Stack: Doomsday Confluence'}
    }
}
