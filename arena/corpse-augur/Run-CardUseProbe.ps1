param([ValidateSet('on','off')][string]$ProfileMode='on',[int]$Seed=96451,[ValidateRange(1,60)][int]$ClockSeconds=20)
$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw|ConvertFrom-Json
foreach($file in $gate.files) { if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Native input changed: $($file.path)" } }
if((Get-FileHash $gate.candidate).Hash -ne $gate.candidateSha256) { throw 'Native candidate changed' }
$jdk=(Get-ChildItem (Join-Path $repoPath 'arena/.tools') -Directory -Filter jdk-*|Select-Object -First 1).FullName
$out=Join-Path $PSScriptRoot "target/card-use/$ProfileMode-$Seed"; $classes=Join-Path $out 'classes'
$null=New-Item -ItemType Directory -Path $classes -Force
$candidate=Join-Path $out 'candidate.jar'; Copy-Item -LiteralPath $gate.candidate -Destination $candidate
$observer=Join-Path $PSScriptRoot 'CorpseAugurProbe.java'
& "$jdk/bin/javac.exe" -encoding UTF-8 -cp $candidate -d $classes $observer *> (Join-Path $out 'compile.log')
if($LASTEXITCODE -ne 0) { throw 'Observer compile failed' }
$actor=Join-Path $PSScriptRoot 'decks/Actor.dck'; $opponent=Join-Path $PSScriptRoot 'decks/Opponent.dck'
$runtime=$gate.runtime.Replace('\','/'); $log=Join-Path $out 'probe.log'
$arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtime/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$classes;$candidate",'CorpseAugurProbe',"$Seed",$ProfileMode,"$ClockSeconds",$actor,$opponent)
Push-Location $gate.runtime
try { & "$jdk/bin/java.exe" @arguments *> $log; $nativeExit=$LASTEXITCODE } finally { Pop-Location }
$lines=@(Get-Content -LiteralPath $log); $text=$lines -join "`n"; $cutoff=$false; $rows=@(); $late=0
foreach($line in $lines) {
    if($line -eq 'AUGUR_PROBE_CLOCK_STOP' -or $line.StartsWith('AUGUR_PROBE_DONE')) { $cutoff=$true }
    if($line.StartsWith('AUGUR_WITNESS ')) { if($cutoff) { $late++ } else { $rows+=($line.Substring(14)|ConvertFrom-Json) } }
}
$clock=$text -match 'AUGUR_PROBE_CLOCK_STOP'
$diagnostics=@([regex]::Matches($text,'(?m)^[^\r\n]*(?:Exception|StackOverflowError)[^\r\n]*$')|ForEach-Object Value)
$unexpected=@($diagnostics|Where-Object {!$clock -or $_ -notmatch '^java\.lang\.InterruptedException\s*$'})
$inputs=@($candidate,$observer,$PSCommandPath,$actor,$opponent,$log,(Join-Path $PSScriptRoot 'target/final/provenance.json'))
@{profile=$ProfileMode;seed=$Seed;clockSeconds=$ClockSeconds;clockReached=$clock;nativeExit=$nativeExit;diagnostics=$diagnostics;unexpectedDiagnostics=$unexpected;
 observations=$rows;lateObserverEventsExcluded=$late;command=@("$jdk/bin/java.exe")+$arguments;files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});
 scope='Autonomous native Match, read-only observer, custom decks; card-use evidence only, no completion/winner/strength claim'}|ConvertTo-Json -Depth 12|Set-Content (Join-Path $out 'summary.json')
if($nativeExit -ne 0 -or $unexpected.Count -or $text -notmatch 'AUGUR_PROBE_DONE') { throw "Probe error: $log" }
Write-Output "$ProfileMode seed $Seed : $($rows.Count) observed events; clock=$clock"
