$ErrorActionPreference = 'Stop'
$toolDir = Join-Path (Split-Path $PSScriptRoot -Parent) '.tools'
New-Item -ItemType Directory -Path $toolDir -Force | Out-Null
$pinnedDownloads = Join-Path (Split-Path $PSScriptRoot -Parent) 'evidence/tool-downloads.json'
if (Test-Path $pinnedDownloads) {
    $package = (Get-Content $pinnedDownloads -Raw | ConvertFrom-Json).jdk
} else {
    $release = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = $release[0].binary.package
}
$jdkZip = Join-Path $toolDir 'jdk.zip'
Invoke-WebRequest $package.link -OutFile $jdkZip
if ((Get-FileHash $jdkZip -Algorithm SHA256).Hash.ToLowerInvariant() -ne $package.checksum) { throw 'JDK checksum mismatch' }
Expand-Archive -LiteralPath $jdkZip -DestinationPath $toolDir -Force
$mavenUrl = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip'
$mavenZip = Join-Path $toolDir 'maven.zip'
Invoke-WebRequest $mavenUrl -OutFile $mavenZip
$expected = (Invoke-RestMethod ($mavenUrl + '.sha512')).Trim()
if ((Get-FileHash $mavenZip -Algorithm SHA512).Hash.ToLowerInvariant() -ne $expected) { throw 'Maven checksum mismatch' }
Expand-Archive -LiteralPath $mavenZip -DestinationPath $toolDir -Force
@{jdk=$package; mavenUrl=$mavenUrl; mavenSha512=$expected} | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $toolDir 'downloads.json')
