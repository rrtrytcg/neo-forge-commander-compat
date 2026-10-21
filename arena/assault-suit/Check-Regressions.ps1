$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$jdkPath=(Get-ChildItem (Join-Path $repoPath 'arena/.tools') -Directory -Filter jdk-* | Select-Object -First 1).FullName
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw | ConvertFrom-Json
foreach($file in $gate.files) {
    if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Native input changed: $($file.path)" }
}
$outPath=Join-Path $PSScriptRoot 'target/regressions'
$classes=Join-Path $outPath 'classes'
$null=New-Item -ItemType Directory -Path $classes -Force
$candidate=Join-Path $outPath 'engine.jar'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'target/final/engine.jar') -Destination $candidate
$production=@(
    'MarshalsAnthemAi','ability/PermanentAi','TragicArroganceAi','ability/RepeatEachAi',
    'ability/ChooseCardAi','DispatchAi','ability/TapAi','ability/ChangeZoneAi',
    'DarksteelMutationAi','ability/AttachAi','ability/ControlGainAi','AssaultSuitAi'
) | ForEach-Object { Join-Path $repoPath "forge-ai/src/main/java/forge/ai/$_.java" }
& (Join-Path $jdkPath 'bin/javac.exe') -encoding UTF-8 -cp $candidate -d $classes @production *> (Join-Path $outPath 'production-compile.log')
if($LASTEXITCODE -ne 0) { throw 'Production overlay compilation failed' }
& (Join-Path $jdkPath 'bin/jar.exe') uf $candidate -C $classes forge
if($LASTEXITCODE -ne 0) { throw 'Production overlay update failed' }
$sources=@(
    (Join-Path $repoPath 'arena/marshals-anthem/MarshalsAnthemTests.java'),
    (Join-Path $repoPath 'arena/mirror-entity/MirrorEntityTests.java'),
    (Join-Path $repoPath 'arena/decree-of-pain/DecreeOfPainTests.java'),
    (Join-Path $repoPath 'arena/oreskos-explorer/OreskosExplorerTests.java'),
    (Join-Path $repoPath 'arena/dispatch/DispatchTests.java'),
    (Join-Path $repoPath 'arena/slaughter/SlaughterTheStrongTests.java'),
    (Join-Path $repoPath 'arena/tragic-arrogance/TragicArroganceTests.java'),
    (Join-Path $repoPath 'arena/darksteel-mutation/DarksteelMutationTests.java'),
    (Join-Path $PSScriptRoot 'AssaultSuitTests.java')
)
& (Join-Path $jdkPath 'bin/javac.exe') -encoding UTF-8 -cp $candidate -d $classes @sources *> (Join-Path $outPath 'compile.log')
if($LASTEXITCODE -ne 0) { throw 'Regression compilation failed' }
$runtimePath=(Join-Path $repoPath 'arena/runtime').Replace('\','/')
$expected=@{MarshalsAnthemTests=135;MirrorEntityTests=48;DecreeOfPainTests=69;OreskosExplorerTests=100;DispatchTests=61;SlaughterTheStrongTests=18;TragicArroganceTests=132;DarksteelMutationTests=87;AssaultSuitTests=21}
$results=@()
Push-Location (Join-Path $repoPath 'forge-gui')
try {
    foreach($suite in @('MarshalsAnthemTests','MirrorEntityTests','DecreeOfPainTests','OreskosExplorerTests','DispatchTests','SlaughterTheStrongTests','TragicArroganceTests','DarksteelMutationTests','AssaultSuitTests')) {
        $logPath=Join-Path $outPath "$suite.log"
        $arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtimePath/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$classes;$candidate",$suite)
        & (Join-Path $jdkPath 'bin/java.exe') @arguments *> $logPath
        if($LASTEXITCODE -ne 0) { throw "$suite failed: $logPath" }
        $checks=@(Select-String -LiteralPath $logPath -Pattern '^PASS ').Count
        if($checks -ne $expected[$suite]) { throw "$suite unexpected count: $checks" }
        $results+=@{suite=$suite;checks=$checks;log=$logPath;sha256=(Get-FileHash -LiteralPath $logPath).Hash}
        Write-Output "$suite PASS: $checks"
    }
} finally { Pop-Location }
$files=@($candidate,$PSCommandPath)+$production+$sources | ForEach-Object { @{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash} }
@{results=$results;total=(($results|Measure-Object checks -Sum).Sum);files=$files;scope='Previous Commander suites and this native gate on the same private candidate'} | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outPath 'summary.json')
