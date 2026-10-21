param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-1.0.28')
$ErrorActionPreference = 'Stop'
$tests = if ($Baseline) { @('GideonJuraBaselineTests.java') } else { @('GideonJuraTests.java') }
$gate = @{
    Lab = 'arena/gideon-jura'
    Base = 'arena/version-compat-1.0.28/target/final-1.0.28'
    Production = @('forge-ai/src/main/java/forge/ai/GideonJuraAi.java',
        'forge-ai/src/main/java/forge/ai/ability/DestroyAi.java',
        'forge-ai/src/main/java/forge/ai/ability/DelayedTriggerAi.java',
        'forge-ai/src/main/java/forge/ai/ability/AnimateAi.java')
    Tests = $tests
    Profiles = @('forge-gui/res/ai/overrides/gideon-jura.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
& 'arena/version-compat-1.0.28/Check-Gate.ps1' @gate
