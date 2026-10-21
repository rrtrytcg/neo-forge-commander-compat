"""Reconcile current opt-in sources without changing the working source tree."""
import json, pathlib, subprocess
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=pathlib.Path(__file__).resolve().parent
SRC=OUT/'target/source'
OLD='64569999b8b99dd7176ab9d3e1e6008f3ab68590'
NEW='77b6f5ae95f7f11d5a718c52a977470131db0fe1'
def git(*args):
    return subprocess.check_output(['git',*args],cwd=ROOT)
def apply(path):
    subprocess.run(['git','apply','--whitespace=error','--directory='+SRC.relative_to(ROOT).as_posix(),str(path)],cwd=ROOT,check=True)
paths=[p for p in git('diff','--name-only','e8ca48dd','HEAD','--','forge-ai/src/main/java','forge-game/src/main/java','forge-core/src/main/java').decode().splitlines() if p.endswith('.java')]
merged=['forge-ai/src/main/java/forge/ai/'+s+'.java' for s in ('AiAttackController','AiBlockController','AiController','ComputerUtil')]
for p in paths:
    dest=SRC/p; dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_bytes(git('show',OLD+':'+p) if p in merged else (ROOT/p).read_bytes())
apply(ROOT/'arena/version-compat/neoforge-1.0.25-hooks.patch')
post=OUT/'post-1.0.25-hooks.patch'
post.write_bytes(git('diff','76b02a69','HEAD','--',*merged))
apply(post)
upstream=OUT/'upstream-overlaps.patch'
upstream.write_bytes(git('diff',OLD,NEW,'--',*paths))
apply(upstream)
# Everything outside these explicitly compiled sources remains native 1.0.27 bytecode.
(OUT/'target/source-list.json').write_text(json.dumps(paths,indent=2))
print('Reconciled',len(paths),'source files; native upstream overlaps:')
print(git('diff','--name-only',OLD,NEW,'--',*paths).decode())
