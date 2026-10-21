param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-drawn-from-dreams-r1')
$ErrorActionPreference = 'Stop'
$tests = if ($Baseline) { @('DrawnFromDreamsBaselineTests.java') } else { @('DrawnFromDreamsTests.java') }
$profilePath = 'forge-gui/res/ai/overrides/drawn-from-dreams.properties'
$gate = @{
    Lab = 'arena/drawn-from-dreams'
    Base = 'arena/mystic-confluence/target/final-mystic-confluence-r2'
    Production = @()
    Tests = $tests
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
if (Test-Path $profilePath) { $gate.Profiles = @($profilePath) }
& "$PSScriptRoot/../version-compat-1.0.28/Check-Gate.ps1" @gate
