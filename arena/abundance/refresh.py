"""Seal the exact tested candidate, native results and autonomous witnesses, preserving prior lanes."""
import hashlib,json,pathlib,subprocess,zipfile
OUT=pathlib.Path(__file__).resolve().parent
ROOT=OUT.parents[1]
def read(p):return json.loads(pathlib.Path(p).read_text(encoding='utf-8-sig'))
def sha(p):
    with open(p,'rb') as f:return hashlib.file_digest(f,'sha256').hexdigest().upper()
def record(p):return dict(path=str(p),sha256=sha(p))
def verify(files):
    for f in files:
        if sha(f['path'])!=f['sha256']:raise RuntimeError('Changed evidence: '+f['path'])
gate_path=OUT/'target/final-1.0.27/provenance.json';gate=read(gate_path)
assert gate['engine']=='NeoForge 1.0.27' and len(gate['results'])==43
assert sum(r['checks'] for r in gate['results'])==4613
assert next(r['checks'] for r in gate['results'] if r['suite']=='AbundanceTests')==129
verify(gate['files']);verify([dict(path=r['log'],sha256=r['sha256']) for r in gate['results']])
assert sha(gate['candidate'])==gate['candidateSha256']
timeouts=read(OUT/'target/final-1.0.27/timeout-summary.json');verify(timeouts['files'])
assert timeouts['candidateSha256']==gate['candidateSha256'] and timeouts['integrationChecks']==9
assert 'Total tests run: 11, Passes: 11, Failures: 0, Skips: 0' in timeouts['upstream']
probes=read(OUT/'card-use-evidence.json');assert probes['candidateSha256']==gate['candidateSha256'] and len(probes['runs'])==6
for run in probes['runs']:
    verify([dict(path=run['summary'],sha256=run['sha256'])]);verify(read(run['summary'])['files'])
baseline=read(OUT/'target/preserved-baseline.json');verify(baseline['installed'])
preservation=read(OUT/'target/preservation-check.json');assert preservation['installedHashesVerified']==len(baseline['installed'])
source=ROOT/'forge-gui/res/cardsfolder/a/abundance.txt'
old=subprocess.check_output(['git','show','HEAD:forge-gui/res/cardsfolder/a/abundance.txt'],cwd=ROOT)
assert source.read_bytes().replace(b'\r\n',b'\n')==old.replace(b'\r\n',b'\n')
with zipfile.ZipFile(ROOT.parent/'NeoForge/res/cardsfolder/cardsfolder.zip') as z:
    assert source.read_bytes().replace(b'\r\n',b'\n')==z.read('a/abundance.txt').replace(b'\r\n',b'\n')
base_path=ROOT/'arena/adaptive-enchantment/target/estrid-1.0.27/provenance.json'
base=read(base_path)
assert len(base['results'])==42 and sum(r['checks'] for r in base['results'])==4484
assert next(r['checks'] for r in gate['results'] if r['suite']=='EstridUntapTests')==39
assert sha(base['candidate'])==base['candidateSha256']
overlays={p.relative_to(OUT/'target/final-1.0.27/classes').as_posix():p.read_bytes() for p in (OUT/'target/final-1.0.27/classes').rglob('*.class')}
with zipfile.ZipFile(base['candidate']) as original,zipfile.ZipFile(gate['candidate']) as actual:
    unchanged=0
    for e in original.infolist():
        if e.filename in overlays or e.is_dir():continue
        assert actual.read(e.filename)==original.read(e),e.filename
        unchanged+=1
    for name,data in overlays.items():assert actual.read(name)==data,name
coverage=read(ROOT/'arena/coverage/coverage.json')
assert coverage['inventory']['decks']==184 and coverage['inventory']['decksWithZeroBlocked']==46
assert coverage['cards']['mechanicallyUnlocked']==61 and coverage['cards']['strategicallyVerified']==51 and len(coverage['cards']['unlockedOnly'])==10
on=[r for r in probes['runs'] if r['profile']=='on']
report=dict(status='verified',date='2026-10-19',engine=gate['engine'],sourceRevision=gate['revision'],nativeChecks=4613,newNativeChecks=129,priorNativeChecks=4484,nativeSuites=43,deadlineChecks=20,
            probeChecks=probes['checks'],paidResolvedCasts=sum(r['casts'] for r in on),landChoices=sum(r['landChoices'] for r in on),nonlandChoices=sum(r['nonlandChoices'] for r in on),
            foundCards=sum(r['found'] for r in on),bottomedCards=sum(r['bottomed'] for r in on),harmonizeChoices=sum(r['harmonizeChoices'] for r in on),
            clockLimitedRuns=sum(r['clockReached'] for r in probes['runs']),candidate=gate['candidate'],candidateSha256=gate['candidateSha256'],
            coverage=dict(decks=184,clear=46,mechanicallyUnlocked=61,verified=51,unlockedOnly=10),installedFilesPreserved=len(baseline['installed']),estridIntegrated=True,estridChecks=39,baseCandidateSha256=base['candidateSha256'],baseProvenance=record(base_path),
            unchangedCandidateEntries=unchanged,compiledOverlayClasses=len(overlays),scope='Bounded Abundance native decisions and autonomous original card use. No completed-game, winner or strength claim.')
paths=[p for p in OUT.rglob('*') if p.is_file() and not {'target','__pycache__'}.intersection(p.relative_to(OUT).parts) and p.name!='verification-summary.json']
paths+=[gate_path,OUT/'target/preserved-baseline.json',OUT/'target/preservation-check.json',OUT/'target/final-1.0.27/timeout-summary.json',OUT/'target/card-use-check.log',pathlib.Path(gate['candidate']),source,
        ROOT/'forge-ai/src/main/java/forge/ai/AbundanceAi.java',ROOT/'forge-ai/src/main/java/forge/ai/AiController.java',ROOT/'forge-ai/src/main/java/forge/ai/ability/ChooseGenericAi.java',ROOT/'forge-ai/src/main/java/forge/ai/ability/PermanentNoncreatureAi.java',ROOT/'forge-ai/src/main/java/forge/ai/ComputerUtil.java',ROOT/'forge-gui/res/ai/overrides/abundance.properties',
        ROOT/'arena/coverage/card-status.tsv',ROOT/'arena/coverage/COVERAGE.md',ROOT/'arena/coverage/coverage.json',ROOT/'arena/COMMANDER_COMPAT_WORKFLOW.md']
report['files']=[record(p) for p in paths]
(OUT/'verification-summary.json').write_text(json.dumps(report,indent=2))
print(json.dumps({k:v for k,v in report.items() if k!='files'},indent=2))
