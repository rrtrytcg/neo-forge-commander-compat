. (Join-Path $PSScriptRoot 'Parse-Result.ps1')
$win = "Turn: Turn 53 (Ai(3)-Plunder the Graves)`nGame Outcome: Turn 27`nGame Result: Game 1 ended in 97169 ms. Ai(3)-Plunder the Graves has won!"
$result = Get-ArenaResult $win '' 0 $false
if ($result.status -ne 'win' -or $result.winnerSeat -ne 3 -or $result.lastTurn -ne 53) { throw 'Valid win or multiplayer turn parsing failed' }
$result = Get-ArenaResult ($win + "`nAI eval thread at timeout:") 'java.util.concurrent.TimeoutException' 0 $false
if ($result.status -ne 'ai_timeout' -or $null -ne $result.winnerSeat -or $result.reportedWinnerSeat -ne 3) { throw 'AI timeout counted as clean win' }
$draw = 'Game Result: Game 1 ended in a Draw! Took 120000 ms.'
if ((Get-ArenaResult $draw '' 0 $false).status -ne 'draw') { throw 'Draw parsing failed' }
if ((Get-ArenaResult ("Stopping slow match as draw`n" + $draw) '' 0 $false).status -ne 'engine_timeout') { throw 'Engine timeout counted as ordinary draw' }
if ((Get-ArenaResult ("Stopping slow match as draw`n" + $win) '' 0 $false).status -ne 'engine_timeout') { throw 'Engine timeout followed by reported winner counted as win' }
if ((Get-ArenaResult $win '' 0 $true).status -ne 'process_timeout') { throw 'Process timeout counted as win' }
if ((Get-ArenaResult $win 'java.lang.IllegalStateException' 0 $false).status -ne 'error') { throw 'Exception counted as win' }
if ((Get-ArenaResult 'Simulation mode' '' 0 $false).status -ne 'unfinished') { throw 'Missing result counted as complete' }
'PASS: win/seat/actual multiplayer turns; AI timeout exclusion; draw; engine timeout; process timeout; exception; unfinished'
