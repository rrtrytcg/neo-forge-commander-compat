$ErrorActionPreference='Stop'
$checks=0
function Check($ok,$label){if(!$ok){throw $label};$script:checks++;Write-Output "PASS $label"}
$gate=Get-Content (Join-Path $PSScriptRoot 'target/final-1.0.25/provenance.json') -Raw|ConvertFrom-Json
Check ($gate.engine -eq 'NeoForge 1.0.25' -and $gate.results.Count -eq 37 -and ($gate.results|Measure-Object checks -Sum).Sum -eq 3829) 'complete native gate'
Check ((Get-FileHash $gate.candidate).Hash -eq $gate.candidateSha256) 'native candidate hash'
foreach($f in $gate.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "native input $($f.path)"}
$runs=@();$allHex=0;$allShroud=0;$allDestroy=0;$allExile=0;$allReuse=0;$allActivations=0;$allRemoved=0
foreach($profile in @('on','off')){foreach($seed in @(110011,110012)){
    $label="lighthouse-$profile-$seed";$dir=Join-Path $PSScriptRoot "target/card-use/$label";$path=Join-Path $dir 'summary.json';$s=Get-Content $path -Raw|ConvertFrom-Json
    Check ($s.engine -eq $gate.engine -and $s.candidateSha256 -eq $gate.candidateSha256) "$label tested engine"
    Check ($s.scenario -eq 'lighthouse' -and $s.profile -eq $profile -and $s.seed -eq $seed) "$label requested run"
    Check ($s.nativeExit -eq 0 -and !$s.unexpectedDiagnostics.Count) "$label native exit and diagnostics"
    foreach($f in $s.files){Check ((Get-FileHash -LiteralPath $f.path).Hash -eq $f.sha256) "$label artifact $($f.path)"}
    Check (($s.files|Where-Object {$_.path.EndsWith('candidate.jar')}).sha256 -eq $gate.candidateSha256) "$label actual candidate"
    $rows=@();$cutoff=$false
    foreach($line in Get-Content (Join-Path $dir 'probe.log')){
        if($line -eq 'LIGHTHOUSE_PROBE_CLOCK_STOP' -or $line.StartsWith('LIGHTHOUSE_PROBE_DONE')){$cutoff=$true}
        if($line.StartsWith('LIGHTHOUSE_WITNESS ')){Check (!$cutoff) "$label no late observer event";$rows+=($line.Substring(19)|ConvertFrom-Json)}
    }
    Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch "Couldn't add to stack" -Quiet)) "$label no failed native stack insertion"
    Check (!(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch 'AI failed to play' -Quiet)) "$label no native payment failure"
    $casts=@($rows|Where-Object kind -eq 'cast');$resolved=@($rows|Where-Object kind -eq 'resolved')
    Check ($casts.Count -eq $resolved.Count) "$label no pending observed actions"
    $acts=@();$removed=0;$hex=0;$shroud=0;$destroy=0;$exile=0;$manaUses=0;$reuses=0;$lastUse=@{};$activationReuse=0;$lastActivation=@{}
    foreach($r in $resolved){
        $b=$r.before
        Check ($null -ne $b -and @($casts|Where-Object abilityId -eq $b.abilityId).Count -eq 1 -and $r.source.id -eq $b.source.id -and $r.abilityId -eq $b.abilityId) "$label unique native action pairing"
        Check (!$r.fizzled -and $b.player -eq 'LighthouseProbe-0') "$label original action resolves without fizzle"
        foreach($m in @($b.mana|Where-Object source -eq 'Arcane Lighthouse')){
            Check ($m.color -eq 'C') "$label original colorless production"
            if($lastUse.ContainsKey($m.sourceId) -and $b.turn -gt $lastUse[$m.sourceId]){
                Check (@($rows|Where-Object {$_.kind -eq 'untap' -and $_.phase -eq 'UNTAP' -and $_.source.id -eq $m.sourceId -and $_.turn -gt $lastUse[$m.sourceId] -and $_.event -lt $b.event}).Count -gt 0) "$label native untap precedes mana reuse";$reuses++
            };$lastUse[$m.sourceId]=$b.turn;$manaUses++
        }
        if(!$b.spell -and $b.api -eq 'AnimateAll'){
            Check ($profile -eq 'on' -and $b.source.name -eq 'Arcane Lighthouse' -and $b.source.tapped -and $b.phase -in @('MAIN1','MAIN2') -and $b.turnPlayer -eq $b.player -and $b.stackSize -eq 1) "$label native own-main activation and source tap"
            Check ($b.mana.Count -eq 1 -and @($b.mana|Where-Object sourceId -eq $b.source.id).Count -eq 0) "$label one separately funded generic activation cost"
            $protected=@($b.board|Where-Object {$_.controller -ne $b.player -and ($_.hexproof -or $_.shroud)})
            Check ($protected.Count -gt 0 -and @($protected|Where-Object value -ge 200).Count -gt 0) "$label actual worthwhile protected threat"
            foreach($c in $protected){$after=@($r.board|Where-Object id -eq $c.id);Check ($after.Count -eq 1 -and !$after[0].hexproof -and !$after[0].shroud) "$label original effect actually removes protection"}
            if($lastActivation.ContainsKey($b.source.id)){
                Check (@($rows|Where-Object {$_.kind -eq 'untap' -and $_.phase -eq 'UNTAP' -and $_.source.id -eq $b.source.id -and $_.event -gt $lastActivation[$b.source.id] -and $_.event -lt $b.event}).Count -gt 0) "$label native untap precedes activation reuse";$activationReuse++
            };$lastActivation[$b.source.id]=$b.event;$acts+=$r
        }
        if($b.spell -and $b.source.name -in @('Murder','Despark')){
            Check ($b.targets.Count -eq 1 -and $r.targets.Count -eq 1 -and $b.targets[0].id -eq $r.targets[0].id) "$label single original removal target retained"
            $id=$b.targets[0].id
            $prior=@($acts|Where-Object {$_.turn -eq $b.turn -and $_.event -lt $b.event -and @($_.before.board|Where-Object {$_.id -eq $id -and ($_.hexproof -or $_.shroud)}).Count -eq 1})
            Check ($profile -eq 'on' -and $prior.Count -gt 0 -and !$b.targets[0].hexproof -and !$b.targets[0].shroud) "$label normal removal follows actual protection removal"
            $a=$prior[-1];$original=@($a.before.board|Where-Object id -eq $id)[0]
            Check (@($b.mana|Where-Object sourceId -eq $a.source.id).Count -eq 0) "$label activated Lighthouse not spent again on follow-up"
            if($b.source.name -eq 'Murder'){
                Check ($b.mana.Count -eq 3 -and @($b.mana|Where-Object color -eq 'B').Count -ge 2 -and $r.targets[0].zone -eq 'Graveyard') "$label actual double-black destruction";$destroy++
            }else{
                Check ($b.mana.Count -eq 2 -and @($b.mana|Where-Object color -eq 'B').Count -eq 1 -and @($b.mana|Where-Object color -eq 'W').Count -eq 1 -and $r.targets[0].zone -eq 'Exile') "$label actual white-black exile";$exile++
            }
            if($original.hexproof){$hex++};if($original.shroud){$shroud++};$removed++
        }
    }
    $lighthouseLands=@(Select-String -LiteralPath (Join-Path $dir 'probe.log') -SimpleMatch 'Land: LighthouseProbe-0 played Arcane Lighthouse').Count
    if($profile -eq 'on'){
        if($acts.Count -gt 0){Check ($removed -gt 0) "$label autonomous activation and follow-up witness"}
        else{Check ($lighthouseLands -eq 0) "$label retained non-witness has no Lighthouse land-play exposure"}
        $allActivations+=$acts.Count;$allRemoved+=$removed
    }
    else{Check ($acts.Count -eq 0 -and $removed -eq 0) "$label unchanged default special-ability behavior"}
    Check (@($resolved|Where-Object {$_.before.source.name -eq 'Walking Corpse'}).Count -gt 0) "$label ordinary native creature casting remains active"
    $allHex+=$hex;$allShroud+=$shroud;$allDestroy+=$destroy;$allExile+=$exile;$allReuse+=$reuses+$activationReuse
    $runs+=@{profile=$profile;seed=$seed;lighthouseLandPlays=$lighthouseLands;activations=$acts.Count;removals=$removed;hexproof=$hex;shroud=$shroud;destructions=$destroy;exiles=$exile;manaUses=$manaUses;manaReuses=$reuses;activationReuses=$activationReuse;clockReached=$s.clockReached;summary=$path;sha256=(Get-FileHash $path).Hash}
}}
Check ($allActivations -gt 0 -and $allRemoved -gt 0) 'autonomous native activation and follow-up witnessed'
Check ($allHex -gt 0 -and $allShroud -gt 0 -and $allDestroy -gt 0 -and $allExile -gt 0) 'both protections and both native removal mechanics witnessed'
Check ($allReuse -gt 0) 'native untapping and reuse witnessed'
@{engine=$gate.engine;candidateSha256=$gate.candidateSha256;checks=$checks;runs=$runs;scope='Autonomous native Commander card use; invalid repeated-card custom decks; no completion, winner or strength claim'}|ConvertTo-Json -Depth 10|Set-Content (Join-Path $PSScriptRoot 'card-use-evidence.json')
Write-Output "DONE $checks Lighthouse card-use checks"
