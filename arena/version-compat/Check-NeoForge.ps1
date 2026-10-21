param([string]$InstallPath, [ValidatePattern('^[a-zA-Z0-9_.-]+$')][string]$Label='neoforge-1.0.25', [switch]$BuildOnly)
$ErrorActionPreference='Stop'
$repoPath=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if(!$InstallPath) { $InstallPath=Join-Path $repoPath '../NeoForge' }
$InstallPath=(Resolve-Path $InstallPath).Path
$upstream='64569999b8b99dd7176ab9d3e1e6008f3ab68590'
$base='e8ca48dd'
$jdk=(Get-ChildItem (Join-Path $repoPath 'arena/.tools') -Directory -Filter jdk-*|Select-Object -First 1).FullName
$out=Join-Path $PSScriptRoot "target/$Label"
$classes=Join-Path $out 'classes'; $src=Join-Path $out 'source'; $work=Join-Path $out 'runtime'
foreach($dir in @($classes,$src,$work)) { $null=New-Item -ItemType Directory -Path $dir -Force }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $InstallPath 'app/neo.jar'))
try { $reader=[IO.StreamReader]::new($zip.GetEntry('forge/neo/build.properties').Open()); try {$build=$reader.ReadToEnd()} finally {$reader.Dispose()} } finally {$zip.Dispose()}
if($build -notmatch '(?m)^neoVersion=1\.0\.25\s*$') { throw 'Unsupported installed version; audit its upstream pin before adding a new lane' }
& git -C $repoPath cat-file -e $upstream
if($LASTEXITCODE -ne 0) { throw "Fetch the pinned official Forge revision first: git fetch --no-tags --depth=32 origin $upstream" }

# Never modify historical source or frozen jars: reconcile changed shared hooks in scratch sources.
$merged=@('AiAttackController','AiBlockController','AiController','ComputerUtil')
foreach($name in $merged) {
    $path="forge-ai/src/main/java/forge/ai/$name.java"
    $dest=Join-Path $src $path; $null=New-Item -ItemType Directory -Path (Split-Path $dest) -Force
    $text=(& git -C $repoPath show "${upstream}:$path") -join "`n"
    [IO.File]::WriteAllText($dest,$text+"`n")
}
$patch=Join-Path $PSScriptRoot 'neoforge-1.0.25-hooks.patch'
$relativeSrc=[IO.Path]::GetRelativePath($repoPath,$src).Replace('\','/')
& git -C $repoPath apply --whitespace=error --directory=$relativeSrc $patch
if($LASTEXITCODE -ne 0) { throw 'Version hook patch no longer applies cleanly' }
$manifest=Get-Content (Join-Path $PSScriptRoot 'hook-sources.json') -Raw|ConvertFrom-Json
foreach($file in $manifest) {
    if((Get-FileHash (Join-Path $repoPath $file.path)).Hash -ne $file.sha256) { throw "Shared hook changed; reconcile the version patch first: $($file.path)" }
}

# Use installed native modules plus the desktop harness/dependencies. Only scratch candidates are updated.
$desktop=(Get-ChildItem (Join-Path $repoPath 'forge-gui-desktop/target') -Filter *jar-with-dependencies.jar|Select-Object -First 1).FullName
$candidate=Join-Path $out 'engine.jar'; Copy-Item -LiteralPath $desktop -Destination $candidate
$native=Join-Path $out 'native'; $null=New-Item -ItemType Directory -Path $native -Force
$jars=@('core','game','ai','gui'|ForEach-Object { Join-Path $InstallPath "app/forge-$_-2.0.16-SNAPSHOT.jar" })
foreach($jar in $jars) {
    $zip=[IO.Compression.ZipFile]::OpenRead($jar)
    try { foreach($entry in $zip.Entries) {
        if(!$entry.FullName.EndsWith('.class')) { continue }
        $dest=Join-Path $native $entry.FullName
        $null=New-Item -ItemType Directory -Path (Split-Path $dest) -Force
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$dest,$true)
    } } finally {$zip.Dispose()}
}
& "$jdk/bin/jar.exe" uf $candidate -C $native forge
if($LASTEXITCODE -ne 0) { throw 'Native module overlay failed' }
$paths=@(& git -C $repoPath diff --name-only $base HEAD -- forge-ai/src/main/java forge-game/src/main/java forge-core/src/main/java|Where-Object { $_.EndsWith('.java') })
$sources=@($paths|ForEach-Object {
    if([IO.Path]::GetFileNameWithoutExtension($_) -in $merged) { Join-Path $src $_ } else { Join-Path $repoPath $_ }
})
& "$jdk/bin/javac.exe" -encoding UTF-8 -cp $candidate -d $classes @sources *> (Join-Path $out 'compile.log')
if($LASTEXITCODE -ne 0) { throw 'Version overlay compilation failed; inspect compile.log' }
& "$jdk/bin/jar.exe" uf $candidate -C $classes forge
if($LASTEXITCODE -ne 0) { throw 'Policy overlay failed' }

