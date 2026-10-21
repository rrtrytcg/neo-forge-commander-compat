param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-1.0.27')
$ErrorActionPreference = 'Stop'
$gate = @{
    Lab = 'arena/jolrael'
    Base = 'arena/easy-cards-3/target/final-1.0.27'
    Production = @('forge-ai/src/main/java/forge/ai/JolraelAi.java',
        'forge-ai/src/main/java/forge/ai/CombatBaseStatsAi.java',
        'forge-ai/src/main/java/forge/ai/ability/AnimateAllAi.java',
        'forge-ai/src/main/java/forge/ai/ComputerUtil.java')
    Tests = @('JolraelTests.java')
    Profiles = @('forge-gui/res/ai/overrides/jolrael.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
& "$PSScriptRoot/../lib/Check-Gate.ps1" @gate
