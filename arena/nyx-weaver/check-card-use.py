"""Independent native-event checks; own hand/public graveyard, battlefield, stack and counts only."""
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
check(gate['engine']=='NeoForge 1.0.27' and len(gate['results'])==46 and sum(r['checks'] for r in gate['results'])==5122,'complete native gate including utility lands')
verify(gate['files']);check(sha(gate['candidate'])==gate['candidateSha256'],'tested candidate')
runs=[]
for scenario,seed in [('value',118011),('value',118012),('rescue',118021),('library',118031)]:
 for profile in ('on','off'):
  label=f'{scenario}-{profile}-{seed}';folder=OUT/'target/card-use'/label;s=read(folder/'summary.json');verify(s['files'])
  check(s['engine']==gate['engine'] and s['candidateSha256']==gate['candidateSha256'] and s['scenario']==scenario and s['profile']==profile and s['seed']==seed,label+' requested exposure')
  check(s['nativeExit']==0 and not s['unexpectedDiagnostics'],label+' no unexpected native diagnostics')
  raw=(folder/'probe.log').read_text(encoding='utf-8-sig');rows=[];ends=[];cutoff=False
  for line in raw.splitlines():
   if line.startswith(('NYX_PROBE_CLOCK_STOP','NYX_PROBE_DONE')):cutoff=True
   if line.startswith('NYX_OBSERVER_END '):ends.append(json.loads(line.split(' ',1)[1]))
   if line.startswith('NYX_WITNESS '):
    check(not cutoff,label+' event before cutoff');rows.append(json.loads(line.split(' ',1)[1]))
  check(ends==[dict(pendingActions=0)],label+' no pending observed action')
  check(not re.search(r"Couldn't add to stack|AI failed to play",raw),label+' no payment or stack failure')
  starts=[r for r in rows if r['kind']=='cast'];resolved=[r for r in rows if r['kind']=='resolved']
  check(len(starts)==len(resolved),label+' all observed actions resolve')
  casts=mills=milled=activations=value=doomed=low=0;returns=[]
  for r in resolved:
   b=r['before'];check(sum(c['abilityId']==b['abilityId'] for c in starts)==1 and not r['fizzled'],label+' unique successful native resolution')
   if b['source']['name']!='Nyx Weaver':continue
   check(profile=='on',label+' native Nyx use is opted in')
   check(r['source']['id']==b['source']['id'],label+' same original source')
   if b['spell']:
    casts+=1;check(b['phase']=='MAIN2' and b['player']==b['turnPlayer'] and b['stackSize']==1 and not b['targets'],label+' original empty-stack second-main creature cast')
    colors=[m['color'] for m in b['mana']];check(len(colors)==3 and 'B' in colors and 'G' in colors,label+' original native 1BG spell payment')
    check(b['librarySize']>=8 and not b['weavers'],label+' cast reserve and duplicate hold')
    check(r['source']['zone']=='Battlefield' and r['source']['power']==2 and r['source']['toughness']==3,label+' original creature resolves as 2/3')
   elif b['api']=='Mill':
    mills+=1;count=min(2,b['librarySize']);moves=r['movements']
    check(b['trigger'] and b['phase']=='UPKEEP' and b['turnPlayer']==b['player'],label+' original mandatory own-upkeep trigger')
    check(len(moves)==count and all(m['from']=='Library' and m['to']=='Graveyard' for m in moves),label+' original native mill movements')
    check(r['librarySize']==b['librarySize']-count and r['drawn']==b['drawn'],label+' mill count and no draw')
    check({m['id'] for m in moves}<={c['id'] for c in r['graveyard']},label+' milled revealed cards actually in graveyard');milled+=count
   else:
    activations+=1;check(b['api']=='ChangeZone' and not b['trigger'] and len(b['targets'])==1,label+' original targeted recovery activation')
    target=b['targets'][0];check(target['controller']==b['player'] and target['zone']=='Graveyard',label+' native target belongs to own public graveyard')
    colors=[m['color'] for m in b['mana']];check(len(colors)==3 and 'B' in colors and 'G' in colors,label+' native 1BG recovery payment')
    exiles=[e for e in rows if e['kind']=='source-exile' and e['sourceId']==b['source']['id'] and e['event']<b['event']]
    check(exiles and exiles[-1]['event']==b['event']-1 and b['source']['zone']=='Exile' and r['source']['zone']=='Exile',label+' exact source exiled natively before activation and stays exiled')
    threats=[t for t in b['stack'] if t['abilityId']!=b['abilityId']]
    if any(t['api']=='Destroy' and b['source']['id'] in t['targetIds'] for t in threats):
     doomed+=1;check(all(t['source']=='Cut Down' and t['api']=='Destroy' and b['source']['id'] in t['targetIds'] for t in threats),label+' understood public lethal Cut Down response')
     check(any(e['kind']=='threat-resolved' and e['source']['name']=='Cut Down' and e['fizzled'] and e['event']>r['event'] and any(t['abilityId']==e['abilityId'] for t in threats) for e in rows),label+' native removal subsequently fizzles on exiled Weaver')
    elif b['librarySize']<=4:
     low+=1;check(not threats and (b['phase']=='MAIN2' and b['turnPlayer']==b['player'] or b['phase']=='END_OF_TURN' and b['nextPlayer']==b['player'] and b['turnPlayer']!=b['player']),label+' low-library cash-out before future upkeep')
    else:
     value+=1;check(not threats and b['phase']=='MAIN2' and b['turnPlayer']==b['player'] and not target['land'] and target['mv']>=4,label+' ordinary high-value own-second-main recovery')
    check(r['movements']==[dict(id=target['id'],name=target['name'],**{'from':'Graveyard','to':'Hand'})],label+' exact native target movement')
    before_ids={c['id'] for c in b['hand']};after_ids={c['id'] for c in r['hand']}
    check(before_ids<=after_ids and after_ids-before_ids=={target['id']},label+' only target enters hand')
    check(r['librarySize']==b['librarySize'] and r['drawn']==b['drawn'],label+' recovery does not mill or draw')
    check(r['life'][b['player']]==b['life'][b['player']] and r['life'][b['player']]>=5,label+' friendly life reserve preserved')
    returns.append((target['id'],r['event'],r['turn']))
  later=sum(any(c['event']>event and c['source']['id']==card and c['spell'] for c in starts) for card,event,turn in returns)
  same_turn=sum(any(c['event']>event and c['source']['id']==card and c['spell'] and c['turn']==turn for c in starts) for card,event,turn in returns)
  plays=sum(r['kind']=='land-play' for r in rows);check(plays>0,label+' ordinary native land plays remain active')
  if scenario=='value':check(any(r['before']['source']['name'] in ('Rumbling Baloth','Craw Wurm') for r in resolved),label+' ordinary native creature casting remains active')
  if profile=='off':check(casts==mills==activations==0,label+' unchanged default excludes all Nyx use')
  else:
   check(casts>0,label+' autonomous native casting witnessed')
   if scenario=='value':
    check(mills>0,label+' original mill witnessed in value pair')
    if seed==118011:
     check(value==activations==0 and len(re.findall(r'^Land: NyxProbe-0 played Forest ',raw,re.M))==2,label+' only two green sources cannot jointly fund activation plus GG target')
   else:check(doomed>0 if scenario=='rescue' else low>0,label+' requested recovery branch witnessed')
  runs.append(dict(scenario=scenario,profile=profile,seed=seed,casts=casts,mills=mills,milled=milled,activations=activations,value=value,doomed=doomed,lowLibrary=low,laterCasts=later,sameTurnCasts=same_turn,landPlays=plays,clockReached=s['clockReached'],summary=str(folder/'summary.json'),sha256=sha(folder/'summary.json')))
check(any(r['scenario']=='value' and r['profile']=='on' and r['value']>0 for r in runs),'ordinary funded recovery witnessed across value seeds')
check(sum(r['mills'] for r in runs)>0 and sum(r['sameTurnCasts'] for r in runs)>0,'original upkeep mill and later same-turn native spending witnessed')
(OUT/'card-use-evidence.json').write_text(json.dumps(dict(engine=gate['engine'],candidateSha256=gate['candidateSha256'],checks=checks,runs=runs,scope='Native autonomous card use only; invalid custom Commander exposure decks; no completed-game/winner/strength claim'),indent=2))
print('DONE',checks,'Nyx Weaver card-use checks')