# Private assets are copied, never linked to writable installed resources or user data.
if(!(Test-Path -LiteralPath (Join-Path $work 'res/howto.txt'))) {
    Copy-Item -LiteralPath (Join-Path $InstallPath 'res') -Destination $work -Recurse -Force
}
if((Get-FileHash (Join-Path $work 'res/cardsfolder/cardsfolder.zip')).Hash -ne (Get-FileHash (Join-Path $InstallPath 'res/cardsfolder/cardsfolder.zip')).Hash) {
    throw 'Private resource snapshot is stale; choose a fresh Label'
}
Copy-Item -LiteralPath (Join-Path $repoPath 'forge-gui/res/ai/overrides') -Destination (Join-Path $work 'res/ai') -Recurse -Force
$runtime=$work.Replace('\','/')
@("userDir=$runtime/user","cacheDir=$runtime/cache","decksDir=$runtime/decks")|Set-Content (Join-Path $work 'forge.profile.properties')
$tests=@(
 'adaptive-enchantment/AdaptiveEnchantmentTests','adaptive-enchantment/family/LandManaFamilyTests',
 'ahoy-mateys/AhoyMateysTests','beast-within/BeastWithinTests','curse-of-the-swine/CurseOfTheSwineTests',
 'filter-lands/FilterLandsTests','psychosis-crawler/PsychosisCrawlerTests','wall-of-limbs/WallOfLimbsTests',
 'slaughter/SlaughterTheStrongTests','mirror-entity/MirrorEntityTests','decree-of-pain/DecreeOfPainTests',
 'oreskos-explorer/OreskosExplorerTests','dispatch/DispatchTests','tragic-arrogance/TragicArroganceTests',
 'marshals-anthem/MarshalsAnthemTests','darksteel-mutation/DarksteelMutationTests'
)
$testSources=@($tests|ForEach-Object {Join-Path $repoPath "arena/$_.java"})
$testClasses=Join-Path $out 'test-classes'; $null=New-Item -ItemType Directory -Path $testClasses -Force
& "$jdk/bin/javac.exe" -encoding UTF-8 -cp $candidate -d $testClasses @testSources *> (Join-Path $out 'test-compile.log')
if($LASTEXITCODE -ne 0) { throw 'Native fixture compilation failed' }
$results=@()
if(!$BuildOnly) {
    Push-Location $work
    try { foreach($test in $tests) {
        $suite=$test.Split('/')[-1]; $log=Join-Path $out "$suite.log"
        $arguments=@('-Xmx2g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtime/home",'-Dsentry.dsn=','-Dsentry.enabled=false','-cp',"$testClasses;$candidate",$suite)
        & "$jdk/bin/java.exe" @arguments *> $log
        if($LASTEXITCODE -ne 0) { throw "Version fixture failed: $suite" }
        $checks=@(Select-String -LiteralPath $log -Pattern '^PASS ').Count
        $results+=@{suite=$suite;checks=$checks;log=$log;sha256=(Get-FileHash $log).Hash}
        Write-Output "$suite PASS: $checks"
    } } finally { Pop-Location }
}
$inputs=@($desktop,$patch,$PSCommandPath,(Join-Path $PSScriptRoot 'hook-sources.json'),(Join-Path $InstallPath 'app/neo.jar'),(Join-Path $InstallPath 'res/cardsfolder/cardsfolder.zip'))+$jars+$sources+$testSources
$files=@($inputs|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash}})
@{date=(Get-Date -Format yyyy-MM-dd);revision=(& git -C $repoPath rev-parse HEAD);installedBuild=$build;upstreamRef=$upstream;
 candidate=$candidate;candidateSha256=(Get-FileHash $candidate).Hash;files=$files;results=$results;runtime=$work;
 scope='Commander compatibility against installed native engine/resources; historical baselines unchanged; no strength or broad research-policy validation'}|
 ConvertTo-Json -Depth 8|Set-Content (Join-Path $out 'summary.json')
Write-Output "Version compatibility evidence: $out"
