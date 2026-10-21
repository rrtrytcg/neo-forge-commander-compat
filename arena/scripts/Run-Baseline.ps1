param([int[]]$Seeds = (1001..1012), [ValidateSet(2,4)][int]$Seats=4, [int]$ClockSeconds=600, [switch]$Quiet, [string]$Label='baseline')
. (Join-Path $PSScriptRoot 'Runtime.ps1')
. (Join-Path $PSScriptRoot 'Parse-Result.ps1')
$results = @()
$sha = (& git -C $repo rev-parse HEAD).Trim()
$control = (& git -C $repo rev-parse arena-control-a2fe312).Trim()
$engineDiff = & git -C $repo diff $control -- forge-core forge-game forge-ai forge-gui forge-gui-desktop pom.xml
if ($engineDiff) { throw 'Engine differs from frozen control' }
$engineDirty = & git -C $repo status --porcelain -- forge-core forge-game forge-ai forge-gui forge-gui-desktop pom.xml
if ($engineDirty) { throw 'Engine has tracked or untracked edits' }
$deckHash = (Get-FileHash (Join-Path $arena 'decks/Meren_A.dck')).Hash
$jarHash = (Get-FileHash $jar).Hash
foreach ($seat in 'A','B','C','D') {
    if ((Get-FileHash (Join-Path $arena "decks/Meren_$seat.dck")).Hash -ne $deckHash) { throw 'Seat decks differ' }
}
foreach ($seed in $Seeds) {
    $id = "$Label-$Seats-$seed"
    $rawPath = Join-Path $arena "evidence/$id.raw.log"
    $errPath = Join-Path $arena "evidence/$id.stderr.log"
    if (Test-Path $rawPath) { throw "Run already exists: $id (choose a new Label)" }
    $arguments = @($javaBase) + @('-jar',$jar,'sim','-D',(Join-Path $arena 'decks'),'-d')
    $arguments += @('A','B','C','D')[0..($Seats-1)] | ForEach-Object { "Meren_$_.dck" }
    $arguments += @('-f','Commander','-n','1','-c',"$ClockSeconds",'-s',"$seed",'-a')
    $arguments += @('Default') * $Seats
    if ($Quiet) { $arguments += '-q' }
    $start = [System.Diagnostics.ProcessStartInfo]::new($java)
    $start.WorkingDirectory = Join-Path $repo 'forge-gui'
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in $arguments) { $start.ArgumentList.Add($argument) }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $start
    $wall = [System.Diagnostics.Stopwatch]::StartNew()
    $null = $process.Start()
    $outputTask = $process.StandardOutput.ReadToEndAsync()
    $errorTask = $process.StandardError.ReadToEndAsync()
    $processTimeout = !$process.WaitForExit(($ClockSeconds + 150)*1000)
    if ($processTimeout) { $process.Kill($true); $process.WaitForExit() }
    $wall.Stop()
    $outputText = $outputTask.GetAwaiter().GetResult()
    $errorText = $errorTask.GetAwaiter().GetResult()
    [System.IO.File]::WriteAllText($rawPath,$outputText)
    [System.IO.File]::WriteAllText($errPath,$errorText)
    $result = Get-ArenaResult $outputText $errorText $process.ExitCode $processTimeout
    $row = [ordered]@{id=$id; seed=$seed; seats=$Seats; policies=(@('classic')*$Seats -join ','); profiles=(@('Default')*$Seats -join ','); revision=$sha; control=$control; deckSha256=$deckHash; jarSha256=$jarHash; wallMilliseconds=$wall.ElapsedMilliseconds; quiet=[bool]$Quiet; command=@($java)+$arguments; rawLog=$rawPath; stderrLog=$errPath}
    foreach ($property in $result.PSObject.Properties) { $row[$property.Name]=$property.Value }
    $results += [pscustomobject]$row
    $results | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $arena "evidence/$Label-results.json")
    $results | Select-Object id,seed,seats,status,winnerSeat,lastTurn,gameMilliseconds,wallMilliseconds,hasError | Export-Csv (Join-Path $arena "evidence/$Label-results.csv") -NoTypeInformation
    Write-Output "$id : $($result.status), seat=$($result.winnerSeat), turns=$($result.lastTurn), wall=$($wall.ElapsedMilliseconds)ms"
}
