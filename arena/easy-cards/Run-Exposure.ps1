# Two-player exposure games for the easy-cards profile: actor deck full of the card vs an opponent that
# creates the situation each rule reacts to. Same seed with the profile off and on; card-use evidence only.
param([int[]]$Seeds = @(118011, 118012), [int]$ClockSeconds = 120, [string[]]$Only = @(),
      [string]$Gate = 'arena/easy-cards/target/final-1.0.27')
$ErrorActionPreference = 'Stop'
$decks = Join-Path $PSScriptRoot 'decks'
$runs = [ordered]@{
    Elenora = @{cast = '^Add To Stack: .*cast Syr Elenora, the Discerning'; draw = '^Resolve Stack: .*Syr Elenora.*Draw'}
    Flitter = @{cast = '^Add To Stack: .*cast Marsh Flitter'; pump = 'activated Marsh Flitter'}
    Insight = @{cast = '^Add To Stack: .*cast Recurring Insight'; resolved = '^Resolve Stack: Recurring Insight'}
    Pact    = @{cast = '^Add To Stack: .*cast Pact of the Serpent'; resolved = '^Resolve Stack: Pact of the Serpent'}
    Suffer  = @{cast = '^Add To Stack: .*cast Suffer the Past'; resolved = '^Resolve Stack: Suffer the Past'}
}
foreach ($name in $runs.Keys) {
    if ($Only.Count -and $name -notin $Only) { continue }
    foreach ($seed in $Seeds) {
        foreach ($mode in 'off', 'on') {
            & (Join-Path $PSScriptRoot '../lib/Run-Smoke.ps1') -Gate $Gate -Profile easy-cards -ProfileMode $mode -Seed $seed -ClockSeconds $ClockSeconds `
                -Decks (Join-Path $decks "$name-Actor.dck"), (Join-Path $decks "$name-Opponent.dck") -Count $runs[$name] -Label "exposure-$name"
        }
    }
}
