param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-mystic-confluence-r2')
$ErrorActionPreference = 'Stop'
$tests = if ($Baseline) { @('MysticConfluenceBaselineTests.java') } else { @('MysticConfluenceTests.java') }
$gate = @{
    Lab = 'arena/mystic-confluence'
    Base = 'arena/daretti/target/final-daretti-r1'
    Production = @('forge-ai/src/main/java/forge/ai/MysticConfluenceAi.java',
        'forge-ai/src/main/java/forge/ai/ability/CharmAi.java')
    Tests = $tests
    Profiles = @('forge-gui/res/ai/overrides/mystic-confluence.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
& "$PSScriptRoot/../version-compat-1.0.28/Check-Gate.ps1" @gate
