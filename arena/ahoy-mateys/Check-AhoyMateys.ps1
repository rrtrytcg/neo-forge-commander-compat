param([ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label='ahoy-mateys')
$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$jdkPath=(Get-ChildItem (Join-Path $repoPath 'arena/.tools') -Directory -Filter jdk-* | Select-Object -First 1).FullName
$engineJar=(Get-ChildItem (Join-Path $repoPath 'forge-gui-desktop/target') -Filter *jar-with-dependencies.jar | Select-Object -First 1).FullName
$outPath=Join-Path $PSScriptRoot "target/$Label"
$classPath=Join-Path $outPath 'classes'
$null=New-Item -ItemType Directory -Path $classPath -Force
# Compile and run against a private snapshot while other chats may rebuild the shared jar.
$privateJar=Join-Path $outPath 'engine.jar'
Copy-Item -LiteralPath $engineJar -Destination $privateJar
$engineJar=$privateJar
$runtimePath=(Join-Path $repoPath 'arena/runtime').Replace('\','/')
$profileText=Get-Content (Join-Path $repoPath 'forge-gui/forge.profile.properties') -Raw
foreach($key in @('userDir','cacheDir','decksDir')) {
    if($profileText -notmatch "(?m)^$key=$([regex]::Escape($runtimePath))/") { throw 'Expected isolated arena runtime profile' }
}
$sources=@('AiOverrideProfile','AiProfileUtil','AhoyMateysAi','AiController') | ForEach-Object { Join-Path $repoPath "forge-ai/src/main/java/forge/ai/$_.java" }
foreach ($ability in @('DrawAi','DiscardAi','CharmAi')) {
    $sources+=Join-Path $repoPath "forge-ai/src/main/java/forge/ai/ability/$ability.java"
}
$sources+=Join-Path $PSScriptRoot 'AhoyMateysTests.java'
& (Join-Path $jdkPath 'bin/javac.exe') -encoding UTF-8 -cp $engineJar -d $classPath @sources *> (Join-Path $outPath 'compile.log')
if($LASTEXITCODE -ne 0) { throw 'Compilation failed; inspect compile.log' }
$arguments=@('-Xmx4g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtimePath/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$classPath;$engineJar")
Push-Location (Join-Path $repoPath 'forge-gui')
try {
    & (Join-Path $jdkPath 'bin/java.exe') @arguments 'AhoyMateysTests' *> (Join-Path $outPath 'AhoyMateysTests.log')
    if($LASTEXITCODE -ne 0) { throw 'AhoyMateysTests failed; inspect its log' }
    Write-Output "AhoyMateysTests PASS ($(@(Select-String -LiteralPath (Join-Path $outPath 'AhoyMateysTests.log') -Pattern '^PASS ').Count) checks)"
} finally { Pop-Location }
$hashes=@($engineJar, (Join-Path $repoPath 'forge-gui/res/ai/overrides/ahoy-mateys.properties'), $PSCommandPath)+$sources | ForEach-Object { @{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash} }
@{revision=(& git -C $repoPath rev-parse HEAD).Trim(); files=$hashes; scope='Native correctness and regression only; not strength evidence'} | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $outPath 'provenance.json')
Write-Output "Evidence: $outPath"
