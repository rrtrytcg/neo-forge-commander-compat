<#
  Shared real-deck Commander smoke on a sealed lab gate candidate. Read-only observation from the
  native game log: counts lines matching each -Count regex. Run the same seed with -ProfileMode off
  and on; card-use evidence only, never a winner or strength claim.

  Example:
    ./arena/lib/Run-Smoke.ps1 -Gate arena/utility-lands/target/gate-1.0.27 -Profile utility-lands -ProfileMode on `
      -Decks 'Draconic Rage [AFC] [2021]','Aura of Courage [AFC] [2021]','Arcane Maelstrom [AFC] [2021]','Dungeons of Death [AFC] [2021]' `
      -Seed 115011 -Count @{crucible='activated Crucible of the Spirit Dragon'; casts='cast .*Dragon'}
  Decks are file names (with or without .dck) under the candidate runtime's res/quest/commanderprecons,
  or full paths. The first deck is seat 1.
#>
param(
    [Parameter(Mandatory)][string]$Gate,
    [Parameter(Mandatory)][string]$Profile,
    [ValidateSet('on', 'off')][string]$ProfileMode = 'on',
    [Parameter(Mandatory)][string[]]$Decks,
    [int]$Seed = 1,
    [ValidateRange(30, 900)][int]$ClockSeconds = 180,
    [hashtable]$Count = @{},
    [string]$Label = 'smoke'
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$gatePath = (Resolve-Path $(if ([IO.Path]::IsPathRooted($Gate)) { $Gate } else { Join-Path $repo $Gate })).Path
$gp = Get-Content (Join-Path $gatePath 'provenance.json') -Raw | ConvertFrom-Json
if ($gp.engine -ne 'NeoForge 1.0.27' -or $gp.baseline) { throw 'Run the full (non-baseline) gate first' }
foreach ($f in $gp.files) { if ((Get-FileHash $f.path).Hash -ne $f.sha256) { throw "Gate input changed since sealing: $($f.path)" } }
if ((Get-FileHash $gp.candidate).Hash -ne $gp.candidateSha256) { throw 'Candidate changed' }
$jdk = (Get-ChildItem (Join-Path $repo 'arena/.tools') -Directory -Filter 'jdk-*' | Select-Object -First 1).FullName
$precons = Join-Path $gp.runtime 'res/quest/commanderprecons'
$deckFiles = @($Decks | ForEach-Object { $d = if ($_ -like '*.dck') { $_ } else { "$_.dck" }; if ([IO.Path]::IsPathRooted($d)) { $d } else { Join-Path $precons $d } })
foreach ($d in $deckFiles) { if (!(Test-Path -LiteralPath $d)) { throw "Deck not found: $d" } }
$dirs = @($deckFiles | ForEach-Object { Split-Path $_ } | Select-Object -Unique)
if ($dirs.Count -ne 1) { throw 'All decks must be in one folder (sim -D)' }
$out = Join-Path (Split-Path $gatePath) "$Label/$ProfileMode-$Seed"; $null = New-Item -ItemType Directory -Force $out
$arguments = @('-Xmx3g', '-Djava.awt.headless=true', '-Dfile.encoding=UTF-8', "-Duser.home=$($gp.runtime)/home", '-Dsentry.dsn=', '-Dsentry.enabled=false',
    '-cp', $gp.candidate, 'forge.view.Main', 'sim')
if ($ProfileMode -eq 'on') { $arguments += "--ai-profile=$Profile" }
$arguments += @('-D', $dirs[0], '-d') + @($deckFiles | ForEach-Object { Split-Path $_ -Leaf }) +
    @('-f', 'Commander', '-n', '1', '-c', "$ClockSeconds", '-s', "$Seed", '-a') + @($deckFiles | ForEach-Object { 'Default' })
$log = Join-Path $out 'game.log'
Push-Location $gp.runtime
try { & "$jdk/bin/java.exe" @arguments *> $log; $exit = $LASTEXITCODE } finally { Pop-Location }
$lines = @(Get-Content $log)
$counts = [ordered]@{}; $examples = [ordered]@{}
foreach ($k in ($Count.Keys | Sort-Object)) {
    $hits = @($lines | Where-Object { $_ -match $Count[$k] })
    $counts[$k] = $hits.Count; $examples[$k] = @($hits | Select-Object -First 5)
}
$summary = [ordered]@{
    profile = $Profile; mode = $ProfileMode; seed = $Seed; clockSeconds = $ClockSeconds; exit = $exit
    decks = @($deckFiles | ForEach-Object { Split-Path $_ -Leaf }); counts = $counts; examples = $examples
    lastTurn = (@($lines | Where-Object { $_ -match '^Turn: Turn (\d+)' } | ForEach-Object { [int]($_ -replace '^Turn: Turn (\d+).*', '$1') }) | Measure-Object -Maximum).Maximum
    result = @($lines | Where-Object { $_ -match '^Game Result:|Stopping slow match' }) | Select-Object -Last 1
    exceptions = @($lines | Where-Object { $_ -match 'Exception' -and $_ -notmatch 'InterruptedException' }).Count
    candidateSha256 = $gp.candidateSha256; log = $log; logSha256 = (Get-FileHash $log).Hash
    command = @("$jdk/bin/java.exe") + $arguments
    scope = 'Card-use smoke with unchanged precon decks; no winner or strength claim'
}
$summary | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $out 'summary.json')
"{0} {1} seed {2}: {3} | turn {4} | exceptions {5} | {6}" -f $Profile, $ProfileMode, $Seed, (($counts.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ' '), $summary.lastTurn, $summary.exceptions, $summary.result
