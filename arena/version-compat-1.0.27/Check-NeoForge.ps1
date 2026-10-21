param([switch]$BuildOnly,[switch]$RunOnly)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$install=(Resolve-Path (Join-Path $repo '../NeoForge')).Path
$out=Join-Path $PSScriptRoot 'target';$work=Join-Path $out 'runtime'
$classes=Join-Path $out 'classes';$tests=Join-Path $out 'test-classes';$candidate=Join-Path $out 'engine.jar'
$jdk=(Get-ChildItem (Join-Path $repo 'arena/.tools') -Directory -Filter jdk-*|Select-Object -First 1).FullName
& python (Join-Path $PSScriptRoot 'verify-preserved.py');if($LASTEXITCODE){throw 'Preserved file mismatch'}
$prior=Get-Content (Join-Path $repo 'arena/mirage-mirror/target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
if($prior.results.Count -ne 38 -or ($prior.results|Measure-Object checks -Sum).Sum -ne 4053){throw 'Historical gate incomplete'}
$fixturePaths=@(& rg --files (Join-Path $repo 'arena') -g '*.java')
$suiteSources=@($prior.results|ForEach-Object { $name=$_.suite+'.java';$matches=@($fixturePaths|Where-Object {[IO.Path]::GetFileName($_) -eq $name});if($matches.Count -ne 1){throw "Ambiguous fixture $name"};$matches[0] })
if(!$RunOnly){
    if(Test-Path (Join-Path $out 'provenance.json')){throw 'Final gate exists; preserve it and use a fresh lane'}
    & python (Join-Path $PSScriptRoot 'prepare.py');if($LASTEXITCODE){throw 'Source reconciliation failed'}
    $null=New-Item -ItemType Directory -Force $work,$classes,$tests
    $paths=Get-Content (Join-Path $out 'source-list.json') -Raw|ConvertFrom-Json
    $sources=@($paths|ForEach-Object {Join-Path $out "source/$_"})
    # Rebuild the native engine layer from installed modules, keeping only desktop harness/dependencies.
    $desktop=(Get-ChildItem (Join-Path $repo 'forge-gui-desktop/target') -Filter *jar-with-dependencies.jar|Select-Object -First 1).FullName
    & python (Join-Path $PSScriptRoot 'assemble.py') $desktop $install $candidate
    if($LASTEXITCODE){throw 'Native assembly failed'}
    & "$jdk/bin/javac.exe" -encoding UTF-8 -cp $candidate -d $classes @sources *> (Join-Path $out 'compile.log')
    if($LASTEXITCODE){throw 'Overlay compilation failed; inspect target/compile.log'}
    & "$jdk/bin/jar.exe" uf $candidate -C $classes forge
    if($LASTEXITCODE){throw 'Policy overlay failed'}
    if(!(Test-Path (Join-Path $work 'res/howto.txt'))){Copy-Item -LiteralPath (Join-Path $install 'res') -Destination $work -Recurse}
    Copy-Item -LiteralPath (Join-Path $repo 'forge-gui/res/ai/overrides') -Destination (Join-Path $work 'res/ai') -Recurse -Force
    $runtime=$work.Replace('\','/')
    @("userDir=$runtime/user","cacheDir=$runtime/cache","decksDir=$runtime/decks")|Set-Content (Join-Path $work 'forge.profile.properties')
    & "$jdk/bin/javac.exe" -encoding UTF-8 -cp $candidate -d $tests @suiteSources *> (Join-Path $out 'test-compile.log')
    if($LASTEXITCODE){throw 'Fixture compilation failed'}
    $inputs=@($PSCommandPath,(Join-Path $PSScriptRoot 'prepare.py'),(Join-Path $PSScriptRoot 'assemble.py'),(Join-Path $PSScriptRoot 'post-1.0.25-hooks.patch'),(Join-Path $PSScriptRoot 'upstream-overlaps.patch'),(Join-Path $repo 'arena/version-compat/neoforge-1.0.25-hooks.patch'),(Join-Path $work 'forge.profile.properties'))+$sources+$suiteSources+@($paths|ForEach-Object {Join-Path $repo $_})+@(Get-ChildItem (Join-Path $repo 'forge-gui/res/ai/overrides') -File|ForEach-Object FullName)
    @{engine='NeoForge 1.0.27';upstream='77b6f5ae95f7f11d5a718c52a977470131db0fe1';revision=(& git -C $repo rev-parse HEAD).Trim();candidate=$candidate;candidateSha256=(Get-FileHash $candidate).Hash;runtime=$work;
      files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}})}|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out 'build.json')
}
$build=Get-Content (Join-Path $out 'build.json') -Raw|ConvertFrom-Json
if((Get-FileHash $candidate).Hash -ne $build.candidateSha256){throw 'Candidate changed'}
foreach($f in $build.files){if((Get-FileHash -LiteralPath $f.path).Hash -ne $f.sha256){throw "Build input changed: $($f.path)"}}
if($BuildOnly){Write-Output 'BUILD complete';return}
$results=@();$runtime=$work.Replace('\','/')
Push-Location $work
try{foreach($old in $prior.results){
    $suite=$old.suite;$log=Join-Path $out "$suite.log"
    $javaArgs=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtime/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$tests;$candidate",$suite)
    & "$jdk/bin/java.exe" @javaArgs *> $log
    if($LASTEXITCODE){throw "Native fixture failed: $suite"}
    $count=@(Select-String -LiteralPath $log -Pattern '^PASS ').Count
    if($count -ne $old.checks){throw "Unexpected fixture count: $suite $count"}
    $results+=@{suite=$suite;checks=$count;log=$log;sha256=(Get-FileHash $log).Hash}
    Write-Output "$suite PASS: $count"
}}finally{Pop-Location}
$build|Add-Member results $results
$build|Add-Member date '2026-10-19'
$build|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out 'provenance.json')
Write-Output "DONE $(($results|Measure-Object checks -Sum).Sum) native checks on NeoForge 1.0.27"
