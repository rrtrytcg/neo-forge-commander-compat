# Resume an unchanged private build after a native initialization failure.
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$out=Join-Path $PSScriptRoot 'target/final-1.0.25'
$focused=Get-Content (Join-Path $out 'focused-provenance.json') -Raw|ConvertFrom-Json
if($focused.engine -ne 'NeoForge 1.0.25' -or $focused.results.Count -ne 1 -or $focused.results[0].checks -ne 224){throw 'Require the focused 224-check gate'}
foreach($f in $focused.files){if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256){throw "Build source changed: $($f.path)"}}
$basePath=Join-Path $repo 'arena/arcane-lighthouse/target/final-1.0.25';$base=Get-Content (Join-Path $basePath 'provenance.json') -Raw|ConvertFrom-Json
if((Get-FileHash $base.candidate).Hash -ne $base.candidateSha256){throw 'Baseline candidate changed'}
$candidate=Join-Path $out 'engine.jar';$candidateHash=(Get-FileHash $candidate).Hash;$buildTime=(Get-Item $candidate).LastWriteTimeUtc
Add-Type -AssemblyName System.IO.Compression.FileSystem
function EntryHash($entry){$stream=$entry.Open();$sha=[Security.Cryptography.SHA256]::Create();try{return [Convert]::ToHexString($sha.ComputeHash($stream))}finally{$sha.Dispose();$stream.Dispose()}}
$overlay=@{};foreach($file in Get-ChildItem (Join-Path $out 'classes') -Recurse -File){$name=[IO.Path]::GetRelativePath((Join-Path $out 'classes'),$file.FullName).Replace('\','/');$overlay[$name]=(Get-FileHash $file.FullName).Hash}
$actual=[IO.Compression.ZipFile]::OpenRead($candidate);$original=[IO.Compression.ZipFile]::OpenRead($base.candidate);$baseEntries=0
try{
    foreach($entry in $original.Entries){if($entry.FullName.EndsWith('/') -or $overlay.ContainsKey($entry.FullName)){continue};$other=$actual.GetEntry($entry.FullName);if(!$other -or (EntryHash $entry) -ne (EntryHash $other)){throw "Non-overlay entry changed: $($entry.FullName)"};$baseEntries++}
    foreach($name in $overlay.Keys){$entry=$actual.GetEntry($name);if(!$entry -or (EntryHash $entry) -ne $overlay[$name]){throw "Overlay bytecode mismatch: $name"}}
}finally{$actual.Dispose();$original.Dispose()}
$editionFiles=@();$runtimeEditions=Join-Path $focused.runtime 'res/editions';$baselineEditions=Join-Path $base.runtime 'res/editions'
foreach($file in Get-ChildItem $runtimeEditions -Recurse -File){$relative=[IO.Path]::GetRelativePath($runtimeEditions,$file.FullName);$baseline=Join-Path $baselineEditions $relative;$hash=(Get-FileHash $file.FullName).Hash;if(!(Test-Path $baseline) -or (Get-FileHash $baseline).Hash -ne $hash){throw "Edition asset changed: $relative"};if($file.Extension -eq '.txt' -and (Get-Content $file.FullName -Raw) -notmatch '(?im)^\[metadata\]'){throw "Edition metadata missing: $relative"};$editionFiles+=@{path=$file.FullName;sha256=$hash}}
$jdk=(Get-ChildItem (Join-Path $repo 'arena/.tools') -Directory -Filter jdk-*|Select-Object -First 1).FullName
$tests=Join-Path $out 'test-classes';$runtime=$focused.runtime.Replace('\','/');$suites=@($base.results|ForEach-Object suite)+'MirageMirrorTests';$results=@();$reuse=$true;$reused=@();$failures=@()
Push-Location $focused.runtime
try{foreach($suite in $suites){
    $log=Join-Path $out "$suite.log";$expected=if($suite -eq 'MirageMirrorTests'){224}else{($base.results|Where-Object suite -eq $suite).checks}
    $canReuse=$reuse -and (Test-Path $log) -and (Get-Item $log).LastWriteTimeUtc -ge $buildTime -and @(Select-String -LiteralPath $log -Pattern '^PASS ').Count -eq $expected -and (Get-Content $log -Raw) -match "(?m)^DONE $expected checks" -and !(Select-String -LiteralPath $log -Pattern 'Exception|AssertionError' -Quiet)
    if($canReuse){$reused+=$suite}else{
        $reuse=$false
        if(Test-Path $log){$saved=Join-Path $out "$suite.before-resume.log";Copy-Item -LiteralPath $log -Destination $saved;$failures+=@{path=$saved;sha256=(Get-FileHash $saved).Hash}}
        if((Get-FileHash $candidate).Hash -ne $candidateHash){throw 'Candidate changed during resumption'}
        & (Join-Path $jdk 'bin/java.exe') '-Xmx2g' '-Djava.awt.headless=true' '-Dfile.encoding=UTF-8' "-Duser.home=$runtime/home" '-Dsentry.dsn=' '-Dsentry.enabled=false' '-cp' "$tests;$candidate" $suite *> $log
        if($LASTEXITCODE){throw "$suite failed again: $log"}
    }
    $count=@(Select-String -LiteralPath $log -Pattern '^PASS ').Count;if($count -ne $expected){throw "$suite check count mismatch"}
    $results+=@{suite=$suite;checks=$count;log=$log;sha256=(Get-FileHash $log).Hash};Write-Output "$suite PASS: $count $(if($canReuse){'(same-build log reused)'}else{'(resumed)'})"
}}finally{Pop-Location}
foreach($f in $focused.files){if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256){throw 'Source changed during resumed gate'}}
if((Get-FileHash $candidate).Hash -ne $candidateHash -or ($results|Measure-Object checks -Sum).Sum -ne 4053){throw 'Final candidate/count mismatch'}
$resumePath=Join-Path $out 'resume-evidence.json'
@{reason='Native edition metadata initialization failed before Assault Suit checks; no source, candidate or asset changes. Reuse only the completed leading same-build suites; rerun the failure and all later suites.';candidateSha256=$candidateHash;unchangedBaseEntries=$baseEntries;overlayBytecode=$overlay;reusedSuites=$reused;editionFiles=$editionFiles;priorLogs=$failures}|ConvertTo-Json -Depth 10|Set-Content $resumePath
$inputs=@($focused.files|ForEach-Object path)+@($PSCommandPath,$resumePath)
@{date='2026-10-18';revision=(& git -C $repo rev-parse HEAD).Trim();engine='NeoForge 1.0.25';candidate=$candidate;candidateSha256=$candidateHash;runtime=$focused.runtime;results=$results;files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});scope='Native correctness and prior Commander regressions; resumption of the unchanged build after initialization failure; no strength claim'}|ConvertTo-Json -Depth 10|Set-Content (Join-Path $out 'provenance.json')
Write-Output 'DONE 4053 checks on NeoForge 1.0.25 (unchanged build resumed)'
