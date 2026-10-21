"""Independent public-event checks of original native mode chains, costs and destruction."""
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
check(gate['engine']=='NeoForge 1.0.27' and len(gate['results'])==40,'full native gate')
verify(gate['files']);check(sha(gate['candidate'])==gate['candidateSha256'],'tested engine')
runs=[];modes=set()
for scenario in ('artifact','enchantment','both'):
    for profile in ('on','off'):
        seed={'artifact':112011,'enchantment':112021,'both':112031}[scenario]
        label=f'{scenario}-{profile}-{seed}';folder=OUT/'target/card-use'/label
        s=read(folder/'summary.json');verify(s['files'])
        check(s['engine']==gate['engine'] and s['candidateSha256']==gate['candidateSha256'] and s['scenario']==scenario and s['profile']==profile and s['seed']==seed,label+' requested engine and exposure')
        check(s['nativeExit']==0 and not s['unexpectedDiagnostics'],label+' no native diagnostic failure')
        rows=[];cutoff=False
        for line in (folder/'probe.log').read_text(encoding='utf-8-sig').splitlines():
            if line.startswith(('HULL_PROBE_CLOCK_STOP','HULL_PROBE_DONE')):cutoff=True
            if line.startswith('HULL_WITNESS '):
                check(not cutoff,label+' event before observation cutoff');rows.append(json.loads(line[len('HULL_WITNESS '):]))
        check(not re.search(r"Couldn't add to stack|AI failed to play",(folder/'probe.log').read_text()),label+' no native payment/stack failure')
        casts=[r for r in rows if r['kind']=='cast'];resolved=[r for r in rows if r['kind']=='resolved']
        check(len(casts)==len(resolved),label+' no pending observed actions')
        count=0;destroyed=0;single_a=0;single_e=0;both=0
        for r in resolved:
            b=r['before'];check(not r['fizzled'],label+' matched action does not fizzle')
            check(sum(c['abilityId']==b['abilityId'] for c in casts)==1 and r['abilityId']==b['abilityId'],label+' unique original action pairing')
            if b['source']['name']!='Hull Breach':continue
            count+=1
            check(profile=='on' and b['spell'] and b['player']=='HullProbe-0' and b['turnPlayer']==b['player'] and b['phase'] in ('MAIN1','MAIN2'),label+' opted-in normal sorcery timing')
            check(b['stackSize']==1 and len(b['mana'])==2 and sorted(m['color'] for m in b['mana'])==['G','R'],label+' native RG cost after an empty stack')
            check(len(b['targets']) in (1,2) and len({t['id'] for t in b['targets']})==len(b['targets']),label+' distinct original targets')
            check(all(t['zone']=='Battlefield' and t['controller']!='HullProbe-0' for t in b['targets']),label+' targets opposing battlefield permanents')
            check([t['id'] for t in r['targets']]==[t['id'] for t in b['targets']],label+' native targets unchanged through resolution')
            check(all(t['zone']=='Graveyard' for t in r['targets']),label+' actual native destruction of every selected target')
            chain=b['chain'];check(chain[0]['api']=='Charm',label+' original Charm root')
            if len(b['targets'])==2:
                check([n['api'] for n in chain]==['Charm','Pump','Pump','Destroy'] and chain[1]['valid']=='Artifact' and chain[2]['valid']=='Enchantment' and chain[3]['defined']=='Targeted',label+' original combined chain')
                check(b['targets'][0]['artifact'] and b['targets'][1]['enchantment'],label+' one artifact and one distinct enchantment');both+=1;modes.add('both')
            else:
                check([n['api'] for n in chain]==['Charm','Destroy'],label+' original single destruction chain')
                kind=chain[1]['valid'];check(kind in ('Artifact','Enchantment') and b['targets'][0][kind.lower()],label+' native single target type')
                if kind=='Artifact':single_a+=1;modes.add('artifact')
                else:single_e+=1;modes.add('enchantment')
            destroyed+=len(b['targets'])
        check(any(r['before']['source']['name']=='Centaur Courser' for r in resolved),label+' ordinary native creature casting remains active')
        if profile=='off':check(count==0,label+' default admission remains blocked')
        else:
            check(count>0,label+' autonomous paid original casts witnessed')
            check((single_a>0 if scenario=='artifact' else single_e>0 if scenario=='enchantment' else both>0),label+' requested native mode witnessed')
        runs.append(dict(scenario=scenario,profile=profile,seed=seed,casts=count,destroyed=destroyed,artifactOnly=single_a,enchantmentOnly=single_e,both=both,clockReached=s['clockReached'],summary=str(folder/'summary.json'),sha256=sha(folder/'summary.json')))
check(modes=={'artifact','enchantment','both'},'all three native modes exposed')
(OUT/'card-use-evidence.json').write_text(json.dumps(dict(engine=gate['engine'],candidateSha256=gate['candidateSha256'],checks=checks,runs=runs,scope='Native autonomous card-use only; no completion, winner or strength claim'),indent=2))
print('DONE',checks,'Hull Breach card-use checks')
