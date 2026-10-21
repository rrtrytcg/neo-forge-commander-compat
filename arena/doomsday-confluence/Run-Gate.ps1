param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-1.0.27')
$ErrorActionPreference = 'Stop'
$gate = @{
    Lab = 'arena/doomsday-confluence'
    Base = 'arena/moonlight-bargain/target/final-1.0.27'
    Production = @('forge-ai/src/main/java/forge/ai/DoomsdayConfluenceAi.java',
        'forge-ai/src/main/java/forge/ai/SpellAbilityAi.java',
        'forge-ai/src/main/java/forge/ai/ComputerUtil.java')
    Tests = @('DoomsdayConfluenceTests.java')
    Profiles = @('forge-gui/res/ai/overrides/doomsday-confluence.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
& "$PSScriptRoot/../lib/Check-Gate.ps1" @gate
