function Get-ArenaResult {
    param([string]$OutputText, [string]$ErrorText, [int]$ExitCode, [bool]$ProcessTimeout)
    $winner = [regex]::Match($OutputText, 'Game Result: Game 1 ended in (\d+) ms\. Ai\((\d+)\)-.* has won!')
    $draw = [regex]::Match($OutputText, 'Game Result: Game 1 ended in a Draw! Took (\d+) ms\.')
    $turns = [regex]::Matches($OutputText, '(?m)^Turn: Turn (\d+) \(Ai\(')
    $engineTimeout = $OutputText.Contains('Stopping slow match as draw')
    $aiTimeout = ($OutputText + $ErrorText).Contains('AI eval thread at timeout:')
    $errorPattern = '(?m)Exception|StackOverflowError|OutOfMemoryError|Could not load deck|Unknown AI profile|Uncaught exception'
    $hasError = ($OutputText -match $errorPattern) -or ($ErrorText -match $errorPattern) -or ($ExitCode -ne 0)
    $status = if ($ProcessTimeout) { 'process_timeout' } elseif ($engineTimeout) { 'engine_timeout' } elseif ($aiTimeout) { 'ai_timeout' } elseif ($hasError) { 'error' } elseif ($winner.Success) { 'win' } elseif ($draw.Success) { 'draw' } else { 'unfinished' }
    [pscustomobject]@{
        status=$status
        winnerSeat=if ($status -eq 'win') { [int]$winner.Groups[2].Value } else { $null }
        reportedWinnerSeat=if ($winner.Success) { [int]$winner.Groups[2].Value } else { $null }
        gameMilliseconds=if ($winner.Success) { [long]$winner.Groups[1].Value } elseif ($draw.Success) { [long]$draw.Groups[1].Value } else { $null }
        lastTurn=if ($turns.Count) { ($turns | ForEach-Object { [int]$_.Groups[1].Value } | Measure-Object -Maximum).Maximum } else { $null }
        engineTimeout=$engineTimeout
        aiTimeout=$aiTimeout
        hasError=$hasError
        exitCode=$ExitCode
    }
}
