param(
    [ValidateSet('on','off')][string]$ProfileMode='on',
    [int]$Seed=96801,
    [ValidateRange(1,120)][int]$ClockSeconds=30
)
$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$gateDir=if(Test-Path (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json')) { Join-Path $PSScriptRoot 'target/final-1.0.25' } else { Join-Path $PSScriptRoot 'target/final' }
$gate=Get-Content (Join-Path $gateDir 'provenance.json') -Raw | ConvertFrom-Json
foreach($file in $gate.files) {
    if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Native verification input changed: $($file.path)" }
}
$testedJar=Join-Path $gateDir 'engine.jar'
$runtimePath=$gate.runtime
$null=New-Item -ItemType Directory -Path (Join-Path $runtimePath 'res/ai/overrides') -Force
Copy-Item -LiteralPath (Join-Path $repoPath 'forge-gui/res/ai/overrides/assault-suit.properties') -Destination (Join-Path $runtimePath 'res/ai/overrides/assault-suit.properties') -Force
$jdkPath=(Get-ChildItem (Join-Path $repoPath 'arena/.tools') -Directory -Filter jdk-* | Select-Object -First 1).FullName
$outPath=Join-Path $PSScriptRoot "target/card-use/$ProfileMode-$Seed"
$null=New-Item -ItemType Directory -Path $outPath -Force
$privateJar=Join-Path $outPath 'candidate.jar'
Copy-Item -LiteralPath $testedJar -Destination $privateJar
$classes=Join-Path $outPath 'classes'; $null=New-Item -ItemType Directory -Path $classes -Force
$observer=Join-Path $PSScriptRoot 'AssaultSuitProbe.java'
& (Join-Path $jdkPath 'bin/javac.exe') -encoding UTF-8 -cp $privateJar -d $classes $observer *> (Join-Path $outPath 'compile.log')
if($LASTEXITCODE -ne 0) { throw 'Probe observer compilation failed' }
$actor=Join-Path $PSScriptRoot 'decks/Arena.dck'
$opponent=Join-Path $PSScriptRoot 'decks/Opponent.dck'
$logPath=Join-Path $outPath 'probe.log'
$arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtimePath/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$classes;$privateJar",'AssaultSuitProbe',"$Seed",$ProfileMode,"$ClockSeconds",$actor,$opponent)
Push-Location $runtimePath
try { & (Join-Path $jdkPath 'bin/java.exe') @arguments *> $logPath; $nativeExit=$LASTEXITCODE } finally { Pop-Location }
$log=Get-Content -LiteralPath $logPath -Raw
$rows=@(Get-Content -LiteralPath $logPath | Where-Object { $_.StartsWith('SUIT_WITNESS ') } | ForEach-Object { $_.Substring(13) | ConvertFrom-Json })
$clockReached=$log -match 'SUIT_PROBE_CLOCK_STOP'
$diagnostics=@([regex]::Matches($log,'(?m)^[^\r\n]*(?:Exception|StackOverflowError)[^\r\n]*$') | ForEach-Object Value)
$unexpected=@($diagnostics | Where-Object { !$clockReached -or $_ -notmatch '^java\.lang\.InterruptedException\s*$' })
$files=@($privateJar,$observer,$PSCommandPath,$actor,$opponent,$logPath,(Join-Path $repoPath 'forge-gui/res/ai/overrides/assault-suit.properties')) | ForEach-Object { @{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash} }
@{profile=$ProfileMode;seed=$Seed;clockSeconds=$ClockSeconds;clockReached=$clockReached;nativeExit=$nativeExit;diagnostics=$diagnostics;unexpectedDiagnostics=$unexpected;observations=$rows;files=$files;engine=$gate.engine;command=@((Join-Path $jdkPath 'bin/java.exe'))+$arguments;scope='Autonomous native Match with read-only event observer; custom decks, card-use only, no completion/winner/strength acceptance'} | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $outPath 'summary.json')
if($nativeExit -ne 0 -or $unexpected.Count -gt 0 -or $log -notmatch 'SUIT_PROBE_DONE') { throw "Probe execution error; inspect $logPath" }
Write-Output "$ProfileMode seed $Seed : casts=$(@($rows | Where-Object kind -eq 'cast').Count), equips=$(@($rows | Where-Object kind -eq 'equip').Count), triggers=$(@($rows | Where-Object kind -eq 'trigger').Count), attachments=$(@($rows | Where-Object kind -eq 'attached').Count)"
