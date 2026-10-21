param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-1.0.27')
$ErrorActionPreference = 'Stop'
$gate = @{
    Lab = 'arena/moonlight-bargain'
    Base = 'arena/jolrael/target/final-1.0.27'
    Production = @('forge-ai/src/main/java/forge/ai/MoonlightBargainAi.java',
        'forge-ai/src/main/java/forge/ai/SpellAbilityAi.java',
        'forge-ai/src/main/java/forge/ai/ability/ChangeZoneAi.java',
        'forge-ai/src/main/java/forge/ai/ComputerUtil.java')
    Tests = @('MoonlightBargainTests.java')
    Profiles = @('forge-gui/res/ai/overrides/moonlight-bargain.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
& "$PSScriptRoot/../lib/Check-Gate.ps1" @gate
