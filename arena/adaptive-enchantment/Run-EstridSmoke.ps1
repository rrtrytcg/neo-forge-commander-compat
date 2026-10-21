param([ValidateSet('on','off')][string]$ProfileMode='on',[int]$Seed=114011,[ValidateRange(30,600)][int]$ClockSeconds=180)
# Real C18 Adaptive Enchantment vs the other three C18 precons, unchanged decks; counts Estrid loyalty uses from the game log.
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$gate=Get-Content (Join-Path $PSScriptRoot 'target/estrid-1.0.27/provenance.json') -Raw|ConvertFrom-Json
if($gate.engine -ne 'NeoForge 1.0.27' -or $gate.results.Count -ne 42){throw 'Complete the full Estrid gate first'}
foreach($f in $gate.files){if((Get-FileHash $f.path).Hash -ne $f.sha256){throw "Native input changed: $($f.path)"}}
if((Get-FileHash $gate.candidate).Hash -ne $gate.candidateSha256){throw 'Candidate changed'}
$jdk=(Get-ChildItem (Join-Path $repo 'arena/.tools') -Directory -Filter jdk-*|Select-Object -First 1).FullName
$out=Join-Path $PSScriptRoot "target/estrid-smoke/$ProfileMode-$Seed";$null=New-Item -ItemType Directory -Force $out
$decks=Join-Path $gate.runtime 'res/quest/commanderprecons'
$names=@('Adaptive Enchantment','Exquisite Invention',"Nature's Vengeance",'Subjective Reality')|ForEach-Object {"$_ [C18] [2018].dck"}
$arguments=@('-Xmx3g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$($gate.runtime)/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',$gate.candidate,'forge.view.Main','sim')
if($ProfileMode -eq 'on'){$arguments+='--ai-profile=adaptive-enchantment'}
$arguments+=@('-D',$decks,'-d')+$names+@('-f','Commander','-n','1','-c',"$ClockSeconds",'-s',"$Seed",'-a','Default','Default','Default','Default')
$log=Join-Path $out 'game.log'
Push-Location $gate.runtime
try{& "$jdk/bin/java.exe" @arguments *> $log;$exit=$LASTEXITCODE}finally{Pop-Location}
$lines=Get-Content $log
$summary=[ordered]@{profile=$ProfileMode;seed=$Seed;clockSeconds=$ClockSeconds;exit=$exit;
  estridCast=@($lines|Where-Object {$_ -match 'cast Estrid, the Masked$'}).Count;
  plusTwo=@($lines|Where-Object {$_ -match '^Resolve Stack: Estrid, the Masked .* - Untap each enchanted'}).Count;
  mask=@($lines|Where-Object {$_ -match '^Resolve Stack: Estrid, the Masked .*named Mask'}).Count;
  maskTargets=@($lines|Where-Object {$_ -match 'activated Estrid, the Masked targeting \[(.+)\]'}|ForEach-Object {$_ -replace '^.*targeting \[(.+)\]$','$1'});
  ultimate=@($lines|Where-Object {$_ -match '^Resolve Stack: Estrid, the Masked .*Mill seven'}).Count;
  outcome=@($lines|Where-Object {$_ -match 'Game Outcome|has won|draw'})|Select-Object -Last 3;
  exceptions=@($lines|Where-Object {$_ -match 'Exception' -and $_ -notmatch 'InterruptedException'}).Count;
  candidateSha256=$gate.candidateSha256;command=@("$jdk/bin/java.exe")+$arguments;
  scope='Card-use smoke with unchanged precon decks; no winner or strength claim'}
$summary|ConvertTo-Json -Depth 6|Set-Content (Join-Path $out 'summary.json')
$summary|ConvertTo-Json -Depth 3
