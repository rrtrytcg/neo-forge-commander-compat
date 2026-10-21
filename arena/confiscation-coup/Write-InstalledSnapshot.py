#!/usr/bin/env python3
"""Capture or verify the current installed files around a private NeoForge 1.0.27 gate."""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "arena/version-compat-1.0.27/target/preserved-baseline.json"
SNAPSHOT = ROOT / "arena/confiscation-coup/target/installed-snapshot.json"


def sha256(path: str) -> str:
    return hashlib.sha256(Path(path).read_bytes()).hexdigest().upper()


def main() -> None:
    files = json.loads(SOURCE.read_text(encoding="utf-8"))["installed"]
    if len(files) != 21729:
        raise SystemExit(f"Unexpected installed-file inventory: {len(files)}")
    if sys.argv[1] == "capture":
        snapshot = {"installed": [{"path": item["path"], "sha256": sha256(item["path"])} for item in files]}
        SNAPSHOT.parent.mkdir(parents=True, exist_ok=True)
        SNAPSHOT.write_text(json.dumps(snapshot, indent=2) + "\n", encoding="utf-8")
        changed_from_1_0_27 = sum(a["sha256"] != b["sha256"] for a, b in zip(files, snapshot["installed"]))
        print(f"Captured {len(files)} installed hashes; {changed_from_1_0_27} differ from the 1.0.27 baseline")
    elif sys.argv[1] == "verify":
        snapshot = json.loads(SNAPSHOT.read_text(encoding="utf-8"))["installed"]
        changed = [item["path"] for item in snapshot if sha256(item["path"]) != item["sha256"]]
        print(f"{len(snapshot)} installed hashes checked after the gate; {len(changed)} changed")
        result = {"installedHashesChecked": len(snapshot), "installedHashesChangedDuringGate": len(changed),
                  "snapshotSha256": hashlib.sha256(SNAPSHOT.read_bytes()).hexdigest().upper()}
        SNAPSHOT.with_name("installed-snapshot-verification.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        if changed:
            print("\n".join(changed))
            raise SystemExit(1)
    else:
        raise SystemExit("Usage: Write-InstalledSnapshot.py capture|verify")


if __name__ == "__main__":
    main()
