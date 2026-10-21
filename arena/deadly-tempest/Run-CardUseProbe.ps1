param([ValidateSet('ordinary','protected')][string]$Scenario='ordinary',[ValidateSet('on','off')][string]$ProfileMode='on',[int]$Seed=113011,[ValidateRange(1,120)][int]$ClockSeconds=30)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.27/provenance.json') -Raw|ConvertFrom-Json
if($gate.engine -ne 'NeoForge 1.0.27' -or $gate.results.Count -ne 41){throw 'Complete native gate first'}
foreach($f in $gate.files){if((Get-FileHash $f.path).Hash -ne $f.sha256){throw "Native input changed: $($f.path)"}}
if((Get-FileHash $gate.candidate).Hash -ne $gate.candidateSha256){throw 'Candidate changed'}
$jdk=(Get-ChildItem (Join-Path $repo 'arena/.tools') -Directory -Filter jdk-*|Select-Object -First 1).FullName
$out=Join-Path $PSScriptRoot "target/card-use/$Scenario-$ProfileMode-$Seed";$null=New-Item -ItemType Directory -Force $out,(Join-Path $out 'classes')
$candidate=Join-Path $out 'candidate.jar';Copy-Item $gate.candidate $candidate
$observer=Join-Path $PSScriptRoot 'DeadlyTempestProbe.java'
& "$jdk/bin/javac.exe" -encoding UTF-8 -cp $candidate -d (Join-Path $out 'classes') $observer *> (Join-Path $out 'compile.log');if($LASTEXITCODE){throw 'Observer compile failed'}
$actor=Join-Path $PSScriptRoot 'decks/Actor.dck';$opponent=Join-Path $PSScriptRoot "decks/Opponents-$Scenario.dck";$log=Join-Path $out 'probe.log'
$arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$($gate.runtime)/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$out/classes;$candidate",'DeadlyTempestProbe',"$Seed",$ProfileMode,"$ClockSeconds",$actor,$opponent)
Push-Location $gate.runtime
try{& "$jdk/bin/java.exe" @arguments *> $log;$nativeExit=$LASTEXITCODE}finally{Pop-Location}
$raw=Get-Content $log -Raw;$clock=$raw -match 'TEMPEST_PROBE_CLOCK_STOP'
$diagnostics=@([regex]::Matches($raw,'(?m)^[^\r\n]*(?:Exception|StackOverflowError)[^\r\n]*$')|ForEach-Object Value)
$unexpected=@($diagnostics|Where-Object {!$clock -or $_ -notmatch '^java\.lang\.InterruptedException\s*$'})
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;scenario=$Scenario;profile=$ProfileMode;seed=$Seed;clockSeconds=$ClockSeconds;clockReached=$clock;nativeExit=$nativeExit;diagnostics=$diagnostics;unexpectedDiagnostics=$unexpected;
 files=@($PSCommandPath,$candidate,$observer,$actor,$opponent,$log|ForEach-Object {@{path=$_;sha256=(Get-FileHash $_).Hash}});command=@("$jdk/bin/java.exe")+$arguments;
 scope='Autonomous Commander card use with read-only native observer; intentionally invalid repeated-card decks, no completion/winner/strength claim'}|ConvertTo-Json -Depth 10|Set-Content (Join-Path $out 'summary.json')
if($nativeExit -or $unexpected.Count -or $raw -notmatch 'TEMPEST_PROBE_DONE'){throw "Probe failed: $log"}
Write-Output "$Scenario $ProfileMode $Seed observation recorded"
