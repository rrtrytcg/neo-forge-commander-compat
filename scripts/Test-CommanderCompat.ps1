param(
    [Parameter(Mandatory)][string]$NeoForgePath,
    [string]$Suite = 'all',
    [string]$JdkPath,
    [string]$EngineJarPath
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$install = (Resolve-Path -LiteralPath $NeoForgePath).Path
$app = Join-Path $install 'app'
$res = Join-Path $install 'res'
$aiJar = Join-Path $app 'forge-ai-2.0.16-SNAPSHOT.jar'
if (!(Test-Path $aiJar) -or !(Test-Path $res)) {
    throw 'Expected a NeoForge 1.0.28 desktop install with app/forge-ai-2.0.16-SNAPSHOT.jar and res/.'
}
$manifest = Get-Content (Join-Path $repo 'project-manifest.json') -Raw | ConvertFrom-Json
if ((Get-FileHash $aiJar -Algorithm SHA256).Hash -ne $manifest.base_ai_sha256) {
    throw 'This test engine is pinned to NeoForge 1.0.28; the installed forge-ai jar does not match its recorded baseline.'
}
$engine = if ($EngineJarPath) { (Resolve-Path -LiteralPath $EngineJarPath).Path } else { Join-Path $repo $manifest.test_engine }
if (!(Test-Path $engine) -and !$EngineJarPath) {
    $release = Invoke-RestMethod 'https://api.github.com/repos/rrtrytcg/neo-forge-commander-compat/releases/latest'
    $asset = $release.assets | Where-Object name -eq (Split-Path $engine -Leaf) | Select-Object -First 1
    if (!$asset) { throw 'The latest public release has no matching NeoForge 1.0.28 test-engine asset.' }
    New-Item -ItemType Directory -Force (Split-Path $engine -Parent) | Out-Null
    Invoke-WebRequest $asset.browser_download_url -OutFile $engine
}
if (!(Test-Path $engine)) { throw "Missing test engine jar: $engine. Pass -EngineJarPath or download the matching release asset." }
if ((Get-FileHash $engine -Algorithm SHA256).Hash -ne $manifest.test_engine_sha256) { throw 'Test engine hash mismatch; use the sealed 1.0.28 artifact.' }
$jdk = if ($JdkPath) { Get-Item -LiteralPath $JdkPath } else { Get-ChildItem (Join-Path $repo 'target/tool-cache') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1 }
if (!$jdk) {
    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if (!$javac -or !$java) {
        $jdkPath = & (Join-Path $repo 'scripts/Get-TestJdk.ps1') -Destination (Join-Path $repo 'target/tool-cache')
        if ($jdkPath) { $jdk = Get-Item -LiteralPath $jdkPath }
        if (!$jdk) { throw 'JDK 17 setup failed. Install JDK 17 and put javac.exe on PATH, or pass -JdkPath.' }
    }
}
if (!$jdk) {
    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if (!$javac -or !$java) { throw 'JDK 17 not found. Pass -JdkPath or install JDK 17 and put javac.exe on PATH.' }
    $javacPath = $javac.Source; $javaPath = $java.Source
} else {
    $javacPath = Join-Path $jdk.FullName 'bin/javac.exe'
    $javaPath = Join-Path $jdk.FullName 'bin/java.exe'
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$out = Join-Path $repo "target/test-$stamp"
$testClasses = Join-Path $out 'test-classes'; $runtime = Join-Path $out 'runtime'
New-Item -ItemType Directory -Force $testClasses, $runtime | Out-Null
Write-Output 'Copying NeoForge resources into the disposable test workspace...'
Copy-Item $res (Join-Path $runtime 'res') -Recurse
$profileDir = Join-Path $runtime 'res/ai/overrides'
New-Item -ItemType Directory -Force $profileDir | Out-Null
Copy-Item (Join-Path $repo 'profiles/*.properties') $profileDir -Force
$runtimeSlash = $runtime.Replace('\','/')
@("userDir=$runtimeSlash/user", "cacheDir=$runtimeSlash/cache", "decksDir=$runtimeSlash/decks") | Set-Content (Join-Path $runtime 'forge.profile.properties') -Encoding ascii
$suites = if ($Suite -eq 'all') { @($manifest.test_suites) } else { @($Suite) }
$testSources = foreach ($name in $suites) {
    $hits = @(Get-ChildItem (Join-Path $repo 'arena') -Recurse -File -Filter "$name.java" | Select-Object -ExpandProperty FullName)
    if ($hits.Count -ne 1) { throw "Expected exactly one source for suite $name; found $($hits.Count)." }
    $hits[0]
}
Write-Output "Compiling $($testSources.Count) native suite source(s)..."
& $javacPath -encoding UTF-8 -nowarn -cp $engine -d $testClasses @testSources *> (Join-Path $out 'test-compile.log')
if ($LASTEXITCODE) { Get-Content (Join-Path $out 'test-compile.log') -Tail 80; throw 'Native fixture compilation failed.' }
Push-Location $runtime
$totalChecks = 0
try {
    foreach ($name in $suites) {
        $arguments = @('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$testClasses;$engine",$name)
        & $javaPath @arguments *> (Join-Path $out "$name.log")
        if ($LASTEXITCODE) { Get-Content (Join-Path $out "$name.log") -Tail 80; throw "Native suite failed: $name" }
        $checks = @(Select-String (Join-Path $out "$name.log") -Pattern '^PASS ').Count
        $expectedChecks = [int]$manifest.suite_checks.$name
        if ($checks -ne $expectedChecks) { throw "Native suite count changed: $name $checks/$expectedChecks" }
        $totalChecks += $checks
        Write-Output "$name PASS: $checks"
    }
} finally { Pop-Location }
Write-Output "DONE native checks=$totalChecks suites=$($suites.Count); logs=$out"
