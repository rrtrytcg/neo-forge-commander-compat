. (Join-Path $PSScriptRoot 'Runtime.ps1')
$expectedHash = (Get-FileHash (Join-Path $arena 'decks/Meren_A.dck')).Hash
foreach ($seat in 'A','B','C','D') {
    if ((Get-FileHash (Join-Path $arena "decks/Meren_$seat.dck")).Hash -ne $expectedHash) { throw 'Seat decks differ' }
}
$upstream = Get-Content -LiteralPath (Join-Path $repo 'forge-gui/res/quest/precons/Plunder the Graves.dck')
$provided = Get-Content -LiteralPath (Join-Path $arena 'decks/Meren_A.dck')
if (Compare-Object $upstream $provided -SyncWindow 0) { throw 'Provided deck differs from upstream lines' }
Push-Location (Join-Path $repo 'forge-gui')
try {
    & $java @javaBase -cp $jar "$arena/scripts/ValidateDeck.java" "$arena/decks/Meren_A.dck" *> "$arena/evidence/deck-validation.log"
    if ($LASTEXITCODE -ne 0) { throw 'Deck validation failed' }
    & $java @javaBase -cp $jar "$arena/scripts/MerenDecisionFixture.java" *> "$arena/evidence/meren-fixture.log"
    if ($LASTEXITCODE -ne 0) { throw 'Controlled Meren choice diagnostic failed' }
} finally { Pop-Location }
