$ErrorActionPreference = 'Stop'
$arena = Split-Path $PSScriptRoot -Parent
$repo = Split-Path $arena -Parent
$jdk = (Get-ChildItem (Join-Path $arena '.tools') -Directory -Filter 'jdk-*' | Select-Object -First 1).FullName
$java = Join-Path $jdk 'bin/java.exe'
$jar = (Get-ChildItem (Join-Path $repo 'forge-gui-desktop/target') -Filter '*jar-with-dependencies.jar' | Select-Object -First 1).FullName
if (!$jar -or !(Test-Path $java)) { throw 'Build Forge and get portable tools first' }
$runtime = Join-Path $arena 'runtime'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$slashRuntime = $runtime.Replace('\','/')
@("userDir=$slashRuntime/user", "cacheDir=$slashRuntime/cache", "decksDir=$slashRuntime/decks") | Set-Content (Join-Path $repo 'forge-gui/forge.profile.properties')
$javaBase = @('-Xmx4g','-Djava.awt.headless=true','-Dfile.encoding=UTF-8',"-Duser.home=$runtime/home",'-Dsentry.dsn=','-Dsentry.enabled=false')
