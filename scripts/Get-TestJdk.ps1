param([Parameter(Mandatory)][string]$Destination)
$ErrorActionPreference = 'Stop'
$version = '17.0.20.1+1'
$archive = 'OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip'
$url = 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/' + $archive
$sha256 = 'e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0'
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$jdk = Get-ChildItem -LiteralPath $Destination -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk -and (Test-Path (Join-Path $jdk.FullName 'bin/javac.exe'))) { return $jdk.FullName }
$zip = Join-Path $Destination $archive
if (!(Test-Path $zip) -or (Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sha256) {
    Invoke-WebRequest -Uri $url -OutFile $zip
}
if ((Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sha256) { throw 'Temurin JDK archive SHA-256 mismatch.' }
Expand-Archive -LiteralPath $zip -DestinationPath $Destination -Force
$jdk = Get-ChildItem -LiteralPath $Destination -Directory -Filter 'jdk-17*' | Select-Object -First 1
if (!$jdk -or !(Test-Path (Join-Path $jdk.FullName 'bin/javac.exe'))) { throw 'JDK 17 extraction did not produce javac.exe.' }
return $jdk.FullName
