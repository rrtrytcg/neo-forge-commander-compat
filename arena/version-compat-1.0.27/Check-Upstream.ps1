$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$out=Join-Path $PSScriptRoot 'target'
$gate=Get-Content (Join-Path $out 'build.json') -Raw|ConvertFrom-Json
if((Get-FileHash $gate.candidate).Hash -ne $gate.candidateSha256){throw 'Candidate changed'}
$jdk=(Get-ChildItem (Join-Path $repo 'arena/.tools') -Directory -Filter jdk-*|Select-Object -First 1).FullName
$source=Join-Path $PSScriptRoot 'UpstreamCombatTests.java';$classes=Join-Path $out 'upstream-classes'
$null=New-Item -ItemType Directory -Path $classes -Force
$cp="$out/test-classes;$($gate.candidate)"
& "$jdk/bin/javac.exe" -encoding UTF-8 -cp $cp -d $classes $source *> (Join-Path $out 'upstream-compile.log')
if($LASTEXITCODE){throw 'Upstream combat fixture compile failed'}
$log=Join-Path $out 'UpstreamCombatTests.log'
Push-Location $gate.runtime
$javaArgs=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$($gate.runtime)/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$classes;$cp",'UpstreamCombatTests')
try{& "$jdk/bin/java.exe" @javaArgs *> $log;$nativeExit=$LASTEXITCODE}finally{Pop-Location}
if($nativeExit -or @(Select-String $log -Pattern '^PASS ').Count -ne 7){throw 'Upstream combat regression failed'}
@{checks=7;candidateSha256=$gate.candidateSha256;files=@($PSCommandPath,$source,$log|ForEach-Object {@{path=$_;sha256=(Get-FileHash $_).Hash}})}|ConvertTo-Json -Depth 6|Set-Content (Join-Path $out 'upstream-summary.json')
Get-Content $log -Tail 8
