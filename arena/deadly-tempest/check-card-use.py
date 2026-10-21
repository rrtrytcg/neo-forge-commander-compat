"""Independent native-event checks. No completion, winner or strength inference."""
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
check(gate['engine']=='NeoForge 1.0.27' and len(gate['results'])==41,'full native gate')
verify(gate['files']);check(sha(gate['candidate'])==gate['candidateSha256'],'tested candidate')
runs=[];protected_seen=0;shield_seen=0;indestructible_seen=0
for scenario,seed in [('ordinary',113011),('ordinary',113012),('protected',113021)]:
    for profile in ('on','off'):
        label=f'{scenario}-{profile}-{seed}';folder=OUT/'target/card-use'/label
        s=read(folder/'summary.json');verify(s['files'])
        check(s['engine']==gate['engine'] and s['candidateSha256']==gate['candidateSha256'] and s['scenario']==scenario and s['profile']==profile and s['seed']==seed,label+' requested exposure')
        check(s['nativeExit']==0 and not s['unexpectedDiagnostics'],label+' no native diagnostic failure')
        text=(folder/'probe.log').read_text(encoding='utf-8-sig');rows=[];cutoff=False
        for line in text.splitlines():
            if line.startswith(('TEMPEST_PROBE_CLOCK_STOP','TEMPEST_PROBE_DONE')):cutoff=True
            if line.startswith('TEMPEST_WITNESS '):
                check(not cutoff,label+' event before cutoff');rows.append(json.loads(line[len('TEMPEST_WITNESS '):]))
        check(not re.search(r"Couldn't add to stack|AI failed to play",text),label+' no native payment or stack failure')
        casts=[r for r in rows if r['kind']=='cast'];resolved=[r for r in rows if r['kind']=='resolved']
        check(len(casts)==len(resolved),label+' no pending observed actions')
        count=enemy_deaths=friendly_deaths=life_lost=protected=shields=indestructibles=0
        for r in resolved:
            b=r['before'];check(not r['fizzled'],label+' matched action does not fizzle')
            check(sum(c['abilityId']==b['abilityId'] for c in casts)==1 and r['abilityId']==b['abilityId'],label+' unique native action pairing')
            if b['source']['name']!='Deadly Tempest':continue
            count+=1
            check(profile=='on' and b['spell'] and b['player']=='TempestProbe-0' and b['turnPlayer']==b['player'] and b['phase']=='MAIN2',label+' opted-in second-main cast')
            check(b['stackSize']==1 and len(b['mana'])==6 and all(m['color']=='B' and m['source']=='Swamp' for m in b['mana']),label+' original six mana paid natively')
            check(not b['targets'] and [n['api'] for n in b['chain']]==['DestroyAll','RepeatEach','Cleanup'],label+' original untargeted destruction repeat cleanup chain')
            after={c['id']:c for c in r['board']};grave={c['id']:c for c in r['graveyards']};deaths={p:0 for p in b['life']};enemy=friendly=0
            for c in b['board']:
                if not c['creature']:continue
                if c['id'] in after:
                    if c['indestructible'] or c['shield']:
                        check(c['controller']==after[c['id']]['controller'],label+' protected survivor stays controlled');protected+=1
                        if c['indestructible']:
                            check(after[c['id']]['indestructible'],label+' native indestructible survivor retained');indestructibles+=1
                        elif c['shield']:
                            check(after[c['id']]['shield']==c['shield']-1,label+' native shield consumed instead of destruction');shields+=1
                    continue
                check(c['id'] in grave and grave[c['id']]['zone']=='Graveyard',label+' actual original creature destruction')
                check(not c['indestructible'] and not c['shield'],label+' protected creatures never counted as destruction')
                deaths[c['controller']]+=1
                if c['controller']==b['player']:friendly+=1
                else:enemy+=1
            check(enemy>=friendly+2,label+' two-creature net advantage at cast')
            for p,life in b['life'].items():
                check(p in r['life'] and r['life'][p]==life-deaths[p],label+' exact LKI controller life loss '+p)
            check(r['life'][b['player']]>=5,label+' actor five-life reserve survives original resolution')
            enemy_deaths+=enemy;friendly_deaths+=friendly;life_lost+=sum(deaths.values())
        check(any(r['before']['source']['name']=='Walking Corpse' for r in resolved),label+' ordinary native actor casting remains active')
        if profile=='off':check(count==0,label+' default block retained')
        else:check(count>0,label+' paid resolved original casts witnessed')
        protected_seen+=protected;shield_seen+=shields;indestructible_seen+=indestructibles
        runs.append(dict(scenario=scenario,profile=profile,seed=seed,casts=count,enemyDestroyed=enemy_deaths,friendlyDestroyed=friendly_deaths,lifeLost=life_lost,protectedSurvivors=protected,shieldSurvivors=shields,indestructibleSurvivors=indestructibles,clockReached=s['clockReached'],summary=str(folder/'summary.json'),sha256=sha(folder/'summary.json')))
check(protected_seen>0 and shield_seen>0 and indestructible_seen>0,'shield and indestructible survivors witnessed in autonomous casts')
(OUT/'card-use-evidence.json').write_text(json.dumps(dict(engine=gate['engine'],candidateSha256=gate['candidateSha256'],checks=checks,runs=runs,scope='Native autonomous card use only; intentionally invalid repeated-card Commander decks; no completion, winner or strength claim'),indent=2))
print('DONE',checks,'Deadly Tempest card-use checks')
