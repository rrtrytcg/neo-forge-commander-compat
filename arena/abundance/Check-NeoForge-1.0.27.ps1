param([switch]$Focused,[switch]$RunOnly)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$basePath=Join-Path $repo 'arena/adaptive-enchantment/target/estrid-1.0.27';$base=Get-Content (Join-Path $basePath 'provenance.json') -Raw|ConvertFrom-Json
if($base.engine -ne 'NeoForge 1.0.27' -or $base.results.Count -ne 42 -or ($base.results|Measure-Object checks -Sum).Sum -ne 4484){throw 'Complete baseline 1.0.27 gate first'}
if((Get-FileHash $base.candidate).Hash -ne $base.candidateSha256){throw 'Baseline changed'}
if(!$Focused){ & python (Join-Path $PSScriptRoot 'verify-preserved.py');if($LASTEXITCODE){throw 'Installed files changed'} }
$jdk=(Get-ChildItem (Join-Path $repo 'arena/.tools') -Directory -Filter jdk-*|Select-Object -First 1).FullName
$out=Join-Path $PSScriptRoot 'target/final-1.0.27';$work=Join-Path $out 'runtime';$classes=Join-Path $out 'classes';$tests=Join-Path $out 'test-classes';$candidate=Join-Path $out 'engine.jar'
if(!$RunOnly){
    if(Test-Path (Join-Path $out 'provenance.json')){throw 'Final gate exists; preserve it'}
    $null=New-Item -ItemType Directory -Force $work,$classes,$tests
    if(!(Test-Path (Join-Path $work 'res/howto.txt'))){
        & python -c 'import shutil,sys;shutil.copytree(sys.argv[1],sys.argv[2],dirs_exist_ok=True)' (Join-Path $base.runtime 'res') (Join-Path $work 'res');if($LASTEXITCODE){throw 'Private resource copy failed'}
    }
    Copy-Item (Join-Path $repo 'forge-gui/res/ai/overrides/abundance.properties') (Join-Path $work 'res/ai/overrides/abundance.properties')
    Copy-Item (Join-Path $repo 'forge-gui/res/ai/overrides/adaptive-enchantment.properties') (Join-Path $work 'res/ai/overrides/adaptive-enchantment.properties')
    $runtime=$work.Replace('\','/');@("userDir=$runtime/user","cacheDir=$runtime/cache","decksDir=$runtime/decks")|Set-Content (Join-Path $work 'forge.profile.properties')
    Copy-Item $base.candidate $candidate
    $sourceRoot=Join-Path $out 'source';$reconciled=@();$patches=@()
    foreach($name in @('ComputerUtil','AiController')){
        $relative="forge-ai/src/main/java/forge/ai/$name.java";$dest=Join-Path $sourceRoot $relative
        $null=New-Item -ItemType Directory -Path (Split-Path $dest) -Force
        Copy-Item (Join-Path $repo "arena/version-compat-1.0.27/target/source/$relative") $dest
        $patch=Join-Path $PSScriptRoot "$($name.ToLower())-hooks.patch"; & git -C $repo diff 899567ea -- $relative | Set-Content $patch
        & git -C $repo apply --whitespace=error "--directory=$([IO.Path]::GetRelativePath($repo,$sourceRoot).Replace('\','/'))" $patch
        if($LASTEXITCODE){throw "Fresh compatibility hook reconciliation failed: $name"}
        $reconciled+=$dest;$patches+=$patch
    }
    $production=@((Join-Path $repo 'forge-ai/src/main/java/forge/ai/AbundanceAi.java'),(Join-Path $repo 'forge-ai/src/main/java/forge/ai/ability/ChooseGenericAi.java'),(Join-Path $repo 'forge-ai/src/main/java/forge/ai/ability/PermanentNoncreatureAi.java'))+$reconciled
    $production+=@('AdaptiveEnchantmentAi.java','ability/UntapAllAi.java','ability/TokenAi.java')|ForEach-Object {Join-Path $repo "forge-ai/src/main/java/forge/ai/$_"}
    & "$jdk/bin/javac.exe" -encoding UTF-8 -cp $candidate -d $classes @production *> (Join-Path $out 'production-compile.log');if($LASTEXITCODE){throw 'Production compilation failed'}
    & "$jdk/bin/jar.exe" uf $candidate -C $classes forge;if($LASTEXITCODE){throw 'Private overlay failed'}
    Copy-Item (Join-Path $basePath 'test-classes/*.class') $tests
    $testSource=Join-Path $PSScriptRoot 'AbundanceTests.java'
    & "$jdk/bin/javac.exe" -encoding UTF-8 -cp "$tests;$candidate" -d $tests $testSource *> (Join-Path $out 'test-compile.log');if($LASTEXITCODE){throw 'Test compilation failed'}
    $inputs=@((Join-Path $repo 'forge-gui/res/ai/overrides/adaptive-enchantment.properties'),(Join-Path $repo 'arena/adaptive-enchantment/EstridUntapTests.java'),$PSCommandPath,$base.candidate,(Join-Path $basePath 'provenance.json'),$testSource,(Join-Path $repo 'forge-ai/src/main/java/forge/ai/ComputerUtil.java'),(Join-Path $repo 'forge-ai/src/main/java/forge/ai/AiController.java'),(Join-Path $repo 'forge-gui/res/ai/overrides/abundance.properties'),(Join-Path $work 'forge.profile.properties'))+$production+$patches
    @{engine='NeoForge 1.0.27';revision=(& git -C $repo rev-parse HEAD).Trim();candidate=$candidate;candidateSha256=(Get-FileHash $candidate).Hash;runtime=$work;
      files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash $_).Hash}})}|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out 'build.json')
}
$build=Get-Content (Join-Path $out 'build.json') -Raw|ConvertFrom-Json
foreach($f in $build.files){if((Get-FileHash $f.path).Hash -ne $f.sha256){throw "Build input changed: $($f.path)"}}
if((Get-FileHash $candidate).Hash -ne $build.candidateSha256){throw 'Candidate changed'}
$suites=if($Focused){@('AbundanceTests')}else{@($base.results|ForEach-Object suite)+@('AbundanceTests')};$results=@();$runtime=$work.Replace('\','/')
Push-Location $work
try{foreach($suite in $suites){
    $log=Join-Path $out "$suite.log";$arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtime/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$tests;$candidate",$suite)
    & "$jdk/bin/java.exe" @arguments *> $log;if($LASTEXITCODE){throw "Native test failed: $suite"}
    $count=@(Select-String $log -Pattern '^PASS ').Count
    if($suite -ne 'AbundanceTests'){$expected=($base.results|Where-Object suite -eq $suite).checks;if($count -ne $expected){throw "Count mismatch: $suite"}}
    $results+=@{suite=$suite;checks=$count;log=$log;sha256=(Get-FileHash $log).Hash};Write-Output "$suite PASS: $count"
}}finally{Pop-Location}
$build|Add-Member results $results
$build|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out $(if($Focused){'focused-provenance.json'}else{'provenance.json'}))
Write-Output "DONE $(($results|Measure-Object checks -Sum).Sum) native checks"
