"""Strict installed-file check; intentionally ignore the retired unrelated group."""
import hashlib,json,pathlib
out=pathlib.Path(__file__).resolve().parent
baseline=json.loads((out/'target/preserved-baseline.json').read_text())
def sha(p):
    with open(p,'rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest().upper()
for f in baseline['installed']:
    if sha(f['path'])!=f['sha256']:raise RuntimeError('Installed file changed: '+f['path'])
report=dict(installedHashesVerified=len(baseline['installed']),scope='Installed group only. Retired research folders and independently committed Estrid files are outside the preservation check.')
(out/'target/preservation-check.json').write_text(json.dumps(report,indent=2))
print('installed',len(baseline['installed']),'hashes unchanged',flush=True)
