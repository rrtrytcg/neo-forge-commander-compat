<#
  Shared NeoForge 1.0.28 native gate (copy of arena/lib/Check-Gate.ps1, parametrised for 1.0.28; the 1.0.27 original stays as historical evidence) for one opt-in card lab.

  Builds a private candidate = previous gate's engine.jar + freshly compiled overlay classes, then runs
  every prior native suite (exact counts required) plus the lab's new suites. Nothing is installed.

  Overlay sources are reconciled the same way for every file, so a stale pre-1.0.27 repo file can
  never be compiled into the candidate (upstream 1.0.28 changed no overlay source, so the 1.0.27 reconciled copies remain the base):
    base   = arena/version-compat-1.0.27/target/source/<file> if that reconciled copy exists,
             otherwise the upstream 1.0.28 file (git show 84f4983c:<file>), otherwise nothing (new file)
    source = base + (working tree vs 899567ea) for that file; any patch conflict stops the gate.
  Commit or stash unrelated edits in production files first: the working tree is what gets compiled.

  Example:
    ./arena/lib/Check-Gate.ps1 -Lab arena/utility-lands -Base arena/version-compat-1.0.28/target/final-1.0.28 `
      -Production forge-ai/src/main/java/forge/ai/UtilityLandsAi.java -Tests UtilityLandsTests.java `
      -Profiles forge-gui/res/ai/overrides/utility-lands.properties
  -Baseline: no overlay; new suites get the argument 'baseline' (document native defects first).
  -Focused:  new suites only.   -RunOnly: reuse the sealed build in the output folder.
