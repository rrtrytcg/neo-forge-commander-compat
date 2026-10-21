import hashlib,json,pathlib
out=pathlib.Path(__file__).resolve().parent
baseline=json.loads((out/'target/preserved-baseline.json').read_text())
for group,files in baseline.items():
    for f in files:
        with open(f['path'],'rb') as stream:
            actual=hashlib.file_digest(stream,'sha256').hexdigest().upper()
        if actual!=f['sha256']:raise RuntimeError('Preserved file changed: '+f['path'])
    print(group, len(files), 'hashes unchanged',flush=True)
