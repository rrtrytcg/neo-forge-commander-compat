param([string]$Label='baseline')
. (Join-Path $PSScriptRoot 'Parse-Result.ps1')
$arena = Split-Path $PSScriptRoot -Parent
$path = Join-Path $arena "evidence/$Label-results.json"
$rows = @(Get-Content $path -Raw | ConvertFrom-Json)
foreach ($row in $rows) {
    $outputText = Get-Content $row.rawLog -Raw
    $errorText = if ((Get-Item $row.stderrLog).Length) { Get-Content $row.stderrLog -Raw } else { '' }
    $parsed = Get-ArenaResult $outputText $errorText $row.exitCode ($row.status -eq 'process_timeout')
    foreach ($property in $parsed.PSObject.Properties) { $row | Add-Member -NotePropertyName $property.Name -NotePropertyValue $property.Value -Force }
}
$rows | ConvertTo-Json -Depth 5 | Set-Content $path
$rows | Select-Object id,seed,seats,status,winnerSeat,reportedWinnerSeat,lastTurn,gameMilliseconds,wallMilliseconds,aiTimeout,engineTimeout,hasError | Export-Csv (Join-Path $arena "evidence/$Label-results.csv") -NoTypeInformation
$clean = @($rows | Where-Object status -eq 'win')
$turnRows = @($clean | Where-Object { $null -ne $_.lastTurn })
$summary = [ordered]@{
    label=$Label; attempted=$rows.Count; cleanWins=$clean.Count
    exclusiveStatusCounts=@($rows | Group-Object status | ForEach-Object { @{status=$_.Name; count=$_.Count} })
    draws=@($rows | Where-Object status -eq 'draw').Count
    engineTimeouts=@($rows | Where-Object engineTimeout).Count
    aiTimeouts=@($rows | Where-Object aiTimeout).Count
    processTimeouts=@($rows | Where-Object status -eq 'process_timeout').Count
    errors=@($rows | Where-Object status -eq 'error').Count
    unfinished=@($rows | Where-Object status -eq 'unfinished').Count
    meanWallMilliseconds=($rows | Measure-Object wallMilliseconds -Average).Average
    meanCleanTurns=if ($turnRows.Count) { ($turnRows | Measure-Object lastTurn -Average).Average } else { $null }
    meanCleanGameMilliseconds=($clean | Measure-Object gameMilliseconds -Average).Average
    seatWins=@(1..$rows[0].seats | ForEach-Object { $seat=$_; @{seat=$seat; cleanWins=@($clean | Where-Object winnerSeat -eq $seat).Count; reportedWins=@($rows | Where-Object reportedWinnerSeat -eq $seat).Count} })
    note='Attempt denominator includes every requested run. Internal AI timeouts are excluded from clean wins even if Forge reports a winner. Turn means use actual sequential player turns, not Forge outcome turns divided by two. Smoke diagnostics do not establish balance or policy superiority.'
}
$summary | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $arena "evidence/$Label-summary.json")
$summary | ConvertTo-Json -Depth 5
