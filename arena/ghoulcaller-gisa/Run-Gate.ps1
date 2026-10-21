param([switch]$Baseline, [switch]$Focused, [string]$OutName = 'final-1.0.27')
$ErrorActionPreference = 'Stop'
$gate = @{
    Lab = 'arena/ghoulcaller-gisa'
    Base = 'arena/carpet-of-flowers/target/final-1.0.27'
    Production = @('forge-ai/src/main/java/forge/ai/GhoulcallerGisaAi.java',
        'forge-ai/src/main/java/forge/ai/ability/TokenAi.java')
    Tests = @('GhoulcallerGisaTests.java')
    Profiles = @('forge-gui/res/ai/overrides/ghoulcaller-gisa.properties')
    OutName = $OutName
    Baseline = $Baseline
    Focused = $Focused
}
& "$PSScriptRoot/../lib/Check-Gate.ps1" @gate
