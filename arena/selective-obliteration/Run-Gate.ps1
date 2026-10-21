param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-1.0.28')
$ErrorActionPreference = 'Stop'
$gate = @{
    Lab = 'arena/selective-obliteration'
    Base = 'arena/version-compat-1.0.28/target/final-1.0.28'
    Production = @('forge-ai/src/main/java/forge/ai/SelectiveObliterationAi.java',
        'forge-ai/src/main/java/forge/ai/ComputerUtilCard.java',
        'forge-ai/src/main/java/forge/ai/ability/RepeatEachAi.java')
    Tests = @('SelectiveObliterationTests.java')
    Profiles = @('forge-gui/res/ai/overrides/selective-obliteration.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
& "$PSScriptRoot/../version-compat-1.0.28/Check-Gate.ps1" @gate
