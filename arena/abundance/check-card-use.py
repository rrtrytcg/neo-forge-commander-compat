"""Independent native-event checks; only own hand, public boards/counts and revealed movements."""
import hashlib,json,pathlib,re
OUT=pathlib.Path(__file__).resolve().parent
checks=0
def check(ok,label):
 global checks
 if not ok:raise AssertionError(label)
 checks+=1;print('PASS',label)
def read(p):return json.loads(pathlib.Path(p).read_text(encoding='utf-8-sig'))
def sha(p):
 with open(p,'rb') as f:return hashlib.file_digest(f,'sha256').hexdigest().upper()
def verify(files):
 for f in files:check(sha(f['path'])==f['sha256'],'artifact '+f['path'])
gate=read(OUT/'target/final-1.0.27/provenance.json')
check(gate['engine']=='NeoForge 1.0.27' and len(gate['results'])==43,'full native gate')
verify(gate['files']);check(sha(gate['candidate'])==gate['candidateSha256'],'tested candidate')
runs=[];all_modes=set();batches=0;bottom_total=0
for scenario,seed in [('demand',114011),('demand',114012),('flood',114021)]:
 for profile in ('on','off'):
  label=f'{scenario}-{profile}-{seed}';folder=OUT/'target/card-use'/label;s=read(folder/'summary.json');verify(s['files'])
  check(s['engine']==gate['engine'] and s['candidateSha256']==gate['candidateSha256'] and s['scenario']==scenario and s['profile']==profile and s['seed']==seed,label+' requested exposure')
  check(s['nativeExit']==0 and not s['unexpectedDiagnostics'],label+' no native diagnostic failure')
  raw=(folder/'probe.log').read_text(encoding='utf-8-sig');rows=[];end=[];cutoff=False
  for line in raw.splitlines():
   if line.startswith(('ABUNDANCE_PROBE_CLOCK_STOP','ABUNDANCE_PROBE_DONE')):cutoff=True
   if line.startswith('ABUNDANCE_OBSERVER_END '):end.append(json.loads(line.split(' ',1)[1]))
   if line.startswith('ABUNDANCE_WITNESS '):
    check(not cutoff,label+' native event before cutoff');rows.append(json.loads(line.split(' ',1)[1]))
  check(len(end)==1 and end[0]==dict(pendingCasts=0,pendingChoice=False),label+' no pending observed cast or replacement')
  check(not re.search(r"Couldn't add to stack|AI failed to play",raw),label+' no native payment or stack failure')
  casts=[r for r in rows if r['kind']=='cast'];resolved=[r for r in rows if r['kind']=='resolved'];choices=[r for r in rows if r['kind']=='choice'];settled=[r for r in rows if r['kind']=='settled']
  check(len(casts)==len(resolved) and len(choices)==len(settled),label+' every observed action and choice paired')
  count=land=nonland=found=bottoms=multi=0
  for r in resolved:
   b=r['before'];check(not r['fizzled'] and sum(c['abilityId']==b['abilityId'] for c in casts)==1,label+' unique resolved native cast')
   if b['source']['name']!='Abundance':continue
   count+=1;check(profile=='on' and b['turnPlayer']==b['player'] and b['phase']=='MAIN2' and b['stackSize']==1,label+' opted-in second-main cast after empty stack')
   check(len(b['mana'])==4 and all(m['color']=='G' and m['source']=='Forest' for m in b['mana']),label+' original four mana paid natively')
   check(not b['abundances'] and len(r['abundances'])==1 and r['abundances'][0]['id']==b['source']['id'],label+' original permanent resolves without duplicate')
  for r in settled:
   b=r['before'];check(sum(c['event']==b['event'] for c in choices)==1 and not b['random'],label+' unique deterministic native replacement choice')
   check(profile=='on' and len(b['abundances'])==1 and r['abundances']==b['abundances'],label+' original source present through native movement')
   want_land=b['lands']<6 and not any(c['land'] for c in b['hand']);mode='Land' if want_land else 'Nonland'
   check(b['mode']==mode,label+' correct choice from current battlefield and hand')
   if want_land:land+=1
   else:nonland+=1
   all_modes.add(mode)
   moves=r['movements'];moved=[c for c in moves if c['to']=='Hand'];rest=[c for c in moves if c['to']=='Library']
   check(len(moved)<=1 and len(moves)==len(moved)+len(rest),label+' one found card at most with only native destinations')
   check(all(c['land']==want_land for c in moved) and all(c['land']!=want_land for c in rest),label+' found and skipped revealed cards match original type rule')
   check(all(c['atBottom'] for c in rest),label+' every revealed skipped card actually moved to bottom')
   before_ids={c['id'] for c in b['hand']};after_ids={c['id'] for c in r['hand']};found_ids={c['id'] for c in moved}
   check(before_ids<=after_ids and after_ids-before_ids==found_ids,label+' exact found card enters hand without extra actions')
   check(r['librarySize']==b['librarySize']-len(moved) and len(moves)<=b['librarySize'],label+' native library count and reveal bound')
   check(r['drawn']==b['drawn'],label+' putting into hand never increments actual draw count')
   if any(s['source']=='Harmonize' for s in b['stack']):multi+=1
   found+=len(moved);bottoms+=len(rest)
  plays=sum(r['kind']=='land-play' for r in rows)
  check(plays>0,label+' ordinary native land plays remain active')
  if scenario=='flood':check(any(r['before']['source']['name']=='Centaur Courser' for r in resolved),label+' ordinary native creature casts remain active')
  if profile=='off':check(count==land==nonland==0,label+' default admission and replacement behavior unchanged')
  else:
   check(count==1 and land+nonland>0,label+' one original cast with native replacements and duplicate holds')
   if scenario=='flood':check(nonland>0,label+' Nonland choice witnessed')
  batches+=multi;bottom_total+=bottoms
  runs.append(dict(scenario=scenario,profile=profile,seed=seed,casts=count,landChoices=land,nonlandChoices=nonland,found=found,bottomed=bottoms,harmonizeChoices=multi,landPlays=plays,clockReached=s['clockReached'],summary=str(folder/'summary.json'),sha256=sha(folder/'summary.json')))
check(any(r['scenario']=='demand' and r['profile']=='on' and r['landChoices']>0 for r in runs),'Land demand witnessed in at least one paired seed')
check(all_modes=={'Land','Nonland'} and bottom_total>0,'both native modes plus actual bottoming witnessed')
check(batches>0,'native replacement choices during multi-card Harmonize witnessed')
(OUT/'card-use-evidence.json').write_text(json.dumps(dict(engine=gate['engine'],candidateSha256=gate['candidateSha256'],checks=checks,runs=runs,scope='Native autonomous card use only; intentionally invalid repeated-card Commander exposure decks; no completion, winner or strength claim'),indent=2))
print('DONE',checks,'Abundance card-use checks')
