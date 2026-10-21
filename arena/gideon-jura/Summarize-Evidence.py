import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).parent
TARGET = ROOT / "target"
gate_dir = TARGET / "final-1.0.28"
gate = json.loads((gate_dir / "provenance.json").read_text(encoding="utf-8"))
baseline_log = TARGET / "baseline-1.0.28-baseline" / "GideonJuraBaselineTests.log"
focused_log = TARGET / "focused-1.0.28" / "GideonJuraTests.log"

def pass_count(path):
    return sum(1 for line in path.read_text(encoding="utf-8", errors="replace").splitlines() if line.startswith("PASS "))

preserved = json.loads(Path("arena/version-compat-1.0.28/target/preserved-baseline.json").read_text(encoding="utf-8"))["installed"]
changed = [item["path"] for item in preserved if hashlib.sha256(Path(item["path"]).read_bytes()).hexdigest().upper() != item["sha256"]]
exposures = []
for seed in (129011, 129012):
    for mode in ("off", "on"):
        path = TARGET / "smoke" / f"{mode}-{seed}" / "summary.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        exposures.append({
            "seed": seed,
            "mode": mode,
            "exit": data["exit"],
            "lastTurn": data["lastTurn"],
            "exceptions": data["exceptions"],
            "counts": data["counts"],
            "examples": data["examples"],
            "candidateSha256": data["candidateSha256"],
            "log": data["log"],
            "logSha256": data["logSha256"],
        })

result = {
    "card": "Gideon Jura",
    "profile": "gideon-jura",
    "engine": gate["engine"],
    "baselineChecks": pass_count(baseline_log),
    "focusedChecks": pass_count(focused_log),
    "fullGate": {
        "checks": sum(item["checks"] for item in gate["results"]),
        "suites": len(gate["results"]),
        "priorChecks": int(gate["priorChecks"]),
        "newChecks": sum(item["checks"] for item in gate["results"] if item["suite"] in gate["newSuites"]),
        "candidateSha256": gate["candidateSha256"],
    },
    "installedHashesCheckedAfterExposure": len(preserved),
    "installedHashesChanged": changed,
    "exposures": exposures,
    "scope": "Card-use and native decision evidence only; no winner or strength claim",
}
if result["baselineChecks"] != 2 or result["focusedChecks"] != 15:
    raise SystemExit("Expected baseline 2/2 and focused 15/15 checks")
if result["fullGate"] != {**result["fullGate"], "checks": 5735, "suites": 57, "priorChecks": 5720, "newChecks": 15}:
    raise SystemExit("Unexpected full-gate totals")
if len(changed) or len(exposures) != 4 or any(x["exit"] != 0 or x["exceptions"] for x in exposures):
    raise SystemExit("Installed-state or exposure integrity check failed")
for item in exposures:
    counts = item["counts"]
    if item["mode"] == "off" and any(counts[name] for name in ("casts", "activates", "resolvesSpell", "resolvesDestroy")):
        raise SystemExit("Profile-off exposure unexpectedly used Gideon")
    if item["mode"] == "on" and not all(counts[name] > 0 for name in ("casts", "activates", "resolvesSpell", "resolvesDestroy")):
        raise SystemExit("Profile-on exposure did not exercise Gideon")

(ROOT / "verification-summary.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(f"baseline={result['baselineChecks']}; focused={result['focusedChecks']}; full={result['fullGate']['checks']}/{result['fullGate']['suites']}; installed={len(preserved)} changed={len(changed)}")
for item in exposures:
    print(f"{item['mode']} {item['seed']}: {item['counts']}; exceptions={item['exceptions']}; lastTurn={item['lastTurn']}")
