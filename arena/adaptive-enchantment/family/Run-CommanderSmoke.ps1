param([int]$Seed=95331,[ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label='commander-family')
$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
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
$deckPath=Join-Path $PSScriptRoot 'decks'
$logPath=Join-Path $outPath 'commander.log'
$arguments=@('-Xmx4g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtimePath/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',$privateJar,'forge.view.Main','sim','--ai-profile=adaptive-enchantment','-f','Commander','-n','1','-c','180','-s',"$Seed",'-D',$deckPath,'-d','FamilySmoke_A.dck','FamilySmoke_B.dck','-a','Default','Default','-g','classic','classic','-p','classic','classic','-y','classic','classic')
Push-Location (Join-Path $repoPath 'forge-gui')
try {
    & (Join-Path $jdkPath 'bin/java.exe') @arguments *> $logPath
    if($LASTEXITCODE -ne 0) { throw "Commander smoke process failed: $LASTEXITCODE" }
} finally { Pop-Location }
$logText=Get-Content -LiteralPath $logPath -Raw
$normal=($logText -match 'Game Result: Game 1 ended in (\d+) ms' -and $logText -match 'has won!' -and $logText -notmatch 'Stopping slow match as draw|Exception|StackOverflowError')
$manaCards=@('Wild Growth','Fertile Ground',"Dawn's Reflection",'Overgrowth','Market Festival','Utopia Sprawl','Shimmerwilds Growth','Trace of Abundance','Glittering Frost','Verdant Haven','Wolfwillow Haven','Abundant Growth','Gift of Paradise','Sheltered Aerie','Urban Utopia')
$castCards=@($manaCards | Where-Object { $logText -match ('Add To Stack:.* cast '+[regex]::Escape($_)+' targeting') })
$hashes=@($privateJar,(Join-Path $repoPath 'forge-gui/res/ai/overrides/adaptive-enchantment.properties'),$PSCommandPath)+@(Get-ChildItem -LiteralPath $deckPath -Filter *.dck | ForEach-Object FullName) | ForEach-Object { @{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash} }
@{normalCompletion=$normal;seed=$Seed;clockSeconds=180;manaAuraCasts=$castCards;files=$hashes;scope='Commander runtime smoke only; not strength evidence'} | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $outPath 'summary.json')
if(!$normal -or $castCards.Count -lt 3) { throw 'Smoke requires normal completion and at least three distinct mana Auras cast; inspect log and summary' }
Write-Output "Commander smoke PASS; distinct mana Auras cast: $($castCards -join ', ')"
Write-Output "Evidence: $outPath"
