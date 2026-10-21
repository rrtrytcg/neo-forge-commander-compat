param(
    [ValidateSet('kicked','unkicked')][string]$Mode='kicked',
    [ValidateSet('on','off')][string]$ProfileMode='on',
    [int]$Seed=96231,
    [ValidateRange(1,60)][int]$ClockSeconds=20
)
$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtimePath=(Join-Path $repoPath 'arena/runtime').Replace('\','/')
$profileText=Get-Content (Join-Path $repoPath 'forge-gui/forge.profile.properties') -Raw
foreach($key in @('userDir','cacheDir','decksDir')) {
    if($profileText -notmatch "(?m)^$key=$([regex]::Escape($runtimePath))/") { throw 'Expected isolated arena runtime profile' }
}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw | ConvertFrom-Json
foreach($file in $gate.files) {
    if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Native verification input changed: $($file.path)" }
}
$testedJar=Join-Path $PSScriptRoot 'target/final/engine.jar'
$jdkPath=(Get-ChildItem (Join-Path $repoPath 'arena/.tools') -Directory -Filter jdk-* | Select-Object -First 1).FullName
$outPath=Join-Path $PSScriptRoot "target/card-use/$Mode-$ProfileMode-$Seed"
$null=New-Item -ItemType Directory -Path $outPath -Force
$privateJar=Join-Path $outPath 'candidate.jar'
Copy-Item -LiteralPath $testedJar -Destination $privateJar
$classes=Join-Path $outPath 'classes'; $null=New-Item -ItemType Directory -Path $classes -Force
$observer=Join-Path $PSScriptRoot 'MarshalsAnthemProbe.java'
& (Join-Path $jdkPath 'bin/javac.exe') -encoding UTF-8 -cp $privateJar -d $classes $observer *> (Join-Path $outPath 'compile.log')
if($LASTEXITCODE -ne 0) { throw 'Probe observer compilation failed' }
$deckPath=Join-Path $PSScriptRoot 'decks'
$actor=Join-Path $deckPath $(if($Mode -eq 'kicked') { 'Kicked.dck' } else { 'Unkicked.dck' })
$opponent=Join-Path $deckPath 'Walls.dck'
$logPath=Join-Path $outPath 'probe.log'
$arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtimePath/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$classes;$privateJar",'MarshalsAnthemProbe',"$Seed",$ProfileMode,"$ClockSeconds",$actor,$opponent)
Push-Location (Join-Path $repoPath 'forge-gui')
try { & (Join-Path $jdkPath 'bin/java.exe') @arguments *> $logPath; $nativeExit=$LASTEXITCODE } finally { Pop-Location }
$log=Get-Content -LiteralPath $logPath -Raw
$rows=@(Get-Content -LiteralPath $logPath | Where-Object { $_.StartsWith('ANTHEM_WITNESS ') } | ForEach-Object { $_.Substring(15) | ConvertFrom-Json })
$clockReached=$log -match 'ANTHEM_PROBE_CLOCK_STOP'
$diagnostics=@([regex]::Matches($log,'(?m)^[^\r\n]*(?:Exception|StackOverflowError)[^\r\n]*$') | ForEach-Object Value)
$unexpected=@($diagnostics | Where-Object { !$clockReached -or $_ -notmatch '^java\.lang\.InterruptedException\s*$' })
$files=@($privateJar,$observer,$PSCommandPath,$actor,$opponent,$logPath,(Join-Path $repoPath 'forge-gui/res/ai/overrides/marshals-anthem.properties')) | ForEach-Object { @{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash} }
@{mode=$Mode;profile=$ProfileMode;seed=$Seed;clockSeconds=$ClockSeconds;clockReached=$clockReached;nativeExit=$nativeExit;diagnostics=$diagnostics;unexpectedDiagnostics=$unexpected;observations=$rows;files=$files;command=@((Join-Path $jdkPath 'bin/java.exe'))+$arguments;scope='Autonomous native Match with read-only event observer; custom decks, card-use only, no completion/winner/strength acceptance'} | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $outPath 'summary.json')
if($nativeExit -ne 0 -or $unexpected.Count -gt 0 -or $log -notmatch 'ANTHEM_PROBE_DONE') { throw "Probe execution error; inspect $logPath" }
Write-Output "$ProfileMode seed $Seed : stacked=$(@($rows | Where-Object stage -eq 'stacked').Count), resolutions=$(@($rows | Where-Object stage -eq 'resolved').Count)"
