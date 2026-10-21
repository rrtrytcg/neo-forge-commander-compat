"""Validate current evidence and seal a separate release report; never rewrite historical lanes."""
import hashlib,json,pathlib,subprocess,zipfile
OUT=pathlib.Path(__file__).resolve().parent
ROOT=OUT.parents[1]
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def sha(path):
    with open(path,'rb') as f:return hashlib.file_digest(f,'sha256').hexdigest().upper()
def record(path):return dict(path=str(path),sha256=sha(path))
def verify(files):
    for f in files:
        if sha(f['path'])!=f['sha256']:raise RuntimeError('Evidence changed: '+f['path'])
gate=read(OUT/'target/provenance.json')
assert gate['engine']=='NeoForge 1.0.27' and len(gate['results'])==38
assert sum(r['checks'] for r in gate['results'])==4053
assert sha(gate['candidate'])==gate['candidateSha256']
verify(gate['files']);verify([dict(path=r['log'],sha256=r['sha256']) for r in gate['results']])
timeouts=read(OUT/'target/timeout-summary.json');upstream=read(OUT/'target/upstream-summary.json');probes=read(OUT/'card-use-evidence.json')
assert 'Total tests run: 11, Passes: 11, Failures: 0, Skips: 0' in timeouts['upstream']
assert timeouts['integrationChecks']==9 and upstream['checks']==7
for evidence in (timeouts,upstream,probes):assert evidence['candidateSha256']==gate['candidateSha256']
verify(timeouts['files']);verify(upstream['files'])
assert len(probes['runs'])==10
for run in probes['runs']:
    verify([dict(path=run['summary'],sha256=run['sha256'])])
    summary=read(pathlib.Path(run['summary']));verify(summary['files'])
    assert summary['candidateSha256']==gate['candidateSha256'] and summary['nativeExit']==0 and not summary['unexpectedDiagnostics']
baseline=read(OUT/'target/preserved-baseline.json')
verify(baseline['installed']);verify(baseline['unrelated'])
# Check the actual assembly: every native class is either unchanged or the exact compiled overlay.
overlays={p.relative_to(OUT/'target/classes').as_posix():p.read_bytes() for p in (OUT/'target/classes').rglob('*.class')}
native_count=0
with zipfile.ZipFile(gate['candidate']) as candidate:
    for module in ('core','game','ai','gui'):
        with zipfile.ZipFile(ROOT.parent/f'NeoForge/app/forge-{module}-2.0.16-SNAPSHOT.jar') as native:
            for e in native.infolist():
                if not e.filename.endswith('.class'):continue
                assert candidate.read(e.filename)==overlays.get(e.filename,native.read(e)),e.filename
                native_count+=1
    for name,data in overlays.items():assert candidate.read(name)==data,name
on=[r for r in probes['runs'] if r['profile']=='on']
report=dict(passed=True,date='2026-10-19',installedVersion='1.0.27',upstreamRef=gate['upstream'],baseRevision=gate['revision'],
            nativeChecks=4060,commanderChecks=4053,commanderSuites=38,upstreamCombatChecks=7,upstreamTimeoutTests=11,integrationChecks=9,
            probeChecks=probes['checks'],probePaidResolvedCasts=sum(r['artifactCasts'] for r in on),probeActivations=sum(r['activations'] for r in on),
            probeAttacks=sum(r['actualAttacks'] for r in on),probeBlocks=sum(r['actualBlocks'] for r in on),probeRestorations=sum(r['restorationWitnesses'] for r in on),
            clockLimitedRuns=sum(r['clockReached'] for r in probes['runs']),candidate=gate['candidate'],candidateSha256=gate['candidateSha256'],
            installedFilesPreserved=len(baseline['installed']),unrelatedFilesPreserved=len(baseline['unrelated']),nativeClassesVerified=native_count,overlayClassesVerified=len(overlays),
            scope='Current Commander opt-in native correctness plus representative autonomous Mirage Mirror card use on a private engine. No installed UI deployment, completed-game, strength or broad research-policy certification.')
(OUT/'verification-summary.json').write_text(json.dumps(report,indent=2))
# Regenerate the human report only after the complete passed snapshot exists.
subprocess.run(['pwsh','-NoProfile','-File',str(ROOT/'arena/Audit-Deck.ps1'),'-All','-Coverage'],cwd=ROOT,stdout=subprocess.DEVNULL,check=True)
coverage=read(ROOT/'arena/coverage/coverage.json')
assert coverage['inventory']['decks']==184 and coverage['inventory']['decksWithZeroBlocked']==46
assert coverage['cards']['mechanicallyUnlocked']==58 and coverage['cards']['strategicallyVerified']==48 and len(coverage['cards']['unlockedOnly'])==10
report['coverage']=dict(decks=184,clear=46,mechanicallyUnlocked=58,verified=48,unlockedOnly=10)
paths=[p for p in OUT.rglob('*') if p.is_file() and not {'target','__pycache__'}.intersection(p.relative_to(OUT).parts) and p.name!='verification-summary.json']
paths += [OUT/'target/provenance.json',OUT/'target/build.json',OUT/'target/preserved-baseline.json',OUT/'target/timeout-summary.json',OUT/'target/upstream-summary.json',pathlib.Path(gate['candidate']),
          OUT/'target/original-seeds-card-use-check.log',OUT/'target/original-seeds-checker.ps1',OUT/'target/card-use-check.log',
          ROOT/'arena/Audit-Deck.ps1',ROOT/'arena/COMMANDER_COMPAT_WORKFLOW.md',ROOT/'arena/coverage/COVERAGE.md',ROOT/'arena/coverage/coverage.json',ROOT/'arena/coverage/card-status.tsv']
desktop=next((ROOT/'forge-gui-desktop/target').glob('*jar-with-dependencies.jar'));paths.append(desktop)
report['files']=[record(p) for p in paths]
(OUT/'verification-summary.json').write_text(json.dumps(report,indent=2))
print(json.dumps({k:v for k,v in report.items() if k!='files'},indent=2))
