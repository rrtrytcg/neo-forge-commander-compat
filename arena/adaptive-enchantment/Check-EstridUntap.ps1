param([switch]$Baseline,[switch]$Focused,[switch]$RunOnly)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$basePath=Join-Path $repo 'arena/deadly-tempest/target/final-1.0.27';$base=Get-Content (Join-Path $basePath 'provenance.json') -Raw|ConvertFrom-Json
if($base.engine -ne 'NeoForge 1.0.27' -or $base.results.Count -ne 41 -or ($base.results|Measure-Object checks -Sum).Sum -ne 4445){throw 'Complete the Deadly Tempest 1.0.27 gate first'}
if((Get-FileHash $base.candidate).Hash -ne $base.candidateSha256){throw 'Baseline candidate changed'}
# Installed app/resources only: the audit's 18 'unrelated' files were this Estrid fixture (now rewritten)
# and the stompy/aristocrats labs removed in 904ff985.
if(!$Focused -and !$Baseline){
    & python -I -c 'import hashlib,json,sys;b=json.load(open(sys.argv[1]))["installed"];bad=[f["path"] for f in b if hashlib.sha256(open(f["path"],"rb").read()).hexdigest().upper()!=f["sha256"]];print(len(b),"installed hashes checked,",len(bad),"changed");sys.exit(1 if bad else 0)' (Join-Path $repo 'arena/version-compat-1.0.27/target/preserved-baseline.json')
    if($LASTEXITCODE){throw 'Installed app/resources changed'}
}
$jdk=(Get-ChildItem (Join-Path $repo 'arena/.tools') -Directory -Filter jdk-*|Select-Object -First 1).FullName
$out=Join-Path $PSScriptRoot $(if($Baseline){'target/estrid-baseline-1.0.27'}else{'target/estrid-1.0.27'})
$work=Join-Path $out 'runtime';$classes=Join-Path $out 'classes';$tests=Join-Path $out 'test-classes';$candidate=Join-Path $out 'engine.jar'
$profile=Join-Path $repo 'forge-gui/res/ai/overrides/adaptive-enchantment.properties'
# Every overlaid source is identical to the native 1.0.27 class (UntapAllAi) or the reconciled overlay source.
$production=@('AdaptiveEnchantmentAi','ability/UntapAllAi','ability/TokenAi')|ForEach-Object {Join-Path $repo "forge-ai/src/main/java/forge/ai/$_.java"}
$testSource=Join-Path $PSScriptRoot 'EstridUntapTests.java'
if(!$RunOnly){
    if(!$Focused -and (Test-Path (Join-Path $out 'provenance.json'))){throw 'Final gate exists; preserve it'}
    $null=New-Item -ItemType Directory -Force $work,$classes,$tests
    if(!(Test-Path (Join-Path $work 'res/howto.txt'))){
        & python -c 'import shutil,sys;shutil.copytree(sys.argv[1],sys.argv[2],dirs_exist_ok=True)' (Join-Path $base.runtime 'res') (Join-Path $work 'res');if($LASTEXITCODE){throw 'Private resource copy failed'}
    }
    if(!$Baseline){Copy-Item $profile (Join-Path $work 'res/ai/overrides/adaptive-enchantment.properties') -Force}
    $runtime=$work.Replace('\','/');@("userDir=$runtime/user","cacheDir=$runtime/cache","decksDir=$runtime/decks")|Set-Content (Join-Path $work 'forge.profile.properties')
    Copy-Item $base.candidate $candidate -Force
    if(!$Baseline){
        & "$jdk/bin/javac.exe" -encoding UTF-8 -cp $candidate -d $classes @production *> (Join-Path $out 'production-compile.log');if($LASTEXITCODE){throw 'Production compilation failed'}
        & "$jdk/bin/jar.exe" uf $candidate -C $classes forge;if($LASTEXITCODE){throw 'Private overlay failed'}
    }
    Copy-Item (Join-Path $basePath 'test-classes/*.class') $tests -Force
    & "$jdk/bin/javac.exe" -encoding UTF-8 -cp "$tests;$candidate" -d $tests $testSource *> (Join-Path $out 'test-compile.log');if($LASTEXITCODE){throw 'Test compilation failed'}
    $inputs=@($PSCommandPath,$testSource,$base.candidate,(Join-Path $basePath 'provenance.json'),(Join-Path $work 'res/ai/overrides/adaptive-enchantment.properties'),(Join-Path $work 'forge.profile.properties'))
    if(!$Baseline){$inputs+=$production}
    @{engine='NeoForge 1.0.27';revision=(& git -C $repo rev-parse HEAD).Trim();baseline=[bool]$Baseline;candidate=$candidate;candidateSha256=(Get-FileHash $candidate).Hash;runtime=$work;
      files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash $_).Hash}})}|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out 'build.json')
}
$build=Get-Content (Join-Path $out 'build.json') -Raw|ConvertFrom-Json
foreach($f in $build.files){if((Get-FileHash $f.path).Hash -ne $f.sha256){throw "Build input changed: $($f.path)"}}
if((Get-FileHash $candidate).Hash -ne $build.candidateSha256){throw 'Candidate changed'}
$suites=if($Baseline -or $Focused){@('EstridUntapTests')}else{@($base.results|ForEach-Object suite)+@('EstridUntapTests')};$results=@();$runtime=$work.Replace('\','/')
Push-Location $work
try{foreach($suite in $suites){
    $log=Join-Path $out "$suite.log";$arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtime/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$tests;$candidate",$suite)
    if($Baseline){$arguments+='baseline'}
    & "$jdk/bin/java.exe" @arguments *> $log;if($LASTEXITCODE){throw "Native test failed: $suite ($log)"}
    $count=@(Select-String $log -Pattern '^PASS ').Count
    if($suite -ne 'EstridUntapTests'){$expected=($base.results|Where-Object suite -eq $suite).checks;if($count -ne $expected){throw "Count mismatch: $suite"}}
    $results+=@{suite=$suite;checks=$count;log=$log;sha256=(Get-FileHash $log).Hash};Write-Output "$suite PASS: $count"
}}finally{Pop-Location}
$build|Add-Member results $results
$build|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out $(if($Focused){'focused-provenance.json'}else{'provenance.json'}))
Write-Output "DONE $(($results|Measure-Object checks -Sum).Sum) native checks"
