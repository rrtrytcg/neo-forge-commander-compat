"""Candidate for NeoForge 1.0.28: installed core/game/ai/gui classes + desktop harness + the sealed 1.0.27 opt-in overlay classes.

Overlay classes = classes in the sealed 1.0.27 gate engine.jar whose bytes differ from the installed 1.0.28 native class, minus the
four native classes upstream 1.0.28 itself changed (Player, RestartGameEffect, FModel, ForgePreferences). No arena overlay compiles any of them
(checked against every compiled source recorded in the arena build.json manifests).
Usage: assemble.py <desktop-harness-jar> <install-dir> <sealed-1.0.27-engine.jar> <candidate-out> <manifest-out>"""
import json, pathlib, sys, zipfile, hashlib, importlib.util
desktop, install, sealed, candidate, manifest = map(pathlib.Path, sys.argv[1:])
UPSTREAM_CHANGED = {'forge/game/player/Player', 'forge/game/ability/effects/RestartGameEffect', 'forge/model/FModel', 'forge/localinstance/properties/ForgePreferences'}
spec = importlib.util.spec_from_file_location('a27', pathlib.Path(__file__).resolve().parents[1] / 'version-compat-1.0.27/assemble.py')
tmp = candidate.with_suffix('.base.jar')
sys.argv = ['assemble.py', str(desktop), str(install), str(tmp)]
spec.loader.exec_module(importlib.util.module_from_spec(spec))  # the unchanged 1.0.27 assembler: harness + installed native modules
native = {}
for m in ('core', 'game', 'ai', 'gui'):
    with zipfile.ZipFile(install / f'app/forge-{m}-2.0.16-SNAPSHOT.jar') as z:
        native.update({e.filename: z.read(e) for e in z.infolist() if e.filename.endswith('.class')})
with zipfile.ZipFile(sealed) as s, zipfile.ZipFile(tmp) as b, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as out:
    base = {e.filename: b.read(e) for e in b.infolist()}
    top = lambda n: n.split('$')[0].removesuffix('.class')
    # Everything in the sealed 1.0.27 jar that differs from the fresh 1.0.28 base is an arena overlay (new or native-replacing class),
    # except the classes upstream 1.0.28 changed and the harness module descriptors, which come from the fresh base.
    overlays = [e.filename for e in s.infolist() if not e.is_dir() and e.filename in base and s.read(e) != base[e.filename] and top(e.filename) not in UPSTREAM_CHANGED and not e.filename.endswith('module-info.class')]
    overlays += [e.filename for e in s.infolist() if not e.is_dir() and e.filename not in base]
    skip = set(overlays)
    for e in b.infolist():
        if e.filename not in skip: out.writestr(e, b.read(e))
    for n in overlays: out.writestr(n, s.read(n))
tmp.unlink()
manifest.write_text(json.dumps(dict(overlayClasses=sorted(overlays), sealedSource=str(sealed), sealedSourceSha256=hashlib.sha256(sealed.read_bytes()).hexdigest().upper()), indent=1))
print('overlay classes:', len(overlays))
