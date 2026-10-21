$ErrorActionPreference = 'Stop'
$arena = Split-Path $PSScriptRoot -Parent
$repo = Split-Path $arena -Parent
$env:JAVA_HOME = (Get-ChildItem (Join-Path $arena '.tools') -Directory -Filter 'jdk-*' | Select-Object -First 1).FullName
if (!$env:JAVA_HOME) { throw 'Run Get-Tools.ps1 first' }
$mvn = Join-Path $arena '.tools/apache-maven-3.9.9/bin/mvn.cmd'
Push-Location $repo
try {
    & $mvn -version
    & $mvn -B '-Dmaven.repo.local=arena/.tools/m2' -pl forge-gui-desktop -am package -DskipTests *> (Join-Path $arena 'evidence/build.log')
    if ($LASTEXITCODE -ne 0) { throw "Build failed; see arena/evidence/build.log" }
} finally { Pop-Location }
