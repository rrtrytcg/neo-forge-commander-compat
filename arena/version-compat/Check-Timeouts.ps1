param([ValidatePattern('^[a-zA-Z0-9_.-]+$')][string]$Label='neoforge-1.0.25')
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$out=Join-Path $PSScriptRoot "target/$Label"
$candidate=Join-Path $out 'engine.jar'
$jdk=(Get-ChildItem (Join-Path $repo 'arena/.tools') -Directory -Filter jdk-*|Select-Object -First 1).FullName
$testng=(Get-ChildItem (Join-Path $repo 'arena/.tools/m2/org/testng/testng') -Recurse -Filter '*.jar'|Select-Object -First 1).FullName
$commander=(Get-ChildItem (Join-Path $repo 'arena/.tools/m2/com/beust/jcommander') -Recurse -Filter '*.jar'|Select-Object -First 1).FullName
$source=Join-Path $out 'AITimeoutTrackerTest.java'
$text=(& git -C $repo show '64569999b8b99dd7176ab9d3e1e6008f3ab68590:forge-gui-desktop/src/test/java/forge/util/AITimeoutTrackerTest.java') -join "`n"
[IO.File]::WriteAllText($source,$text+"`n")
$own=Join-Path $PSScriptRoot 'CompatibilityTimeoutTests.java'
$classes=Join-Path $out 'timeout-classes'; $null=New-Item -ItemType Directory -Path $classes -Force
$cp="$candidate;$out/test-classes;$testng;$commander"
& "$jdk/bin/javac.exe" -encoding UTF-8 -cp $cp -d $classes $source $own *> (Join-Path $out 'timeout-compile.log')
if($LASTEXITCODE -ne 0) { throw 'Timeout fixture compilation failed' }
$work=Join-Path $out 'runtime'; $runtime=$work.Replace('\','/')
$common=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtime/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$classes;$cp")
Push-Location $work
try {
    $log=Join-Path $out 'upstream-timeouts.log'
    & "$jdk/bin/java.exe" @common org.testng.TestNG -usedefaultlisteners false -testclass forge.util.AITimeoutTrackerTest *> $log
    if($LASTEXITCODE -ne 0) { throw 'Upstream tracker tests failed' }
    $ownLog=Join-Path $out 'CompatibilityTimeoutTests.log'
    & "$jdk/bin/java.exe" @common CompatibilityTimeoutTests *> $ownLog
    if($LASTEXITCODE -ne 0) { throw 'Timeout integration tests failed' }
} finally { Pop-Location }
$files=@($candidate,$source,$own,$PSCommandPath,$log,$ownLog)|ForEach-Object {@{path=$_;sha256=(Get-FileHash $_).Hash}}
@{candidateSha256=(Get-FileHash $candidate).Hash;files=$files;
    upstream=(Select-String -Path $log -Pattern 'Total tests run:').Line.Trim();
    integrationChecks=@(Select-String -Path $ownLog -Pattern '^PASS ').Count}|
 ConvertTo-Json -Depth 6|Set-Content (Join-Path $out 'timeout-summary.json')
Get-Content $log -Tail 5
Get-Content $ownLog -Tail 11
