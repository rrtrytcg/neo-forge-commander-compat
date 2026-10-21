# Two-player exposure games for the four utility lands: actor deck full of the land vs an opponent that
# creates the situation each rule reacts to. Same seed with the profile off and on; card-use evidence only.
param([int[]]$Seeds = @(117011, 117012), [int]$ClockSeconds = 120, [string[]]$Only = @())
$ErrorActionPreference = 'Stop'
$gate = 'arena/utility-lands/target/final-1.0.27'
$decks = Join-Path $PSScriptRoot 'decks'
$runs = [ordered]@{
    Labyrinth = @{remove = 'activated Labyrinth of Skophos targeting'; resolved = '^Resolve Stack: Labyrinth of Skophos'}
    Helios    = @{energy = 'activated HELIOS One$'; destroy = 'activated HELIOS One targeting'; destroyed = '^Resolve Stack: HELIOS One .*Destroy'}
    Stage     = @{copy = "activated Thespian's Stage targeting"; commanderCast = "cast Trostani, Selesnya's Voice"}
    Dakmor    = @{dredge = '^Replacement Effect: Dakmor Salvage - Dredge'; salvagePlayed = 'Land: .*Dakmor Salvage'}
}
foreach ($name in $runs.Keys) {
    if ($Only.Count -and $name -notin $Only) { continue }
    foreach ($seed in $Seeds) {
        foreach ($mode in 'off', 'on') {
            & (Join-Path $PSScriptRoot '../lib/Run-Smoke.ps1') -Gate $gate -Profile utility-lands -ProfileMode $mode -Seed $seed -ClockSeconds $ClockSeconds `
                -Decks (Join-Path $decks "$name-Actor.dck"), (Join-Path $decks "$name-Opponent.dck") -Count $runs[$name] -Label "exposure-$name"
        }
    }
}
