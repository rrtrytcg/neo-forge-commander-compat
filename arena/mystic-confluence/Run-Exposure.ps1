$ErrorActionPreference = 'Stop'
$decks = @((Join-Path $PSScriptRoot 'decks/Actor.dck'), (Join-Path $PSScriptRoot 'decks/Opponent.dck'))
foreach ($seed in @(139011, 139012)) {
    foreach ($mode in @('off', 'on')) {
        & "$PSScriptRoot/../version-compat-1.0.28/Run-Smoke.ps1" -Gate 'arena/mystic-confluence/target/final-mystic-confluence-r2' `
            -Profile mystic-confluence -ProfileMode $mode -Seed $seed -ClockSeconds 120 -Decks $decks -Label exposure `
            -Count @{casts='^Add To Stack: .*cast Mystic Confluence'; resolutions='^Resolve Stack: Mystic Confluence \(\d+\) -'; bounce='^Resolve Stack: Mystic Confluence .*Return target creature'; repeatedDraw='^Resolve Stack: Mystic Confluence .*Draw a card\. Draw a card\.'}
    }
}
