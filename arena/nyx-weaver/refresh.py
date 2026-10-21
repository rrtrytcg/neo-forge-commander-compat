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
assert gate['engine']=='NeoForge 1.0.27' and len(gate['results'])==46
assert sum(r['checks'] for r in gate['results'])==5122
assert next(r['checks'] for r in gate['results'] if r['suite']=='NyxWeaverTests')==224
verify(gate['files']);verify([dict(path=r['log'],sha256=r['sha256']) for r in gate['results']])
assert sha(gate['candidate'])==gate['candidateSha256']
timeouts=read(OUT/'target/final-1.0.27/timeout-summary.json');verify(timeouts['files'])
assert timeouts['candidateSha256']==gate['candidateSha256'] and timeouts['integrationChecks']==9
assert 'Total tests run: 11, Passes: 11, Failures: 0, Skips: 0' in timeouts['upstream']
probes=read(OUT/'card-use-evidence.json');assert probes['candidateSha256']==gate['candidateSha256'] and len(probes['runs'])==8
for run in probes['runs']:
    verify([dict(path=run['summary'],sha256=run['sha256'])]);verify(read(run['summary'])['files'])
baseline=read(ROOT/'arena/version-compat-1.0.27/target/preserved-baseline.json');verify(baseline['installed'])
source=ROOT/'forge-gui/res/cardsfolder/n/nyx_weaver.txt'
old=subprocess.check_output(['git','show','HEAD:forge-gui/res/cardsfolder/n/nyx_weaver.txt'],cwd=ROOT)
assert source.read_bytes().replace(b'\r\n',b'\n')==old.replace(b'\r\n',b'\n')
with zipfile.ZipFile(ROOT.parent/'NeoForge/res/cardsfolder/cardsfolder.zip') as z:
    assert source.read_bytes().replace(b'\r\n',b'\n')==z.read('n/nyx_weaver.txt').replace(b'\r\n',b'\n')
base_path=ROOT/'arena/utility-lands/target/final-1.0.27/provenance.json'
base=read(base_path)
assert len(base['results'])==45 and sum(r['checks'] for r in base['results'])==4898
assert next(r['checks'] for r in gate['results'] if r['suite']=='EstridUntapTests')==39
assert next(r['checks'] for r in gate['results'] if r['suite']=='UtilityLandsTests')==51
assert sha(base['candidate'])==base['candidateSha256']
native_baseline=read(OUT/'target/final-1.0.27-baseline/provenance.json')
assert native_baseline['baseline'] and native_baseline['candidateSha256']==base['candidateSha256']
assert sum(r['checks'] for r in native_baseline['results'])==3
verify(native_baseline['files']);verify([dict(path=r['log'],sha256=r['sha256']) for r in native_baseline['results']])

overlays={p.relative_to(OUT/'target/final-1.0.27/classes').as_posix():p.read_bytes() for p in (OUT/'target/final-1.0.27/classes').rglob('*.class')}
with zipfile.ZipFile(base['candidate']) as original,zipfile.ZipFile(gate['candidate']) as actual:
    unchanged=0
    for e in original.infolist():
        if e.filename in overlays or e.is_dir():continue
        assert actual.read(e.filename)==original.read(e),e.filename
        unchanged+=1
    for name,data in overlays.items():assert actual.read(name)==data,name
coverage=read(ROOT/'arena/coverage/coverage.json')
assert coverage['inventory']['decks']==184 and coverage['inventory']['decksWithZeroBlocked']==52
assert coverage['cards']['mechanicallyUnlocked']==67 and coverage['cards']['strategicallyVerified']==57 and len(coverage['cards']['unlockedOnly'])==10
on=[r for r in probes['runs'] if r['profile']=='on']
report=dict(status='verified',date='2026-10-19',engine=gate['engine'],sourceRevision=gate['revision'],nativeChecks=5122,newNativeChecks=224,priorNativeChecks=4898,nativeSuites=46,deadlineChecks=20,
            probeChecks=probes['checks'],paidResolvedCasts=sum(r['casts'] for r in on),paidResolvedActivations=sum(r['activations'] for r in on),
            millTriggers=sum(r['mills'] for r in on),cardsMilled=sum(r['milled'] for r in on),ordinaryRecoveries=sum(r['value'] for r in on),
            doomedRecoveries=sum(r['doomed'] for r in on),lowLibraryRecoveries=sum(r['lowLibrary'] for r in on),laterNativeCasts=sum(r['laterCasts'] for r in on),sameTurnNativeCasts=sum(r['sameTurnCasts'] for r in on),
            clockLimitedRuns=sum(r['clockReached'] for r in probes['runs']),candidate=gate['candidate'],candidateSha256=gate['candidateSha256'],
            coverage=dict(decks=184,clear=52,mechanicallyUnlocked=67,verified=57,unlockedOnly=10),installedFilesPreserved=len(baseline['installed']),estridIntegrated=True,estridChecks=39,utilityOverlayPreserved=True,utilityRegressionChecks=51,baseCandidateSha256=base['candidateSha256'],baseProvenance=record(base_path),
            unchangedCandidateEntries=unchanged,compiledOverlayClasses=len(overlays),scope='Bounded Nyx Weaver native decisions and autonomous original card use. No completed-game, winner or strength claim.')
paths=[p for p in OUT.rglob('*') if p.is_file() and not {'target','__pycache__'}.intersection(p.relative_to(OUT).parts) and p.name!='verification-summary.json']
paths+=[gate_path,OUT/'target/final-1.0.27-baseline/provenance.json',ROOT/'arena/lib/Check-Gate.ps1',ROOT/'arena/version-compat-1.0.27/target/preserved-baseline.json',OUT/'target/final-1.0.27/timeout-summary.json',OUT/'target/card-use-check.log',pathlib.Path(gate['candidate']),source,
        ROOT/'forge-ai/src/main/java/forge/ai/NyxWeaverAi.java',ROOT/'forge-ai/src/main/java/forge/ai/ability/PermanentCreatureAi.java',ROOT/'forge-ai/src/main/java/forge/ai/ability/ChangeZoneAi.java',ROOT/'forge-ai/src/main/java/forge/ai/ComputerUtil.java',ROOT/'forge-gui/res/ai/overrides/nyx-weaver.properties',
        ROOT/'arena/coverage/card-status.tsv',ROOT/'arena/coverage/COVERAGE.md',ROOT/'arena/coverage/coverage.json',ROOT/'arena/COMMANDER_COMPAT_WORKFLOW.md']
report['files']=[record(p) for p in paths]
(OUT/'verification-summary.json').write_text(json.dumps(report,indent=2))
print(json.dumps({k:v for k,v in report.items() if k!='files'},indent=2))
