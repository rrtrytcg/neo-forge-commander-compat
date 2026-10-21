"""Replace native modules, including removal of obsolete classes, in a private harness."""
import pathlib,sys,zipfile
desktop,install,candidate=map(pathlib.Path,sys.argv[1:])
old=pathlib.Path(__file__).resolve().parents[1]/'version-compat/target/neoforge-1.0.25/native'
previous={p.relative_to(old).as_posix() for p in old.rglob('*.class')}
native={}
for m in ('core','game','ai','gui'):
    with zipfile.ZipFile(install/f'app/forge-{m}-2.0.16-SNAPSHOT.jar') as z:
        native.update({e.filename:z.read(e) for e in z.infolist() if e.filename.endswith('.class')})
with zipfile.ZipFile(desktop) as source,zipfile.ZipFile(candidate,'w',zipfile.ZIP_DEFLATED) as dest:
    for e in source.infolist():
        if e.filename not in previous and e.filename not in native:
            dest.writestr(e,source.read(e))
    for name,data in native.items():dest.writestr(name,data)
print('Native classes:',len(native),'obsolete removed:',len(previous-native.keys()))
