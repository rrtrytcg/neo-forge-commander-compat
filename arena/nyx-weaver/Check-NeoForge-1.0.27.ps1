param([switch]$Focused,[switch]$RunOnly,[switch]$Baseline)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$production=@('forge-ai/src/main/java/forge/ai/NyxWeaverAi.java','forge-ai/src/main/java/forge/ai/ComputerUtil.java','forge-ai/src/main/java/forge/ai/ability/PermanentCreatureAi.java','forge-ai/src/main/java/forge/ai/ability/ChangeZoneAi.java')
$lockPath=Join-Path $PSScriptRoot 'target/native-runtime.lock'
try { $runtimeLock=[IO.FileStream]::new($lockPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None,4096,[IO.FileOptions]::DeleteOnClose) }
catch { throw "Another task-owned gate/probe holds the private runtime: $lockPath" }
try {
& (Join-Path $repo 'arena/lib/Check-Gate.ps1') -Lab 'arena/nyx-weaver' -Base 'arena/utility-lands/target/final-1.0.27' -Production $production -Tests $(if($Baseline){'NyxWeaverBaselineTests.java'}else{'NyxWeaverTests.java'}) -Profiles 'forge-gui/res/ai/overrides/nyx-weaver.properties' -OutName 'final-1.0.27' -Focused:$Focused -RunOnly:$RunOnly -Baseline:$Baseline

} finally { $runtimeLock.Dispose() }
