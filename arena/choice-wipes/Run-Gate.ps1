param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-1.0.27')
$ErrorActionPreference = 'Stop'
$tests = if ($Baseline) { @('ChoiceWipesBaselineTests.java') } else { @('ChoiceWipesTests.java') }
$gate = @{
    Lab = 'arena/choice-wipes'
    Base = 'arena/ghoulcaller-gisa/target/final-1.0.27'
    Production = @('forge-ai/src/main/java/forge/ai/ChoiceWipesAi.java',
        'forge-ai/src/main/java/forge/ai/ability/RepeatEachAi.java')
    Tests = $tests
    Profiles = @('forge-gui/res/ai/overrides/choice-wipes.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
& "$PSScriptRoot/../lib/Check-Gate.ps1" @gate
