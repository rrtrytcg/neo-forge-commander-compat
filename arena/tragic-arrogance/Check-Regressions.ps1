$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$jdkPath=(Get-ChildItem (Join-Path $repoPath 'arena/.tools') -Directory -Filter jdk-* | Select-Object -First 1).FullName
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final/provenance.json') -Raw | ConvertFrom-Json
foreach($file in $gate.files) {
    if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Native input changed: $($file.path)" }
}
$candidate=Join-Path $PSScriptRoot 'target/final/engine.jar'
$outPath=Join-Path $PSScriptRoot 'target/regressions'; $classes=Join-Path $outPath 'classes'
$null=New-Item -ItemType Directory -Path $classes -Force
$sources=@((Join-Path $repoPath 'arena/mirror-entity/MirrorEntityTests.java'),(Join-Path $repoPath 'arena/decree-of-pain/DecreeOfPainTests.java'),(Join-Path $repoPath 'arena/oreskos-explorer/OreskosExplorerTests.java'),(Join-Path $repoPath 'arena/dispatch/DispatchTests.java'),(Join-Path $repoPath 'arena/slaughter/SlaughterTheStrongTests.java'))
& (Join-Path $jdkPath 'bin/javac.exe') -encoding UTF-8 -cp $candidate -d $classes @sources *> (Join-Path $outPath 'compile.log')
if($LASTEXITCODE -ne 0) { throw 'Regression compilation failed' }
$runtimePath=(Join-Path $repoPath 'arena/runtime').Replace('\','/')
$results=@()
Push-Location (Join-Path $repoPath 'forge-gui')
try {
    foreach($suite in @('MirrorEntityTests','DecreeOfPainTests','OreskosExplorerTests','DispatchTests','SlaughterTheStrongTests')) {
        $logPath=Join-Path $outPath "$suite.log"
        $arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtimePath/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$classes;$candidate",$suite)
        & (Join-Path $jdkPath 'bin/java.exe') @arguments *> $logPath
        if($LASTEXITCODE -ne 0) { throw "$suite failed" }
        $checks=@(Select-String -LiteralPath $logPath -Pattern '^PASS ').Count
        if($checks -ne $(if($suite -eq 'MirrorEntityTests') {48} elseif($suite -eq 'OreskosExplorerTests') {100} elseif($suite -eq 'DispatchTests') {61} elseif($suite -eq 'SlaughterTheStrongTests') {18} else {69})) { throw 'Unexpected regression count' }
        $results+=@{suite=$suite;checks=$checks;log=$logPath;sha256=(Get-FileHash -LiteralPath $logPath).Hash}
        Write-Output "$suite PASS: $checks"
    }
} finally { Pop-Location }
$files=@($candidate,$PSCommandPath)+$sources | ForEach-Object { @{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash} }
@{results=$results;files=$files} | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outPath 'summary.json')
