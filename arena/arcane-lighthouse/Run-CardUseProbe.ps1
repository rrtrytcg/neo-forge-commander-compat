param(
    [ValidateSet('lighthouse')][string]$Scenario='lighthouse',
    [ValidateSet('on','off')][string]$ProfileMode='on',
    [int]$Seed=110011,
    [ValidateRange(1,120)][int]$ClockSeconds=30
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
if($gate.engine -ne 'NeoForge 1.0.25' -or $gate.results.Count -ne 37) {throw 'Run the complete native gate first'}
foreach($file in $gate.files) {if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) {throw "Native input changed: $($file.path)"}}
if((Get-FileHash $gate.candidate).Hash -ne $gate.candidateSha256) {throw 'Candidate changed'}
$jdk=(Get-ChildItem (Join-Path $repo 'arena/.tools') -Directory -Filter jdk-*|Select-Object -First 1).FullName
$out=Join-Path $PSScriptRoot "target/card-use/$Scenario-$ProfileMode-$Seed"
$null=New-Item -ItemType Directory -Force $out,(Join-Path $out 'classes')
$candidate=Join-Path $out 'candidate.jar'; Copy-Item -LiteralPath $gate.candidate -Destination $candidate
$observer=Join-Path $PSScriptRoot 'LighthouseProbe.java'
& (Join-Path $jdk 'bin/javac.exe') -encoding UTF-8 -cp $candidate -d (Join-Path $out 'classes') $observer *> (Join-Path $out 'compile.log')
if($LASTEXITCODE) {throw 'Observer compilation failed'}
$actor=Join-Path $PSScriptRoot 'decks/Actor.dck'
$opponent=Join-Path $PSScriptRoot 'decks/Opponents.dck'
$log=Join-Path $out 'probe.log'
$argsJava=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$($gate.runtime)/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$out/classes;$candidate",'LighthouseProbe',"$Seed",$ProfileMode,"$ClockSeconds",$actor,$opponent)
Push-Location $gate.runtime
try {& (Join-Path $jdk 'bin/java.exe') @argsJava *> $log; $nativeExit=$LASTEXITCODE} finally {Pop-Location}
$raw=Get-Content $log -Raw
$rows=@(Get-Content $log|Where-Object {$_.StartsWith('LIGHTHOUSE_WITNESS ')}|ForEach-Object {$_.Substring(19)|ConvertFrom-Json})
$clock=$raw -match 'LIGHTHOUSE_PROBE_CLOCK_STOP'
$diagnostics=@([regex]::Matches($raw,'(?m)^[^\r\n]*(?:Exception|StackOverflowError)[^\r\n]*$')|ForEach-Object Value)
$unexpected=@($diagnostics|Where-Object {!$clock -or $_ -notmatch '^java\.lang\.InterruptedException\s*$'})
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;scenario=$Scenario;profile=$ProfileMode;seed=$Seed;clockSeconds=$ClockSeconds;clockReached=$clock;nativeExit=$nativeExit;
 diagnostics=$diagnostics;unexpectedDiagnostics=$unexpected;casts=@($rows|Where-Object {$_.kind -eq 'cast' -and $_.spell}).Count;
 resolved=@($rows|Where-Object kind -eq 'resolved').Count;
 files=@($candidate,$observer,$PSCommandPath,$actor,$opponent,$log|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});command=@((Join-Path $jdk 'bin/java.exe'))+$argsJava;
 scope='Autonomous native custom-deck card use with read-only observer; no completion, winner or strength claim'}|ConvertTo-Json -Depth 14|Set-Content (Join-Path $out 'summary.json')
if($nativeExit -ne 0 -or $unexpected.Count -or $raw -notmatch 'LIGHTHOUSE_PROBE_DONE') {throw "Probe failed: $log"}
Write-Output "$Scenario $ProfileMode seed $Seed : $(@($rows|Where-Object kind -eq 'resolved').Count) resolved actor spells"
