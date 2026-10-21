param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-daretti-r1')
$ErrorActionPreference = 'Stop'
$tests = if ($Baseline) { @('DarettiBaselineTests.java') } else { @('DarettiTests.java') }
$gate = @{
    Lab = 'arena/daretti'
    Base = 'arena/divine-reckoning/target/final-divine-r1'
    Production = @('forge-ai/src/main/java/forge/ai/DarettiAi.java',
        'forge-ai/src/main/java/forge/ai/ability/DiscardAi.java',
        'forge-ai/src/main/java/forge/ai/ability/SacrificeAi.java')
    Tests = $tests
    Profiles = @('forge-gui/res/ai/overrides/daretti.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
& "$PSScriptRoot/../version-compat-1.0.28/Check-Gate.ps1" @gate
