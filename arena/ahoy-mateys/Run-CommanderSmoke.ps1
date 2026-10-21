param([int]$Seed=95441,[ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label='commander-ahoy')
$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtimePath=(Join-Path $repoPath 'arena/runtime').Replace('\','/')
$profileText=Get-Content (Join-Path $repoPath 'forge-gui/forge.profile.properties') -Raw
foreach($key in @('userDir','cacheDir','decksDir')) {
    if($profileText -notmatch "(?m)^$key=$([regex]::Escape($runtimePath))/") { throw 'Expected isolated arena runtime profile' }
}
$jdkPath=(Get-ChildItem (Join-Path $repoPath 'arena/.tools') -Directory -Filter jdk-* | Select-Object -First 1).FullName
$engineJar=(Get-ChildItem (Join-Path $repoPath 'forge-gui-desktop/target') -Filter *jar-with-dependencies.jar | Select-Object -First 1).FullName
$outPath=Join-Path $PSScriptRoot "target/$Label"
$null=New-Item -ItemType Directory -Path $outPath -Force
# Private copy prevents another chat's build from replacing a running JVM's jar.
$privateJar=Join-Path $outPath 'candidate.jar'
Copy-Item -LiteralPath $engineJar -Destination $privateJar
$logPath=Join-Path $outPath 'commander.log'
$arguments=@('-Xmx4g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtimePath/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',$privateJar,'forge.view.Main','sim','--ai-profile=ahoy-mateys','-f','Commander','-n','1','-c','180','-s',"$Seed",'-a','Default','Default','-g','classic','classic','-p','classic','classic','-y','classic','classic')
Push-Location (Join-Path $repoPath 'forge-gui')
try {
    & (Join-Path $jdkPath 'bin/java.exe') @arguments *> $logPath
    if($LASTEXITCODE -ne 0) { throw "Commander smoke process failed: $LASTEXITCODE" }
} finally { Pop-Location }
$logText=Get-Content -LiteralPath $logPath -Raw
$normal=($logText -match 'Game Result: Game 1 ended in (\d+) ms' -and $logText -match 'has won!' -and $logText -notmatch 'Stopping slow match as draw|Exception|StackOverflowError')
$policyCards=@('Faithless Looting','Rakdos Charm','Windfall')
$castCards=@($policyCards | Where-Object { $logText -match ('Add To Stack:.* cast '+[regex]::Escape($_)) })
$hashes=@($privateJar,(Join-Path $repoPath 'forge-gui/res/ai/overrides/ahoy-mateys.properties'),$PSCommandPath)+@((Join-Path $repoPath 'forge-gui/res/quest/commanderprecons/Ahoy Mateys [LCC] [2023].dck')) | ForEach-Object { @{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash} }
@{normalCompletion=$normal;seed=$Seed;clockSeconds=180;policyCardCasts=$castCards;files=$hashes;scope='Commander runtime smoke only; not strength evidence'} | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $outPath 'summary.json')
if(!$normal) { throw 'Smoke requires normal completion within the declared clock; inspect log and summary' }
Write-Output "Commander smoke PASS; policy cards cast: $($castCards -join ', ')"
Write-Output "Evidence: $outPath"
