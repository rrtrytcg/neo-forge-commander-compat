param([string]$InstallPath)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if(!$InstallPath) {$InstallPath=Join-Path $repo '../NeoForge'}
$InstallPath=(Resolve-Path $InstallPath).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Read-ZipText($entry) {
 $reader=[IO.StreamReader]::new($entry.Open())
 try {return $reader.ReadToEnd()} finally {$reader.Dispose()}
}
function Class-Map($path) {
 $map=@{}; $zip=[IO.Compression.ZipFile]::OpenRead($path)
 try {foreach($entry in $zip.Entries) {
  if(!$entry.FullName.EndsWith('.class')) {continue}
  $stream=$entry.Open()
  try {$map[$entry.FullName]=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream))} finally {$stream.Dispose()}
 }} finally {$zip.Dispose()}
 return ,$map
}
$neo=Join-Path $InstallPath 'app/neo.jar'
$zip=[IO.Compression.ZipFile]::OpenRead($neo)
try {$build=Read-ZipText $zip.GetEntry('forge/neo/build.properties')} finally {$zip.Dispose()}
$frozen=Join-Path $repo 'arena/.tools/frozen/forge-classic.jar'; $base=Class-Map $frozen
$modules=@()
foreach($module in @('core','game','ai','gui')) {
 $path=Join-Path $InstallPath "app/forge-$module-2.0.16-SNAPSHOT.jar"; $installed=Class-Map $path
 $modules+=@{module=$module;path=$path;sha256=(Get-FileHash $path).Hash;installedClasses=$installed.Count;
  changed=@($installed.Keys|Where-Object {$base.ContainsKey($_) -and $installed[$_] -ne $base[$_]}|Sort-Object);
  added=@($installed.Keys|Where-Object {!$base.ContainsKey($_)}|Sort-Object)}
}
$registry=@(Get-Content (Join-Path $repo 'arena/coverage/card-status.tsv')|Where-Object {$_ -and !$_.StartsWith('#')}|ForEach-Object {$_.Split([char]9)[0]})
$cards=Join-Path $repo 'forge-gui/res/cardsfolder'; $zipPath=Join-Path $InstallPath 'res/cardsfolder/cardsfolder.zip'
$same=0; $changed=@(); $added=@(); $registered=@(); $hintChanges=@()
$zip=[IO.Compression.ZipFile]::OpenRead($zipPath)
try {foreach($entry in $zip.Entries) {
 if(!$entry.FullName.EndsWith('.txt')) {continue}
 $rel=$entry.FullName -replace '^\./',''; $path=Join-Path $cards $rel
 $text=(Read-ZipText $entry).Replace("`r`n","`n").TrimEnd()
 $name=[regex]::Match($text,'(?m)^Name:(.+)$').Groups[1].Value.Trim()
 if(![IO.File]::Exists($path)) {$added+=@{path=$rel;name=$name};continue}
 $old=[IO.File]::ReadAllText($path).Replace("`r`n","`n").TrimEnd()
 if($old -ceq $text) {$same++;continue}
 $row=@{path=$rel;name=$name}; $changed+=$row
 if($name -in $registry) {$registered+=$row}
 $oldHints=([regex]::Matches($old,'(?m)^AI:RemoveDeck:.*$|AILogic\$[A-Za-z0-9]+')|ForEach-Object Value) -join '|'
 $newHints=([regex]::Matches($text,'(?m)^AI:RemoveDeck:.*$|AILogic\$[A-Za-z0-9]+')|ForEach-Object Value) -join '|'
 if($oldHints -cne $newHints) {$hintChanges+=@{name=$name;before=$oldHints;after=$newHints}}
}} finally {$zip.Dispose()}
$deckDir=Join-Path $InstallPath 'res/adventure/Realm of Legends/decks/starter/commanderprecon'
$decks=@(Get-ChildItem -LiteralPath $deckDir -Filter '*.dck'|Sort-Object Name|ForEach-Object {@{name=$_.BaseName;sha256=(Get-FileHash $_.FullName).Hash}})
$result=@{date=(Get-Date -Format yyyy-MM-dd);revision=(& git -C $repo rev-parse HEAD);installedBuild=$build;
 installedNeo=@{path=$neo;sha256=(Get-FileHash $neo).Hash};modules=$modules;
 comparisonBase=@{path=$frozen;sha256=(Get-FileHash $frozen).Hash};
 cards=@{zipPath=$zipPath;zipSha256=(Get-FileHash $zipPath).Hash;same=$same;changed=$changed;added=$added;registeredChanged=$registered;hintChanges=$hintChanges};
 installedCommanderDecks=$decks;scope='Read-only installed snapshot versus frozen Classic classes and current source scripts; bytecode differences alone are not behavioral claims'}
$result|ConvertTo-Json -Depth 9|Set-Content (Join-Path $PSScriptRoot 'installed-audit.json')
Write-Output $build.Trim()
Write-Output "Scripts: $same same, $($changed.Count) changed, $($added.Count) additions, $($registered.Count) registered changes, $($hintChanges.Count) AI-hint changes"
Write-Output "Installed Commander inventory: $($decks.Count) decks"
