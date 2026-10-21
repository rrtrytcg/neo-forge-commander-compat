param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-1.0.27')
$ErrorActionPreference = 'Stop'
$gate = @{
    Lab = 'arena/carpet-of-flowers'
    Base = 'arena/immoral-bargain/target/final-1.0.27'
    Production = @('forge-ai/src/main/java/forge/ai/CarpetOfFlowersAi.java',
        'forge-ai/src/main/java/forge/ai/ability/PermanentNoncreatureAi.java',
        'forge-ai/src/main/java/forge/ai/ability/PumpAi.java')
    Tests = @('CarpetOfFlowersTests.java')
    Profiles = @('forge-gui/res/ai/overrides/carpet-of-flowers.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
& "$PSScriptRoot/../lib/Check-Gate.ps1" @gate
