param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-divine-r1')
$ErrorActionPreference = 'Stop'
$tests = if ($Baseline) { @('DivineReckoningBaselineTests.java') } else { @('DivineReckoningTests.java') }
$gate = @{
    Lab = 'arena/divine-reckoning'
    Base = 'arena/magus-of-the-wheel/target/final-magus-r1'
    Production = @('forge-ai/src/main/java/forge/ai/DivineReckoningAi.java',
        'forge-ai/src/main/java/forge/ai/ability/ChooseCardAi.java')
    Tests = $tests
    Profiles = @('forge-gui/res/ai/overrides/divine-reckoning.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
& "$PSScriptRoot/../version-compat-1.0.28/Check-Gate.ps1" @gate
