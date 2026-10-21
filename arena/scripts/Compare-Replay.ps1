param([string]$FirstLog='baseline-4-1002.raw.log',[string]$SecondLog='replay-4-1002.raw.log')
$arena = Split-Path $PSScriptRoot -Parent
# Match only gameplay-log lines. Keep outcomes; omit elapsed duration and JVM/card-load diagnostics.
$prefixes = '^(Game Outcome|Match Result|Turn|Mulligan|Ante|Draft|Zone Change|Player Control|Damage|Life|Land|Discard|Combat|Information|Resolve Stack|Add To Stack|Replacement Effect|Mana|Phase):'
function Get-GameplayTrace([string]$path) {
    @(Get-Content $path | Where-Object { $_ -match $prefixes -and $_ -notmatch '^Game Outcome: Match Duration' })
}
$first = Get-GameplayTrace (Join-Path $arena "evidence/$FirstLog")
$second = Get-GameplayTrace (Join-Path $arena "evidence/$SecondLog")
if (!$first.Count -or !$second.Count) { throw 'Missing gameplay trace' }
$differences = @(Compare-Object $first $second -SyncWindow 0)
$result = @{first=$FirstLog; second=$SecondLog; firstLines=$first.Count; secondLines=$second.Count; exactGameplayTraceMatch=($differences.Count -eq 0); differingLines=$differences.Count; note='Checks unchanged-engine seeded repeatability for this seed only. Excludes timing, initialization diagnostics and the command header. Does not establish treatment parity or independent random streams.'}
$result | ConvertTo-Json | Set-Content (Join-Path $arena 'evidence/replay-comparison.json')
$result | ConvertTo-Json
