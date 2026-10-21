"""Read-only audit of the installed NeoForge 1.0.28 against the preserved 1.0.27 baseline; writes the 1.0.28 baseline."""
import difflib, hashlib, json, pathlib, zipfile
ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = pathlib.Path(__file__).resolve().parent
INSTALL = ROOT.parent / 'NeoForge'
OLD = ROOT / 'arena/version-compat-1.0.27/target'
def digest(path):
    with open(path, 'rb') as s: return hashlib.file_digest(s, 'sha256').hexdigest().upper()
old = {f['path']: f['sha256'] for f in json.loads((OLD/'preserved-baseline.json').read_text())['installed']}
files = sorted(p for d in ('app', 'res') for p in (INSTALL/d).rglob('*') if p.is_file())
new = {str(p): digest(p) for p in files}
changed = sorted(k for k in new if k in old and old[k] != new[k]); added = sorted(k for k in new if k not in old); removed = sorted(k for k in old if k not in new)
rel = lambda k: str(pathlib.Path(k).relative_to(INSTALL))
def scripts(path):
    with zipfile.ZipFile(path) as z: return {e.filename: z.read(e) for e in z.infolist() if e.filename.endswith('.txt')}
oldcards = scripts(OLD/'runtime/res/cardsfolder/cardsfolder.zip'); newcards = scripts(INSTALL/'res/cardsfolder/cardsfolder.zip')
cchanged = [k for k in newcards if k in oldcards and oldcards[k] != newcards[k]]
registry = [l.split('\t')[0] for l in (ROOT/'arena/coverage/card-status.tsv').read_text(encoding='utf-8-sig').splitlines() if l and not l.startswith('#')]
registered = []
for name in registry:
    for k, v in newcards.items():
        if ('Name:' + name + '\n') in v.decode('utf-8-sig').replace('\r\n', '\n'):
            registered.append(dict(card=name, script=k, changed=k not in oldcards or oldcards[k] != v)); break
deckdir = pathlib.Path('res/adventure/Realm of Legends/decks/starter/commanderprecon')
olddecks = {p.name: digest(p) for p in (OLD/'runtime'/deckdir).glob('*.dck')}
newdecks = {p.name: digest(p) for p in (INSTALL/deckdir).glob('*.dck')}
with zipfile.ZipFile(INSTALL/'app/neo.jar') as z: build = z.read('forge/neo/build.properties').decode()
result = dict(engine='NeoForge 1.0.28', upstream='84f4983c', baseline='arena/version-compat-1.0.27/target/preserved-baseline.json', build=build,
    installedFiles=len(new), unchangedFiles=len(new) - len(changed) - len(added), changedFiles=[rel(k) for k in changed], addedFiles=[rel(k) for k in added], removedFiles=[rel(k) for k in removed],
    cards=dict(identical=sum(k in oldcards and v == oldcards[k] for k, v in newcards.items()), changed=cchanged, added=[k for k in newcards if k not in oldcards], removed=[k for k in oldcards if k not in newcards],
               registeredCount=len(registered), registeredChanged=[r for r in registered if r['changed']]),
    commanderDecks=dict(count=len(newdecks), changed=[k for k in newdecks if k in olddecks and newdecks[k] != olddecks[k]], added=[k for k in newdecks if k not in olddecks], removed=[k for k in olddecks if k not in newdecks]))
(OUT/'target').mkdir(exist_ok=True)
(OUT/'installed-audit.json').write_text(json.dumps(result, indent=2))
(OUT/'card-script-changes.patch').write_text(''.join(''.join(difflib.unified_diff(oldcards[k].decode('utf-8-sig').splitlines(True), newcards[k].decode('utf-8-sig').splitlines(True), fromfile='1.0.27/' + k, tofile='1.0.28/' + k)) for k in cchanged), encoding='utf-8')
bl = OUT/'target/preserved-baseline.json'
if not bl.exists(): bl.write_text(json.dumps(dict(installed=[dict(path=k, sha256=v) for k, v in new.items()]), indent=2))
print(json.dumps({k: (v if not isinstance(v, list) or len(v) < 40 else f'{len(v)} items') for k, v in result.items()}, indent=1, default=str)[:6000])
