param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-1.0.27')
$ErrorActionPreference = 'Stop'
$gate = @{
    Lab = 'arena/immoral-bargain'
    Base = 'arena/doomsday-confluence/target/final-1.0.27'
    Production = @('forge-ai/src/main/java/forge/ai/ImmoralBargainAi.java',
        'forge-ai/src/main/java/forge/ai/ability/DestroyAi.java',
        'forge-ai/src/main/java/forge/ai/ComputerUtil.java')
    Tests = @('ImmoralBargainTests.java')
    Profiles = @('forge-gui/res/ai/overrides/immoral-bargain.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
& "$PSScriptRoot/../lib/Check-Gate.ps1" @gate
