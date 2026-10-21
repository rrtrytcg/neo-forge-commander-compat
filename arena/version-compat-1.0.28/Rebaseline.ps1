<#
  Re-baseline of the sealed NeoForge 1.0.27 gate on the installed NeoForge 1.0.28.
  Candidate = installed 1.0.28 core/game/ai/gui classes + desktop harness + the sealed 1.0.27 opt-in overlay classes
  (assemble.py). Runs every prior suite (same compiled fixtures) with the exact per-suite counts of the 1.0.27 gate.
  Output: target/final-1.0.28 (provenance.json is the seal). Nothing is installed; production sources are not recompiled.
#>
param([string]$Prior = 'arena/choice-wipes/target/final-1.0.27', [string]$OutName = 'final-1.0.28')
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$install = (Resolve-Path (Join-Path $repo '../NeoForge')).Path
$priorPath = (Resolve-Path (Join-Path $repo $Prior)).Path
$prev = Get-Content (Join-Path $priorPath 'provenance.json') -Raw | ConvertFrom-Json
if ($prev.engine -ne 'NeoForge 1.0.27' -or $prev.baseline) { throw 'Prior gate must be a sealed full NeoForge 1.0.27 gate' }
if ((Get-FileHash $prev.candidate).Hash -ne $prev.candidateSha256) { throw 'Prior candidate changed' }
& python -I -c 'import hashlib,json,sys;b=json.load(open(sys.argv[1]))["installed"];bad=[f["path"] for f in b if hashlib.sha256(open(f["path"],"rb").read()).hexdigest().upper()!=f["sha256"]];print(len(b),"installed hashes checked,",len(bad),"changed");sys.exit(1 if bad else 0)' (Join-Path $PSScriptRoot 'target/preserved-baseline.json')
if ($LASTEXITCODE) { throw 'Installed app/resources differ from the 1.0.28 baseline' }
$jdk = (Get-ChildItem (Join-Path $repo 'arena/.tools') -Directory -Filter 'jdk-*' | Select-Object -First 1).FullName
$out = Join-Path $PSScriptRoot "target/$OutName"
if (Test-Path (Join-Path $out 'provenance.json')) { throw "Final gate exists; preserve it or use -OutName: $out" }
$work = Join-Path $out 'runtime'; $testClasses = Join-Path $out 'test-classes'; $candidate = Join-Path $out 'engine.jar'
$null = New-Item -ItemType Directory -Force $work, $testClasses
$desktop = (Get-ChildItem (Join-Path $repo 'forge-gui-desktop/target') -Filter *jar-with-dependencies.jar | Select-Object -First 1).FullName
& python -I (Join-Path $PSScriptRoot 'assemble.py') $desktop $install $prev.candidate $candidate (Join-Path $out 'overlay-manifest.json')
if ($LASTEXITCODE) { throw 'Candidate assembly failed' }
# Private runtime: installed 1.0.28 resources + the exact AI profiles the 1.0.27 gate used.
& python -I -c 'import shutil,sys;shutil.copytree(sys.argv[1],sys.argv[2],dirs_exist_ok=True);shutil.copytree(sys.argv[3],sys.argv[4],dirs_exist_ok=True)' (Join-Path $install 'res') (Join-Path $work 'res') (Join-Path $prev.runtime 'res/ai/overrides') (Join-Path $work 'res/ai/overrides')
if ($LASTEXITCODE) { throw 'Private resource copy failed' }
$runtime = $work.Replace('\', '/')
@("userDir=$runtime/user", "cacheDir=$runtime/cache", "decksDir=$runtime/decks") | Set-Content (Join-Path $work 'forge.profile.properties')
Copy-Item (Join-Path $priorPath 'test-classes/*.class') $testClasses -Force
$inputs = @($PSCommandPath, (Join-Path $PSScriptRoot 'assemble.py'), $prev.candidate, (Join-Path $priorPath 'provenance.json'), (Join-Path $work 'forge.profile.properties'), (Join-Path $out 'overlay-manifest.json'))
$build = [ordered]@{engine = 'NeoForge 1.0.28'; upstream = '84f4983c1ce872932cf9a26a8bd54ca00056aae9'; revision = (& git -C $repo rev-parse HEAD).Trim(); base = $priorPath; baseline = $false
    candidate = $candidate; candidateSha256 = (Get-FileHash $candidate).Hash; runtime = $work; newSuites = @()
    files = @($inputs | ForEach-Object { @{path = $_; sha256 = (Get-FileHash $_).Hash} })}
$results = @(); Push-Location $work
try {
    foreach ($old in $prev.results) {
        $suite = $old.suite; $log = Join-Path $out "$suite.log"
        & "$jdk/bin/java.exe" '-Xmx2g' '-Djava.awt.headless=true' '-Dfile.encoding=UTF-8' "-Duser.home=$runtime/home" '-Dsentry.dsn=' '-Dsentry.enabled=false' '-cp' "$testClasses;$candidate" $suite *> $log
        if ($LASTEXITCODE) { throw "Native suite failed: $suite ($log)" }
        $count = @(Select-String $log -Pattern '^PASS ').Count
        $results += @{suite = $suite; checks = $count; expected = $old.checks; match = ($count -eq $old.checks); log = $log; sha256 = (Get-FileHash $log).Hash}
        Write-Output "$suite $count/$($old.checks)$(if ($count -ne $old.checks) { '  <-- DIFFERS' })"
    }
} finally { Pop-Location }
$build['results'] = $results; $build['priorChecks'] = ($prev.results | Measure-Object checks -Sum).Sum
$total = ($results | Measure-Object checks -Sum).Sum
$bad = @($results | Where-Object { !$_.match })
$build['mismatches'] = @($bad | ForEach-Object suite)
if ($bad.Count) { $build | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $out 'UNSEALED-mismatch.json'); throw "$($bad.Count) suite(s) differ from the 1.0.27 gate; not sealed" }
$build | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $out 'provenance.json')
Write-Output "DONE $total native checks across $($results.Count) suites (expected $($build.priorChecks))"
