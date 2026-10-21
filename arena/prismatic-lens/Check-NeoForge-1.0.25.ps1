param([switch]$Focused)
$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$basePath=Join-Path $repoPath 'arena/call-the-coppercoats/target/final-1.0.25'
$base=Get-Content (Join-Path $basePath 'provenance.json') -Raw | ConvertFrom-Json
if($base.engine -ne 'NeoForge 1.0.25' -or $base.results.Count -ne 31 -or ($base.results|Measure-Object checks -Sum).Sum -ne 2725) { throw 'Complete the Call the Coppercoats gate first' }
if((Get-FileHash $base.candidate).Hash -ne $base.candidateSha256) { throw 'Compatibility baseline candidate changed' }
$version=Get-Content (Join-Path $repoPath 'arena/version-compat/verification-summary.json') -Raw | ConvertFrom-Json
$install=(Resolve-Path (Join-Path $repoPath '../NeoForge')).Path
foreach($file in $version.nativeEvidence.files) {
    if($file.path.StartsWith($install,[StringComparison]::OrdinalIgnoreCase) -and (Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw 'Installed 1.0.25 engine/assets changed; audit the update first' }
}
$jdk=(Get-ChildItem (Join-Path $repoPath 'arena/.tools') -Directory -Filter jdk-* | Select-Object -First 1).FullName
$out=Join-Path $PSScriptRoot 'target/final-1.0.25'
$classes=Join-Path $out 'classes'; $tests=Join-Path $out 'test-classes'; $work=Join-Path $out 'runtime'
$null=New-Item -ItemType Directory -Path $classes,$tests,$work -Force
if(!(Test-Path (Join-Path $work 'res/howto.txt'))) { Copy-Item -LiteralPath (Join-Path $base.runtime 'res') -Destination $work -Recurse }
foreach($profile in @('prismatic-lens')) {
    Copy-Item -LiteralPath (Join-Path $repoPath "forge-gui/res/ai/overrides/$profile.properties") -Destination (Join-Path $work "res/ai/overrides/$profile.properties") -Force
}
$runtime=$work.Replace('\','/')
@("userDir=$runtime/user","cacheDir=$runtime/cache","decksDir=$runtime/decks")|Set-Content (Join-Path $work 'forge.profile.properties')
$candidate=Join-Path $out 'engine.jar'; Copy-Item -LiteralPath $base.candidate -Destination $candidate
$production=@('AiOverrideProfile','ComputerUtilMana') | ForEach-Object { Join-Path $repoPath "forge-ai/src/main/java/forge/ai/$_.java" }
& (Join-Path $jdk 'bin/javac.exe') -encoding UTF-8 -cp $candidate -d $classes @production *> (Join-Path $out 'production-compile.log')
if($LASTEXITCODE -ne 0) { throw 'Production overlay compilation failed; inspect production-compile.log' }
& (Join-Path $jdk 'bin/jar.exe') uf $candidate -C $classes forge
if($LASTEXITCODE -ne 0) { throw 'Private candidate update failed' }
Copy-Item -Path (Join-Path $basePath 'test-classes/*.class') -Destination $tests -Force
$testSources=@((Join-Path $PSScriptRoot 'PrismaticLensTests.java'))
& (Join-Path $jdk 'bin/javac.exe') -encoding UTF-8 -cp "$tests;$candidate" -d $tests @testSources *> (Join-Path $out 'test-compile.log')
if($LASTEXITCODE -ne 0) { throw 'Fixture compilation failed; inspect test-compile.log' }
$suites=if($Focused) { @('PrismaticLensTests') } else { @($base.results | ForEach-Object suite) + 'PrismaticLensTests' }
$results=@()
Push-Location $work
try { foreach($suite in $suites) {
    $log=Join-Path $out "$suite.log"
    $arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtime/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$tests;$candidate",$suite)
    & (Join-Path $jdk 'bin/java.exe') @arguments *> $log
    if($LASTEXITCODE -ne 0) { throw "$suite failed: $log" }
    $count=@(Select-String -LiteralPath $log -Pattern '^PASS ').Count
    $expected=if($suite -eq 'PrismaticLensTests') {161} else {($base.results | Where-Object suite -eq $suite).checks}
    if($count -ne $expected) { throw "$suite unexpected count: $count (expected $expected)" }
    $results+=@{suite=$suite;checks=$count;log=$log;sha256=(Get-FileHash $log).Hash}; Write-Output "$suite PASS: $count"
} } finally { Pop-Location }
$inputs=@($PSCommandPath,(Join-Path $basePath 'provenance.json'),$base.candidate,(Join-Path $work 'forge.profile.properties'),
    (Join-Path $repoPath 'forge-gui/res/ai/overrides/prismatic-lens.properties'))+$production+$testSources
@{date='2026-10-17';revision=(& git -C $repoPath rev-parse HEAD).Trim();engine='NeoForge 1.0.25';candidate=$candidate;
 candidateSha256=(Get-FileHash $candidate).Hash;runtime=$work;results=$results;
 files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}});
 scope='Native correctness and prior Commander regressions on the installed 1.0.25 engine; no strength claim'}|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out $(if($Focused) {'focused-provenance.json'} else {'provenance.json'}))
Write-Output "DONE $(($results|Measure-Object checks -Sum).Sum) checks on NeoForge 1.0.25"



