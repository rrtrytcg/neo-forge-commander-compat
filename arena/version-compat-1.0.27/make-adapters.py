"""Keep historical runners and witnesses intact; redirect a fresh release lane."""
import pathlib
out=pathlib.Path(__file__).resolve().parent
root=out.parents[1]
old=root/'arena/mirage-mirror'
text=(old/'Run-CardUseProbe.ps1').read_text()
text=text.replace('target/final-1.0.25/provenance.json','target/provenance.json').replace('NeoForge 1.0.25','NeoForge 1.0.27')
text=text.replace("Join-Path $PSScriptRoot 'MirrorProbe.java'", "Join-Path $repo 'arena/mirage-mirror/MirrorProbe.java'")
text=text.replace('Join-Path $PSScriptRoot "decks/', 'Join-Path $repo "arena/mirage-mirror/decks/')
(out/'Run-CardUseProbe.ps1').write_text(text)
exposure=text.replace('[int]$Seed=111011','[int]$Seed=111031').replace("[string]$Scenario='offense'","[string]$Scenario='defense'")
exposure=exposure.replace('target/card-use-final/$Scenario-$ProfileMode-$Seed','target/card-use-final/defense-exposure-$ProfileMode-$Seed')
exposure=exposure.replace('Join-Path $repo "arena/mirage-mirror/decks/Opponents-$Scenario.dck"', "Join-Path $PSScriptRoot 'decks/Opponents-defense-exposure.dck'")
(out/'Run-DefenseExposure.ps1').write_text(exposure)
text=(old/'Check-CardUseEvidence.ps1').read_text()
text=text.replace('target/final-1.0.25/provenance.json','target/provenance.json').replace('NeoForge 1.0.25','NeoForge 1.0.27')
start=text.index('$initial=')
end=text.index('$runs=@()',start)
text=text[:start]+text[end:]
text=text.replace(';initialDiagnostics=$initial','')
outer="foreach($scenario in @('offense','defense')){foreach($profile in @('on','off')){foreach($seed in $(if($scenario -eq 'offense'){@(111011,111012)}else{@(111021,111022)})){"
specs='''$specs=@()
foreach($scenario in @('offense','defense')){foreach($profile in @('on','off')){foreach($seed in $(if($scenario -eq 'offense'){@(111011,111012)}else{@(111021,111022)})){
    $specs+=@{scenario=$scenario;profile=$profile;seed=$seed;label="$scenario-$profile-$seed"}
}}}
foreach($profile in @('on','off')){$specs+=@{scenario='defense';profile=$profile;seed=111031;label="defense-exposure-$profile-111031"}}
foreach($spec in $specs){
    $scenario=$spec.scenario;$profile=$spec.profile;$seed=$spec.seed'''
assert outer in text
text=text.replace(outer,specs).replace('$label="$scenario-$profile-$seed";$dir=','$label=$spec.label;$dir=')
text=text.replace('$runs+=@{scenario=','$runs+=@{label=$label;scenario=').replace('}}}\nCheck ($offenseAttacks','}\nCheck ($offenseAttacks')
(out/'Check-CardUseEvidence.ps1').write_text(text)
text=(root/'arena/version-compat/Check-Timeouts.ps1').read_text()
text=text.replace("param([ValidatePattern('^[a-zA-Z0-9_.-]+$')][string]$Label='neoforge-1.0.25')",'param()')
text=text.replace('Join-Path $PSScriptRoot "target/$Label"',"Join-Path $PSScriptRoot 'target'")
text=text.replace('64569999b8b99dd7176ab9d3e1e6008f3ab68590','77b6f5ae95f7f11d5a718c52a977470131db0fe1')
text=text.replace("Join-Path $PSScriptRoot 'CompatibilityTimeoutTests.java'", "Join-Path $repo 'arena/version-compat/CompatibilityTimeoutTests.java'")
(out/'Check-Timeouts.ps1').write_text(text)
print('Generated isolated adapters using unchanged Mirage Mirror observer/decks and timeout fixtures')
