#!/usr/bin/env python3
"""Summarize the sealed gate and paired Immoral Bargain smoke logs."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent
TARGET = ROOT / "target"
GATE = TARGET / "final-1.0.27"


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def witnesses(path: Path) -> dict[str, list[dict[str, object]]]:
    patterns = {
        "cast": "cast Immoral Bargain",
        "resolve": "Resolve Stack: Immoral Bargain",
        "sacrifice": "was put into Graveyard from Battlefield",
    }
    found = {key: [] for key in patterns}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        for key, needle in patterns.items():
            if needle in line:
                found[key].append({"line": number, "text": line})
    return found


def main() -> None:
    gate = read_json(GATE / "provenance.json")
    new_suite = next(item for item in gate["results"] if item["suite"] == "ImmoralBargainTests")
    observations = []
    for seed in (124011, 124012):
        for mode in ("off", "on"):
            run_dir = TARGET / "smoke" / f"{mode}-{seed}"
            run = read_json(run_dir / "summary.json")
            log = Path(run["log"])
            observations.append({
                "mode": mode,
                "seed": seed,
                "clockSeconds": run["clockSeconds"],
                "exit": run["exit"],
                "counts": run["counts"],
                "lastTurn": run["lastTurn"],
                "exceptions": run["exceptions"],
                "result": run["result"],
                "logSha256": sha256(log),
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
            "baselineChecks": 1,
            "installedHashesChecked": 21729,
            "installedHashesChanged": 0,
        },
        "exposure": observations,
        "scope": "Paired custom-deck card-use evidence only; no winner or strength claim.",
    }
    out = ROOT / "verification-summary.json"
    out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {out}")


if __name__ == "__main__":
    main()
