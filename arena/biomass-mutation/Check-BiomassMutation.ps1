param([ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label='final',[switch]$CardOnly)
$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$basePath=Join-Path $repoPath 'arena/nesting-grounds/target/final'
$base=Get-Content (Join-Path $basePath 'provenance.json') -Raw|ConvertFrom-Json
if($base.engine -ne 'NeoForge 1.0.25' -or $base.results.Count -ne 19 -or ($base.results|Measure-Object checks -Sum).Sum -ne 1610) { throw 'Complete the prior Nesting Grounds 1.0.25 gate first' }
if((Get-FileHash $base.candidate).Hash -ne $base.candidateSha256) { throw 'Compatibility baseline candidate changed' }
$version=Get-Content (Join-Path $repoPath 'arena/version-compat/verification-summary.json') -Raw|ConvertFrom-Json
$install=(Resolve-Path (Join-Path $repoPath '../NeoForge')).Path
foreach($file in $version.nativeEvidence.files) {
    if($file.path.StartsWith($install,[StringComparison]::OrdinalIgnoreCase) -and (Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw 'Installed 1.0.25 engine/assets changed; audit the update first' }
}
$jdk=(Get-ChildItem (Join-Path $repoPath 'arena/.tools') -Directory -Filter jdk-*|Select-Object -First 1).FullName
$out=Join-Path $PSScriptRoot "target/$Label"
$classes=Join-Path $out 'classes'; $work=Join-Path $out 'runtime'
$null=New-Item -ItemType Directory -Path $classes,$work -Force
$candidate=Join-Path $out 'engine.jar'; Copy-Item -LiteralPath $base.candidate -Destination $candidate
if(!(Test-Path (Join-Path $work 'res/howto.txt'))) { Copy-Item -LiteralPath (Join-Path $base.runtime 'res') -Destination $work -Recurse }
Copy-Item -LiteralPath (Join-Path $repoPath 'forge-gui/res/ai/overrides/biomass-mutation.properties') -Destination (Join-Path $work 'res/ai/overrides/biomass-mutation.properties')
$runtime=$work.Replace('\','/')
@("userDir=$runtime/user","cacheDir=$runtime/cache","decksDir=$runtime/decks")|Set-Content (Join-Path $work 'forge.profile.properties')
$sources=@('forge-ai/src/main/java/forge/ai/CombatBaseStatsAi.java','forge-ai/src/main/java/forge/ai/MirrorEntityAi.java','forge-ai/src/main/java/forge/ai/BiomassMutationAi.java','forge-ai/src/main/java/forge/ai/ability/AnimateAllAi.java'|ForEach-Object {Join-Path $repoPath $_})
& "$jdk/bin/javac.exe" -encoding UTF-8 -cp $candidate -d $classes @sources *> (Join-Path $out 'compile.log')
if($LASTEXITCODE -ne 0) { throw 'Compile failed; inspect compile.log' }
& "$jdk/bin/jar.exe" uf $candidate -C $classes forge
if($LASTEXITCODE -ne 0) { throw 'Private candidate overlay failed' }
$testSources=@(Join-Path $PSScriptRoot 'BiomassMutationTests.java')
if(!$CardOnly) { $testSources+=@($base.files|Where-Object {$_.path -match 'Tests.java$'}|ForEach-Object {$_.path}) }
& "$jdk/bin/javac.exe" -encoding UTF-8 -cp $candidate -d $classes @testSources *> (Join-Path $out 'test-compile.log')
if($LASTEXITCODE -ne 0) { throw 'Fixture compile failed; inspect test-compile.log' }
$results=@()
Push-Location $work
try { foreach($test in $testSources) {
    $suite=[IO.Path]::GetFileNameWithoutExtension($test); $log=Join-Path $out "$suite.log"
    $arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtime/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$classes;$candidate",$suite)
    & "$jdk/bin/java.exe" @arguments *> $log
    if($LASTEXITCODE -ne 0) { throw "$suite failed: $log" }
    $count=@(Select-String -LiteralPath $log -Pattern '^PASS ').Count
    $results+=@{suite=$suite;checks=$count;log=$log;sha256=(Get-FileHash $log).Hash}; Write-Output "$suite PASS: $count"
} } finally { Pop-Location }
$inputs=@($PSCommandPath,(Join-Path $basePath 'provenance.json'),$base.candidate,(Join-Path $work 'res/cardsfolder/cardsfolder.zip'),(Join-Path $work 'forge.profile.properties'),(Join-Path $repoPath 'forge-gui/res/cardsfolder/b/biomass_mutation.txt'))+$sources+$testSources
$inputs+=@(Get-ChildItem (Join-Path $work 'res/ai/overrides') -File|ForEach-Object FullName)
$inputs+=Join-Path $repoPath 'forge-gui/res/ai/overrides/biomass-mutation.properties'
@{date='2026-10-17';revision=(& git -C $repoPath rev-parse HEAD).Trim();engine='NeoForge 1.0.25';candidate=$candidate;candidateSha256=(Get-FileHash $candidate).Hash;runtime=$work;
 completeRegressionGate=(!$CardOnly -and $results.Count -eq 20);results=$results;files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});
 scope='Original-script native correctness and previous Commander regressions on the 1.0.25 engine; no strength claim'}|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out 'provenance.json')
