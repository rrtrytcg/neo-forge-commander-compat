param(
 [ValidatePattern('^[a-zA-Z0-9_.-]+$')][string]$Label='neoforge-1.0.25',
 [ValidateSet('all','threats','utility')][string]$Mode='all',
 [ValidateSet('all','on','off')][string]$ProfileMode='all',
 [switch]$PrepareOnly, [switch]$VerifyOnly
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$out=Join-Path $PSScriptRoot "target/$Label"
$gate=Get-Content (Join-Path $out 'summary.json') -Raw|ConvertFrom-Json
foreach($file in $gate.files) {
 if((Get-FileHash -LiteralPath $file.path).Hash -ne $file.sha256) { throw "Compatibility input changed: $($file.path)" }
}
if($gate.results.Count -ne 16 -or (Get-FileHash $gate.candidate).Hash -ne $gate.candidateSha256) { throw 'Complete native compatibility gate required first' }
$probe=Join-Path $out 'commander-probes'
$original=Join-Path $repo 'arena/darksteel-mutation'
if($PrepareOnly -or !(Test-Path (Join-Path $probe 'Run-CardUseProbe.ps1'))) {
 $final=Join-Path $probe 'target/final'; $null=New-Item -ItemType Directory -Path $final -Force
 $candidate=Join-Path $final 'engine.jar'; Copy-Item -LiteralPath $gate.candidate -Destination $candidate
 foreach($name in @('DarksteelMutationProbe.java','Check-CardUseEvidence.ps1','decks')) {
  Copy-Item -LiteralPath (Join-Path $original $name) -Destination $probe -Recurse -Force
 }
 $runner=Get-Content (Join-Path $original 'Run-CardUseProbe.ps1') -Raw
 $safeRepo=$repo.Replace("'","''"); $safeWork=$gate.runtime.Replace("'","''"); $slashWork=$gate.runtime.Replace('\','/').Replace("'","''")
 $runner=$runner.Replace('$repoPath=(Resolve-Path (Join-Path $PSScriptRoot ''../..'')).Path',"`$repoPath='$safeRepo'")
 $runner=$runner.Replace('$runtimePath=(Join-Path $repoPath ''arena/runtime'').Replace(''\'',''/'')',"`$runtimePath='$slashWork'")
 $runner=$runner.Replace('(Join-Path $repoPath ''forge-gui/forge.profile.properties'')',"'$safeWork/forge.profile.properties'")
 $runner=$runner.Replace('Push-Location (Join-Path $repoPath ''forge-gui'')',"Push-Location '$safeWork'")
 Set-Content (Join-Path $probe 'Run-CardUseProbe.ps1') $runner
 $files=@($gate.files)+@(@{path=$candidate;sha256=(Get-FileHash $candidate).Hash},@{path=$PSCommandPath;sha256=(Get-FileHash $PSCommandPath).Hash})
 @{files=$files;scope='Installed 1.0.25 engine plus reconciled arena hooks; 16 native suites passed'}|
  ConvertTo-Json -Depth 8|Set-Content (Join-Path $final 'provenance.json')
}
if($PrepareOnly) { Write-Output "Prepared native-tested compatibility probes: $probe"; return }
if(!$VerifyOnly) {
 $modes=if($Mode -eq 'all') {@('threats','utility')} else {@($Mode)}
 $profiles=if($ProfileMode -eq 'all') {@('on','off')} else {@($ProfileMode)}
 foreach($m in $modes) { foreach($p in $profiles) { foreach($seed in @(96341,96342)) {
  & (Join-Path $probe 'Run-CardUseProbe.ps1') -Mode $m -ProfileMode $p -Seed $seed
 } } }
}
if($VerifyOnly -or ($Mode -eq 'all' -and $ProfileMode -eq 'all')) {
 & (Join-Path $probe 'Check-CardUseEvidence.ps1')
 Copy-Item -LiteralPath (Join-Path $probe 'card-use-evidence.json') -Destination (Join-Path $PSScriptRoot 'card-use-evidence.json')
}
