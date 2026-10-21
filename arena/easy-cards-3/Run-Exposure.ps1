# Exposure games for the easy-cards-3 profile: actor deck full of the card vs opponents that create the
# situation each rule reacts to (Call for Aid needs a three-player game). Same seed with the profile off and on.
param([int[]]$Seeds = @(120011, 120012, 120013), [int]$ClockSeconds = 120, [string[]]$Only = @(),
      [string]$Gate = 'arena/easy-cards-3/target/final-1.0.27')
$ErrorActionPreference = 'Stop'
$decks = Join-Path $PSScriptRoot 'decks'
$runs = [ordered]@{
    Piper       = @{cast = '^Add To Stack: .*cast The Prismatic Piper'}
    Cave        = @{activated = 'activated Cave of Temptation'}
    Throes      = @{cast = '^Add To Stack: .*cast Throes of Chaos'; resolved = '^Resolve Stack: Throes of Chaos'}
    Replication = @{cast = '^Add To Stack: .*cast Masterful Replication'; resolved = '^Resolve Stack: Masterful Replication'}
    Aid         = @{cast = '^Add To Stack: .*cast Call for Aid'; resolved = '^Resolve Stack: Call for Aid'}
    Raptor      = @{cast = '^Add To Stack: .*cast Marauding Raptor'; raptorHitPiker = '^Damage: Marauding Raptor .*deals 2 damage to Goblin Piker'; raptorHitGiant = '^Damage: Marauding Raptor .*deals 2 damage to Hill Giant'}
    Bontu       = @{cast = '^Add To Stack: .*cast God-Eternal Bontu'; landsSacrificed = '^Zone Change: Swamp .*put into Graveyard from Battlefield'}
    Esix        = @{cast = '^Add To Stack: .*cast Esix, Fractal Bloom'; replacementOffered = 'Replacement Effect: The first time you would create'}
}
foreach ($name in $runs.Keys) {
    if ($Only.Count -and $name -notin $Only) { continue }
    $files = @((Join-Path $decks "$name-Actor.dck"), (Join-Path $decks "$name-Opponent.dck"))
    if (Test-Path (Join-Path $decks "$name-OpponentB.dck")) { $files += (Join-Path $decks "$name-OpponentB.dck") }
    foreach ($seed in $Seeds) {
        foreach ($mode in 'off', 'on') {
            & (Join-Path $PSScriptRoot '../lib/Run-Smoke.ps1') -Gate $Gate -Profile easy-cards-3 -ProfileMode $mode -Seed $seed -ClockSeconds $ClockSeconds `
                -Decks $files -Count $runs[$name] -Label "exposure-$name"
        }
    }
}
