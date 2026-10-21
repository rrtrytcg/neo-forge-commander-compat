"""Read-only installed release audit against preserved 1.0.25 native assets."""
import difflib, hashlib, json, pathlib, subprocess, zipfile
ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = pathlib.Path(__file__).resolve().parent
INSTALL = ROOT.parent / 'NeoForge'
OLD = ROOT / 'arena/version-compat/target/neoforge-1.0.25'
def digest(path):
    return hashlib.file_digest(path.open('rb'), 'sha256').hexdigest().upper()
def record(path):
    return dict(path=str(path), sha256=digest(path))
def scripts(path):
    with zipfile.ZipFile(path) as z:
        return {e.filename: z.read(e) for e in z.infolist() if e.filename.endswith('.txt')}
(OUT/'target').mkdir(exist_ok=True)
if not (OUT / 'target/preserved-baseline.json').exists():
    installed = sorted(p for d in ('app', 'res') for p in (INSTALL/d).rglob('*') if p.is_file())
    unrelated = [ROOT/p for p in subprocess.check_output(['git','ls-files','--others','--exclude-standard'],cwd=ROOT,text=True).splitlines()
                 if not p.startswith('arena/version-compat-1.0.27/')]
    (OUT/'target/preserved-baseline.json').write_text(json.dumps(dict(installed=[record(p) for p in installed], unrelated=[record(p) for p in unrelated]), indent=2))
modules=[]
for module in ('core','game','ai','gui'):
    path=INSTALL/f'app/forge-{module}-2.0.16-SNAPSHOT.jar'
    with zipfile.ZipFile(path) as z:
        current={e.filename: hashlib.sha256(z.read(e)).hexdigest().upper() for e in z.infolist() if e.filename.endswith('.class')}
    previous={p.relative_to(OLD/'native').as_posix(): digest(p) for p in (OLD/'native').rglob('*.class')}
    modules.append(dict(module=module, jar=record(path), changed=[k for k,v in current.items() if k in previous and previous[k]!=v], added=[k for k in current if k not in previous]))
old=scripts(OLD/'runtime/res/cardsfolder/cardsfolder.zip')
new=scripts(INSTALL/'res/cardsfolder/cardsfolder.zip')
changed=[k for k in new if k in old and old[k]!=new[k]]
registry=[line.split('\t')[0] for line in (ROOT/'arena/coverage/card-status.tsv').read_text(encoding='utf-8-sig').splitlines() if line and not line.startswith('#')]
registered=[]
for name in registry:
    for k,v in new.items():
        if ('Name:'+name+'\n') in v.decode('utf-8-sig').replace('\r\n','\n'):
            registered.append(dict(card=name, script=k, changed=k not in old or old[k]!=v)); break
with zipfile.ZipFile(INSTALL/'app/neo.jar') as z:
    build=z.read('forge/neo/build.properties').decode()
deckdir=pathlib.Path('res/adventure/Realm of Legends/decks/starter/commanderprecon')
olddecks={p.name:digest(p) for p in (OLD/'runtime'/deckdir).glob('*.dck')}
newdecks={p.name:digest(p) for p in (INSTALL/deckdir).glob('*.dck')}
result=dict(engine='NeoForge 1.0.27', upstream='77b6f5ae95f7f11d5a718c52a977470131db0fe1', build=build, modules=modules,
            cards=dict(identical=sum(k in old and v==old[k] for k,v in new.items()), changed=changed, added=[k for k in new if k not in old], removed=[k for k in old if k not in new], registered=registered),
            commanderDecks=dict(count=len(newdecks),changed=[k for k in newdecks if k in olddecks and newdecks[k]!=olddecks[k]],added=[k for k in newdecks if k not in olddecks],removed=[k for k in olddecks if k not in newdecks]))
(OUT/'card-script-changes.patch').write_text(''.join(''.join(difflib.unified_diff(old[k].decode('utf-8-sig').splitlines(True),new[k].decode('utf-8-sig').splitlines(True),fromfile='1.0.25/'+k,tofile='1.0.27/'+k)) for k in changed))
(OUT/'installed-audit.json').write_text(json.dumps(result,indent=2))
print(json.dumps(dict(build=build, modules=[dict(module=m['module'],changed=len(m['changed']),added=len(m['added'])) for m in modules], cards=dict(identical=result['cards']['identical'],changed=changed,added=result['cards']['added'],registeredChanged=[r for r in registered if r['changed']])),indent=2))
