"""Independent checks of native casts, per-opponent damage and basic-land search results."""
import hashlib,json,pathlib,re
OUT=pathlib.Path(__file__).resolve().parent
checks=0
def read(p):return json.loads(pathlib.Path(p).read_text(encoding='utf-8-sig'))
def sha(p):
 with open(p,'rb') as f:return hashlib.file_digest(f,'sha256').hexdigest().upper()
def check(ok,label):
 global checks
 if not ok:raise AssertionError(label)
 checks+=1;print('PASS',label)
def verify(files):
 for f in files:check(sha(f['path'])==f['sha256'],'artifact '+f['path'])
gate=read(OUT/'target/final-1.0.27/provenance.json')
check(gate['engine']=='NeoForge 1.0.27' and len(gate['results'])==44,'full native gate')
verify(gate['files']);check(sha(gate['candidate'])==gate['candidateSha256'],'sealed candidate')
runs=[]
for scenario,seed in [('cast',115011),('cast',115012),('cycle',115021)]:
 for profile in ('on','off'):
  label=f'{scenario}-{profile}-{seed}';folder=OUT/'target/card-use'/label;s=read(folder/'summary.json');verify(s['files'])
  check(s['engine']==gate['engine'] and s['candidateSha256']==gate['candidateSha256'] and s['scenario']==scenario and s['profile']==profile and s['seed']==seed,label+' requested exposure')
  check(s['nativeExit']==0 and not s['unexpectedDiagnostics'],label+' no unexpected native diagnostic')
  raw=(folder/'probe.log').read_text(encoding='utf-8-sig');rows=[];ends=[];cutoff=False
  for line in raw.splitlines():
   if line.startswith(('TERRAIN_PROBE_CLOCK_STOP','TERRAIN_PROBE_DONE')):cutoff=True
   if line.startswith('TERRAIN_OBSERVER_END '):ends.append(json.loads(line.split(' ',1)[1]))
   if line.startswith('TERRAIN_WITNESS '):
    check(not cutoff,label+' event before cutoff');rows.append(json.loads(line.split(' ',1)[1]))
  check(ends==[dict(pendingActions=0)],label+' no pending native observed action')
  check(not re.search(r"Couldn't add to stack|AI failed to play",raw),label+' no native payment or stack failure')
  casts=[r for r in rows if r['kind']=='cast'];resolved=[r for r in rows if r['kind']=='resolved'];discards=[r for r in rows if r['kind']=='discard']
  check(len(casts)==len(resolved),label+' all observed actions paired')
  spells=cycles=damage=basics=shuffles=0
  for r in resolved:
   b=r['before'];check(not r['fizzled'] and sum(c['abilityId']==b['abilityId'] for c in casts)==1,label+' unique original resolution')
   if b['source']['name']!='Treacherous Terrain':continue
   check(profile=='on' and b['player']==b['turnPlayer'] and b['stackSize']==1 and b['targets']==0,label+' opt-in own-turn nontargeted empty-stack admission')
   check(b['life'][b['player']]>=5 and r['life'][b['player']]==b['life'][b['player']],label+' self life reserve preserved')
   check(r['source']['id']==b['source']['id'],label+' original source identity retained')
   if b['spell']:
    check(any(c['kind']=='spell-cleanup' and c['sourceId']==b['source']['id'] and c['event']>r['event'] for c in rows),label+' native sorcery cleanup moves source to graveyard')
    spells+=1;check(b['phase']=='MAIN2' and b['api']=='RepeatEach',label+' original second-main spell')
    colors=[m['color'] for m in b['mana']];check(len(colors)==8 and 'G' in colors and 'R' in colors,label+' native original 6RG payment')
    total=0;lethal=False
    for player,life in b['life'].items():
     if player==b['player']:continue
     expected=b['lands'][player];check(r['life'][player]==life-expected,label+' exact original damage for '+player)
     total+=expected;lethal|=expected>0 and life-expected<=0
    check(total>=8 or lethal,label+' approved aggregate value or lethal exception');damage+=total
    check(not r['found'] and r['shuffles']==0,label+' spell never searches for basics')
   else:
    check(r['source']['zone']=='Graveyard',label+' discarded source remains in native graveyard')
    cycles+=1;check(b['phase'] in ('MAIN1','MAIN2') and b['api']=='ChangeZone',label+' native own-main basic landcycling')
    check(b['lands'][b['player']]<6 and not any(c['land'] for c in b['hand']),label+' current land scarcity and no hand land')
    check(len(b['mana'])==2,label+' exact native cycling mana payment')
    d=[d for d in discards if d['sourceId']==b['source']['id'] and d['event']<b['event']]
    check(d and d[-1]['event']==b['event']-1,label+' exact source discarded natively before activation')
    check(len(r['found'])==1 and r['found'][0]['basic'] and r['found'][0]['land'],label+' native search finds one revealed basic land')
    before_ids={c['id'] for c in b['hand']};after_ids={c['id'] for c in r['hand']}
    check(before_ids<=after_ids and after_ids-before_ids=={r['found'][0]['id']},label+' only found basic enters hand')
    check(r['librarySize']==b['librarySize']-1 and r['drawn']==b['drawn'],label+' search is not a draw')
    check(r['shuffles']==1,label+' original native library shuffle witnessed');basics+=len(r['found']);shuffles+=r['shuffles']
  plays=sum(r['kind']=='land-play' for r in rows);check(plays>0,label+' ordinary native land plays remain active')
  if scenario=='cast':check(any(r['before']['source']['name']=='Centaur Courser' for r in resolved),label+' ordinary creature casting remains active')
  if profile=='off':check(spells==cycles==0,label+' unchanged default blocks both Terrain modes')
  else:check(spells>0 if scenario=='cast' else cycles>0,label+' requested mode witnessed')
  runs.append(dict(scenario=scenario,profile=profile,seed=seed,casts=spells,cycles=cycles,damage=damage,basics=basics,shuffles=shuffles,landPlays=plays,clockReached=s['clockReached'],summary=str(folder/'summary.json'),sha256=sha(folder/'summary.json')))
check(sum(r['casts'] for r in runs)>0 and sum(r['cycles'] for r in runs)>0,'both native modes witnessed')
(OUT/'card-use-evidence.json').write_text(json.dumps(dict(engine=gate['engine'],candidateSha256=gate['candidateSha256'],checks=checks,runs=runs,scope='Autonomous card use only; invalid repeated-card Commander exposure decks; no completion, winner or strength claim'),indent=2))
print('DONE',checks,'Treacherous Terrain card-use checks')
