param(
    [Parameter(Mandatory)][ValidateSet('trading-post','gaze-of-granite')][string]$Card,
    [ValidateSet('on','off')][string]$ProfileMode='on',
    [int]$Seed=97021,
    [ValidateSet('standard','return')][string]$Scenario='standard',
    [ValidateRange(1,120)][int]$ClockSeconds=30
)
$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$gateDir=Join-Path $PSScriptRoot 'target/repair-1.0.25'
$gate=Get-Content (Join-Path $gateDir 'provenance.json') -Raw | ConvertFrom-Json
if($gate.engine -ne 'NeoForge 1.0.25' -or $gate.results.Count -ne 24) { throw 'Run the combined 1.0.25 gate first' }
foreach($file in $gate.files) {
    if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Native verification input changed: $($file.path)" }
}
$testedJar=Join-Path $gateDir 'engine.jar'
$runtimePath=$gate.runtime
$null=New-Item -ItemType Directory -Path (Join-Path $runtimePath 'res/ai/overrides') -Force
Copy-Item -LiteralPath (Join-Path $repoPath "forge-gui/res/ai/overrides/$Card.properties") -Destination (Join-Path $runtimePath "res/ai/overrides/$Card.properties") -Force
$jdkPath=(Get-ChildItem (Join-Path $repoPath 'arena/.tools') -Directory -Filter jdk-* | Select-Object -First 1).FullName
if($Scenario -eq 'return' -and $Card -ne 'trading-post') { throw 'Return scenario is for Trading Post only' }
$label=if($Scenario -eq 'return') { "$Card-return" } else { $Card }
$outPath=Join-Path $PSScriptRoot "target/repair-card-use/$label-$ProfileMode-$Seed"
$null=New-Item -ItemType Directory -Path $outPath -Force
$privateJar=Join-Path $outPath 'candidate.jar'
Copy-Item -LiteralPath $testedJar -Destination $privateJar
$classes=Join-Path $outPath 'classes'; $null=New-Item -ItemType Directory -Path $classes -Force
$observer=Join-Path $PSScriptRoot 'CardUseProbe.java'
& (Join-Path $jdkPath 'bin/javac.exe') -encoding UTF-8 -cp $privateJar -d $classes $observer *> (Join-Path $outPath 'compile.log')
if($LASTEXITCODE -ne 0) { throw 'Probe observer compilation failed' }
$actor=if($Card -eq 'trading-post') { Join-Path $PSScriptRoot 'decks/TradingPost.dck' } else { Join-Path $PSScriptRoot 'decks/Gaze.dck' }
if($Scenario -eq 'return') { $actor=Join-Path $PSScriptRoot 'decks/TradingPostReturn.dck' }
$opponent=Join-Path $PSScriptRoot 'decks/Walls.dck'
$cardName=if($Card -eq 'trading-post') { 'Trading Post' } else { 'Gaze of Granite' }
$logPath=Join-Path $outPath 'probe.log'
$arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtimePath/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$classes;$privateJar",'CardUseProbe',"$Seed",$ProfileMode,$Card,$cardName,"$ClockSeconds",$actor,$opponent)
Push-Location $runtimePath
try { & (Join-Path $jdkPath 'bin/java.exe') @arguments *> $logPath; $nativeExit=$LASTEXITCODE } finally { Pop-Location }
$log=Get-Content -LiteralPath $logPath -Raw
$witnesses=@(Get-Content -LiteralPath $logPath | Where-Object { $_.StartsWith('CARD_WITNESS ') })
$clockReached=$log -match 'CARD_PROBE_CLOCK_STOP'
$diagnostics=@([regex]::Matches($log,'(?m)^[^\r\n]*(?:Exception|StackOverflowError)[^\r\n]*$') | ForEach-Object Value)
$unexpected=@($diagnostics | Where-Object { !$clockReached -or $_ -notmatch '^java\.lang\.InterruptedException\s*$' })
$files=@($privateJar,$observer,$PSCommandPath,$actor,$opponent,$logPath,(Join-Path $repoPath "forge-gui/res/ai/overrides/$Card.properties")) | ForEach-Object { @{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash} }
@{card=$Card;scenario=$Scenario;profile=$ProfileMode;seed=$Seed;clockSeconds=$ClockSeconds;clockReached=$clockReached;nativeExit=$nativeExit;diagnostics=$diagnostics;unexpectedDiagnostics=$unexpected;observations=$witnesses.Count;castWitnesses=@($witnesses|Where-Object {$_ -match '"kind":"cast"'}).Count;resolvedWitnesses=@($witnesses|Where-Object {$_ -match '"kind":"resolved"'}).Count;files=$files;engine=$gate.engine;command=@((Join-Path $jdkPath 'bin/java.exe'))+$arguments;scope='Autonomous native Match with read-only event observer; custom decks, card-use only, no completion/winner/strength acceptance'} | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $outPath 'summary.json')
if($nativeExit -ne 0 -or $unexpected.Count -gt 0 -or $log -notmatch 'CARD_PROBE_DONE') { throw "Probe execution error; inspect $logPath" }
Write-Output "$Card $ProfileMode seed $Seed : casts=$(@($witnesses|Where-Object {$_ -match '"kind":"cast"'}).Count), resolved=$(@($witnesses|Where-Object {$_ -match '"kind":"resolved"'}).Count)"
