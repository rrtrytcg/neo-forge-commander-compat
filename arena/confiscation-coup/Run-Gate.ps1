param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-1.0.27')
$ErrorActionPreference = 'Stop'
$tests = if ($Baseline) { @('ConfiscationCoupBaselineTests.java') } else { @('ConfiscationCoupTests.java') }
$gate = @{
    Lab = 'arena/confiscation-coup'
    Base = 'arena/choice-wipes/target/final-1.0.27-r2'
    Production = @('forge-ai/src/main/java/forge/ai/ConfiscationCoupAi.java',
        'forge-ai/src/main/java/forge/ai/ability/CountersPutAi.java',
        'forge-ai/src/main/java/forge/ai/ability/ControlGainAi.java')
    Tests = $tests
    Profiles = @('forge-gui/res/ai/overrides/confiscation-coup.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
if (!$Baseline -and !$Focused) {
    # The user has a 1.0.28 install under active compatibility testing. Preserve that exact
    # installed state across this isolated 1.0.27 candidate run instead of comparing it to 1.0.27.
    & python -I "$PSScriptRoot/Write-InstalledSnapshot.py" capture
    if ($LASTEXITCODE) { throw 'Could not capture current installed-file hashes' }
    $tools = Join-Path $PSScriptRoot 'target/gate-tools'
    $null = New-Item -ItemType Directory -Force $tools
    $checker = Join-Path $tools 'Check-Gate.ps1'
    $shared = Get-Content (Join-Path $PSScriptRoot '../lib/Check-Gate.ps1') -Raw
    $shared = $shared.Replace("(Join-Path `$PSScriptRoot '../..')", "(Join-Path `$PSScriptRoot '../../../..')")
    $shared = $shared.Replace("(Join-Path `$repo 'arena/version-compat-1.0.27/target/preserved-baseline.json')", "(Join-Path `$repo 'arena/confiscation-coup/target/installed-snapshot.json')")
    Set-Content -LiteralPath $checker -Value $shared -Encoding utf8
    & $checker @gate
    if ($LASTEXITCODE) { throw 'Full private 1.0.27 gate failed' }
    & python -I "$PSScriptRoot/Write-InstalledSnapshot.py" verify
    if ($LASTEXITCODE) { throw 'Installed-file snapshot changed during the private gate' }
} else {
    & "$PSScriptRoot/../lib/Check-Gate.ps1" @gate
}