#>
param(
    [Parameter(Mandatory)][string]$Lab,
    [Parameter(Mandatory)][string]$Base,
    [string[]]$Production = @(),
    [Parameter(Mandatory)][string[]]$Tests,
    [string[]]$Profiles = @(),
    [string]$OutName = 'final-1.0.28',
    [switch]$Baseline, [switch]$Focused, [switch]$RunOnly
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Abs([string]$p) { if ([IO.Path]::IsPathRooted($p)) { $p } else { Join-Path $repo $p } }
$UPSTREAM = '84f4983c1ce872932cf9a26a8bd54ca00056aae9'; $RECONCILED_AT = '899567ea'
$reconciled = Join-Path $repo 'arena/version-compat-1.0.27/target/source'
$labPath = (Resolve-Path (Abs $Lab)).Path
$basePath = (Resolve-Path (Abs $Base)).Path
$prev = Get-Content (Join-Path $basePath 'provenance.json') -Raw | ConvertFrom-Json
if ($prev.engine -ne 'NeoForge 1.0.28') { throw "Base gate is not NeoForge 1.0.28: $basePath" }
if ((Get-FileHash $prev.candidate).Hash -ne $prev.candidateSha256) { throw 'Base candidate changed' }
$priorChecks = ($prev.results | Measure-Object checks -Sum).Sum
$testFiles = @($Tests | ForEach-Object { Join-Path $labPath $_ })
$newSuites = @($testFiles | ForEach-Object { [IO.Path]::GetFileNameWithoutExtension($_) })
$jdk = (Get-ChildItem (Join-Path $repo 'arena/.tools') -Directory -Filter 'jdk-*' | Select-Object -First 1).FullName
$out = Join-Path $labPath ("target/" + $(if ($Baseline) { "$OutName-baseline" } else { $OutName }))
$work = Join-Path $out 'runtime'; $classes = Join-Path $out 'classes'; $testClasses = Join-Path $out 'test-classes'
$sourceRoot = Join-Path $out 'source'; $candidate = Join-Path $out 'engine.jar'

if (!$Focused -and !$Baseline) {
    # Installed app/resources only. The version-compat 'unrelated' group is obsolete (removed labs, rewritten fixtures).
    & python -I -c 'import hashlib,json,sys;b=json.load(open(sys.argv[1]))["installed"];bad=[f["path"] for f in b if hashlib.sha256(open(f["path"],"rb").read()).hexdigest().upper()!=f["sha256"]];print(len(b),"installed hashes checked,",len(bad),"changed");sys.exit(1 if bad else 0)' (Join-Path $repo 'arena/version-compat-1.0.28/target/preserved-baseline.json')
    if ($LASTEXITCODE) { throw 'Installed app/resources changed' }
}
if (!$RunOnly) {
    if (!$Focused -and (Test-Path (Join-Path $out 'provenance.json'))) { throw "Final gate exists; preserve it or use -OutName: $out" }
    $null = New-Item -ItemType Directory -Force $work, $classes, $testClasses, $sourceRoot
    if (!(Test-Path (Join-Path $work 'res/howto.txt'))) {
        & python -I -c 'import shutil,sys;shutil.copytree(sys.argv[1],sys.argv[2],dirs_exist_ok=True)' (Join-Path $prev.runtime 'res') (Join-Path $work 'res')
        if ($LASTEXITCODE) { throw 'Private resource copy failed' }
    }
    if (!$Baseline) { foreach ($p in $Profiles) { Copy-Item (Abs $p) (Join-Path $work 'res/ai/overrides') -Force } }
    $runtime = $work.Replace('\', '/')
    @("userDir=$runtime/user", "cacheDir=$runtime/cache", "decksDir=$runtime/decks") | Set-Content (Join-Path $work 'forge.profile.properties')
    Copy-Item $prev.candidate $candidate -Force
    $compiled = @()
    if (!$Baseline -and $Production.Count) {
        foreach ($rel in $Production) {
            $rel = $rel.Replace('\', '/'); $dest = Join-Path $sourceRoot $rel
            $null = New-Item -ItemType Directory -Force (Split-Path $dest)
            $tracked = (& git -C $repo cat-file -t "${RECONCILED_AT}:$rel" 2>$null) -eq 'blob'
            if (Test-Path (Join-Path $reconciled $rel)) { Copy-Item (Join-Path $reconciled $rel) $dest -Force }
            elseif ((& git -C $repo cat-file -t "${UPSTREAM}:$rel" 2>$null) -eq 'blob') {
                & python -I -c 'import subprocess,sys;CR,LF=bytes([13]),bytes([10]);b=subprocess.check_output(["git","-C",sys.argv[1],"show",sys.argv[2]]).replace(CR+LF,LF);w=open(sys.argv[4],"rb").read();open(sys.argv[3],"wb").write(b.replace(LF,CR+LF) if CR+LF in w else b)' $repo "${UPSTREAM}:$rel" $dest (Abs $rel)
                if ($LASTEXITCODE) { throw "Could not read upstream 1.0.28 $rel" }
            }
            elseif (!$tracked) { Copy-Item (Abs $rel) $dest -Force; $compiled += $dest; continue }
            else { throw "No 1.0.28 base for $rel" }
            if ($tracked) {
                $patch = Join-Path $out ("patch-" + ($rel -replace '[\\/]', '_') + '.patch')
                & git -C $repo diff $RECONCILED_AT -- $rel | Set-Content $patch
                # PowerShell emits CRLF, but some reconciled sources retain LF. Match that source.
                & python -I -c 'import pathlib,sys;p=pathlib.Path(sys.argv[1]);b=p.read_bytes().replace(bytes([13,10]),bytes([10]));s=pathlib.Path(sys.argv[2]).read_bytes();p.write_bytes(b.replace(bytes([10]),bytes([13,10])) if bytes([13,10]) in s else b)' $patch $dest
                if ($LASTEXITCODE) { throw "Could not normalize patch line endings: $rel" }
                if ((Get-Item $patch).Length -gt 0) {
                    & git -C $repo apply --whitespace=nowarn "--directory=$([IO.Path]::GetRelativePath($repo, $sourceRoot).Replace('\', '/'))" $patch
                    if ($LASTEXITCODE) { throw "1.0.28 reconciliation failed for $rel (patch $patch)" }
                }
            }
            $compiled += $dest
        }
        & "$jdk/bin/javac.exe" -encoding UTF-8 -nowarn -cp $candidate -d $classes @compiled *> (Join-Path $out 'production-compile.log')
        if ($LASTEXITCODE) { throw "Production compilation failed: $out/production-compile.log" }
        & "$jdk/bin/jar.exe" uf $candidate -C $classes forge
        if ($LASTEXITCODE) { throw 'Private overlay failed' }
    }
    Copy-Item (Join-Path $basePath 'test-classes/*.class') $testClasses -Force
    & "$jdk/bin/javac.exe" -encoding UTF-8 -nowarn -cp "$testClasses;$candidate" -d $testClasses @testFiles *> (Join-Path $out 'test-compile.log')
    if ($LASTEXITCODE) { throw "Test compilation failed: $out/test-compile.log" }
    $inputs = @($PSCommandPath, $prev.candidate, (Join-Path $basePath 'provenance.json'), (Join-Path $work 'forge.profile.properties')) + $testFiles + ($Profiles | ForEach-Object { Abs $_ })
    if (!$Baseline) { $inputs += ($Production | ForEach-Object { Abs $_ }) + $compiled }
    @{engine = 'NeoForge 1.0.28'; revision = (& git -C $repo rev-parse HEAD).Trim(); base = $basePath; baseline = [bool]$Baseline
      candidate = $candidate; candidateSha256 = (Get-FileHash $candidate).Hash; runtime = $work; newSuites = @($newSuites)
      files = @($inputs | ForEach-Object { @{path = $_; sha256 = (Get-FileHash $_).Hash} })
    } | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $out 'build.json')
}
$build = Get-Content (Join-Path $out 'build.json') -Raw | ConvertFrom-Json
foreach ($f in $build.files) { if ((Get-FileHash $f.path).Hash -ne $f.sha256) { throw "Build input changed since build: $($f.path)" } }
if ((Get-FileHash $candidate).Hash -ne $build.candidateSha256) { throw 'Candidate changed' }
$suites = if ($Baseline -or $Focused) { @($newSuites) } else { @($prev.results | ForEach-Object suite) + @($newSuites) }
$results = @(); $runtime = $work.Replace('\', '/')
Push-Location $work
try {
    foreach ($suite in $suites) {
        $log = Join-Path $out "$suite.log"
        $arguments = @('-Xmx2g', '-Djava.awt.headless=true', '-Dfile.encoding=UTF-8', "-Duser.home=$runtime/home", '-Dsentry.dsn=', '-Dsentry.enabled=false', '-cp', "$testClasses;$candidate", $suite)
        if ($Baseline) { $arguments += 'baseline' }
        & "$jdk/bin/java.exe" @arguments *> $log
        if ($LASTEXITCODE) { throw "Native suite failed: $suite ($log)" }
        $count = @(Select-String $log -Pattern '^PASS ').Count
        if ($suite -notin $newSuites) {
            $expected = ($prev.results | Where-Object suite -eq $suite).checks
            if ($count -ne $expected) { throw "Prior suite count changed: $suite $count/$expected" }
        }
        $results += @{suite = $suite; checks = $count; log = $log; sha256 = (Get-FileHash $log).Hash }
        Write-Output "$suite PASS: $count"
    }
} finally { Pop-Location }
$build | Add-Member results $results -Force
$build | Add-Member priorChecks $priorChecks -Force
$build | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $out $(if ($Focused) { 'focused-provenance.json' } else { 'provenance.json' }))
$total = ($results | Measure-Object checks -Sum).Sum
Write-Output "DONE $total native checks across $($results.Count) suites$(if (!$Focused -and !$Baseline) { " ($($total - $priorChecks) new + $priorChecks prior)" })"
