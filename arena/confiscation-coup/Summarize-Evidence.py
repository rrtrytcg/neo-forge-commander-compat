#!/usr/bin/env python3
"""Summarize the sealed Confiscation Coup gate and paired Commander smoke evidence."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent
TARGET = ROOT / "target"
GATE_DIR = TARGET / "final-1.0.27-current-install"
PATTERNS = {
    "cast": "cast Confiscation Coup",
    "resolution": "Resolve Stack: Confiscation Coup",
    "control": "gains control of ",
    "highTarget": "Resolve Stack: Craw Wurm",
}


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def witnesses(path: Path) -> dict[str, list[dict[str, object]]]:
    found = {key: [] for key in PATTERNS}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        for key, needle in PATTERNS.items():
            if needle in line:
                found[key].append({"line": number, "text": line})
    return found


def main() -> None:
    gate = read_json(GATE_DIR / "provenance.json")
    new_suite = next(item for item in gate["results"] if item["suite"] == "ConfiscationCoupTests")
    baseline = read_json(ROOT.parent / "version-compat-1.0.27/target/preserved-baseline.json")["installed"]
    installed_snapshot = read_json(TARGET / "installed-snapshot.json")["installed"]
    install_check = read_json(TARGET / "installed-snapshot-verification.json")
    exposure = []
    for seed in (128011, 128012):
        for mode in ("off", "on"):
            run_dir = TARGET / "smoke" / f"{mode}-{seed}"
            run = read_json(run_dir / "summary.json")
            log = Path(run["log"])
            exposure.append({
                "mode": mode,
                "seed": seed,
                "clockSeconds": run["clockSeconds"],
                "exit": run["exit"],
                "counts": run["counts"],
                "lastTurn": run["lastTurn"],
                "exceptions": run["exceptions"],
                "result": run["result"],
                "logSha256": sha256(log),
                "deckSha256": [sha256(ROOT / "decks" / name) for name in ("Actor.dck", "Opponent.dck")],
                "witnesses": witnesses(log),
            })
    result = {
        "engine": gate["engine"],
        "revision": gate["revision"],
        "candidateSha256": gate["candidateSha256"],
        "base": gate["base"],
        "gate": {
            "checks": sum(item["checks"] for item in gate["results"]),
            "suites": len(gate["results"]),
            "priorChecks": int(gate["priorChecks"]),
            "newChecks": new_suite["checks"],
            "newSuiteSha256": new_suite["sha256"],
            "baselineChecks": 4,
            "focusedChecks": 13,
            "installedHashesChecked": install_check["installedHashesChecked"],
            "installedHashesChangedDuringGate": install_check["installedHashesChangedDuringGate"],
            "installedSnapshotSha256": install_check["snapshotSha256"],
            "installedHashesDifferFrom1_0_27": sum(a["sha256"] != b["sha256"] for a, b in zip(baseline, installed_snapshot)),
        },
        "exposure": exposure,
        "scope": "Paired custom-deck card-use and target-selection evidence only; no winner or strength claim.",
    }
    out = ROOT / "verification-summary.json"
    out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {out}")


if __name__ == "__main__":
    main()
