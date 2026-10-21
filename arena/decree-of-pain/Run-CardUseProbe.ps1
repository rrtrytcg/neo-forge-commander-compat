param(
    [ValidateSet('cast','cycle')][string]$Mode='cast',
    [ValidateSet('on','off')][string]$ProfileMode='on',
    [int]$Seed=95771,
    [ValidateRange(1,60)][int]$ClockSeconds=20
)
$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtimePath=(Join-Path $repoPath 'arena/runtime').Replace('\','/')
$profileText=Get-Content (Join-Path $repoPath 'forge-gui/forge.profile.properties') -Raw
foreach($key in @('userDir','cacheDir','decksDir')) {
    if($profileText -notmatch "(?m)^$key=$([regex]::Escape($runtimePath))/") { throw 'Expected isolated arena runtime profile' }
}
$verification=Get-Content (Join-Path $PSScriptRoot 'verification-summary.json') -Raw | ConvertFrom-Json
$originalJar=@($verification.nativeEvidence.files | Where-Object { $_.path -match '[\\/]engine\.jar$' })
if($originalJar.Count -ne 1) { throw 'Missing original native-tested jar provenance' }
$testedJar=Join-Path $PSScriptRoot 'target/final/engine.jar'
if((Get-FileHash -LiteralPath $testedJar).Hash -ne $originalJar[0].sha256) { throw 'Native-tested jar changed or unavailable' }
# Match every recorded production/test/profile input before reusing this correctness gate.
foreach($file in $verification.nativeEvidence.files) {
    if($file.path -notmatch '[\\/]engine\.jar$' -and (Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) {
        throw "Changed native verification input: $($file.path)"
    }
}
$jdkPath=(Get-ChildItem (Join-Path $repoPath 'arena/.tools') -Directory -Filter jdk-* | Select-Object -First 1).FullName
$outPath=Join-Path $PSScriptRoot "target/card-use/$Mode-$ProfileMode-$Seed"
$null=New-Item -ItemType Directory -Path $outPath -Force
$privateJar=Join-Path $outPath 'candidate.jar'
Copy-Item -LiteralPath $testedJar -Destination $privateJar
$deckPath=Join-Path $PSScriptRoot 'decks'
$targets=if($Mode -eq 'cast') { 'CastTargets.dck' } else { 'CycleTargets.dck' }
$logPath=Join-Path $outPath 'probe.log'
$arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtimePath/home",'-Dneoforge.aiProfile=','-Dsentry.dsn=','-Dsentry.enabled=false','-cp',$privateJar,'forge.view.Main','sim')
if($ProfileMode -eq 'on') { $arguments+='--ai-profile=decree-of-pain' }
$arguments+=@('-f','Constructed','-n','1','-c',"$ClockSeconds",'-s',"$Seed",'-D',$deckPath,'-d','DecreeProbe.dck',$targets,$targets,$targets,'-a','Default','Default','Default','Default','-g','classic','classic','classic','classic','-p','classic','classic','classic','classic','-y','classic','classic','classic','classic')
Push-Location (Join-Path $repoPath 'forge-gui')
try {
    & (Join-Path $jdkPath 'bin/java.exe') @arguments *> $logPath
    $processExit=$LASTEXITCODE
} finally { Pop-Location }
$log=Get-Content -LiteralPath $logPath -Raw
$castPattern='(?m)^Add To Stack: .* cast Decree of Pain\s*$'
# Decree's only activated ability in its original script is cycling.
$cyclePattern='(?m)^Add To Stack: .* activated Decree of Pain\s*$'
$triggerPattern='(?m)^Add To Stack: .* triggered Decree of Pain\s*$'
$castCount=[regex]::Matches($log,$castPattern).Count
$cycleCount=[regex]::Matches($log,$cyclePattern).Count
$triggerCount=[regex]::Matches($log,$triggerPattern).Count
$clockReached=($log -match 'Stopping slow match as draw')
$diagnostics=@([regex]::Matches($log,'(?m)^[^\r\n]*(?:Exception|StackOverflowError)[^\r\n]*$') | ForEach-Object Value)
$unexpected=@($diagnostics | Where-Object { !$clockReached -or $_ -notmatch '^java\.lang\.InterruptedException\s*$' })
$errors=($processExit -ne 0 -or $unexpected.Count -gt 0)
$files=@($privateJar,$PSCommandPath,(Join-Path $repoPath 'forge-gui/res/ai/overrides/decree-of-pain.properties'),(Join-Path $deckPath 'DecreeProbe.dck'),(Join-Path $deckPath $targets),$logPath) | ForEach-Object { @{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash} }
@{mode=$Mode;profile=$ProfileMode;seed=$Seed;clockSeconds=$ClockSeconds;casts=$castCount;cyclingActivations=$cycleCount;cyclingTriggers=$triggerCount;clockReached=$clockReached;clockStopInterruption=($clockReached -and $diagnostics -contains 'java.lang.InterruptedException');diagnostics=$diagnostics;executionError=$errors;nativeExitCode=$processExit;files=$files;command=@((Join-Path $jdkPath 'bin/java.exe'))+$arguments;scope='Autonomous card-use witness in deliberately nonstandard custom decks; completion and winner are not acceptance criteria; no strength or Commander-use claim'} | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outPath 'summary.json')
if($errors) { throw "Probe execution error; inspect $logPath" }
Write-Output "$Mode/$ProfileMode seed $Seed : casts=$castCount cycles=$cycleCount cyclingTriggers=$triggerCount"
