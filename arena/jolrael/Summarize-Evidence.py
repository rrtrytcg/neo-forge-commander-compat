"""Verify sealed gate inputs and paired native log witnesses; write the durable lab summary."""
import hashlib
import json
import re
from pathlib import Path

LAB = Path(__file__).resolve().parent
ROOT = LAB.parents[1]


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest().upper()


def relative(path):
    return str(Path(path).resolve().relative_to(ROOT)).replace("\\", "/")


gate_path = LAB / "target/final-1.0.27/provenance.json"
gate = read(gate_path)
assert gate["engine"] == "NeoForge 1.0.27" and not gate["baseline"]
assert sha(gate["candidate"]) == gate["candidateSha256"]
for item in gate["files"] + gate["results"]:
    path = item.get("path", item.get("log"))
    assert sha(path) == item["sha256"], path
base = read(Path(gate["base"]) / "provenance.json")
prior = {r["suite"]: r["checks"] for r in base["results"]}
current = {r["suite"]: r["checks"] for r in gate["results"]}
assert len(prior) == 49 and sum(prior.values()) == 5317
assert all(current[name] == count for name, count in prior.items())
assert len(current) == 50 and current["JolraelTests"] == 71
baseline_path = LAB / "target/baseline-1.0.27-baseline/provenance.json"
baseline = read(baseline_path)
assert baseline["baseline"] and baseline["results"][0]["checks"] == 2
for item in baseline["files"] + baseline["results"]:
    assert sha(item.get("path", item.get("log"))) == item["sha256"]

games = []
for seed in (121011, 121012):
    for mode in ("off", "on"):
        summary_path = LAB / f"target/smoke/{mode}-{seed}/summary.json"
        smoke = read(summary_path)
        assert smoke["candidateSha256"] == gate["candidateSha256"]
        assert smoke["exceptions"] == 0 and smoke["exit"] == 0
        assert sha(smoke["log"]) == smoke["logSha256"]
        lines = Path(smoke["log"]).read_text(encoding="utf-8-sig").splitlines()
        casts = [i for i, line in enumerate(lines) if re.search(r"^Add To Stack: .*cast Jolrael, Mwonvuli Recluse", line)]
        activations = [i for i, line in enumerate(lines) if re.search(r"^Add To Stack: .*activated Jolrael, Mwonvuli Recluse", line)]
        cats = [i for i, line in enumerate(lines) if line.startswith("Resolve Stack: Whenever you draw your second card each turn, create a 2/2 green Cat creature token.")]
        # Recount derived Cat metadata from the unchanged raw log. Native trigger text has no source name.
        smoke["counts"]["cats"] = len(cats)
        smoke["examples"]["cats"] = [lines[i] for i in cats[:5]]
        smoke["scope"] = "Card-use exposure with purpose-made repeated-card two-player decks; no strength claim"
        summary_path.write_text(json.dumps(smoke, indent=2) + "\n", encoding="utf-8")
        witnesses = []
        for index in activations:
            phase = next(line for line in reversed(lines[:index]) if line.startswith("Phase:"))
            turn = next(line for line in reversed(lines[:index]) if line.startswith("Turn: Turn "))
            assert "Ai(1)" in phase and phase.endswith("Beginning of Combat Step"), (seed, index + 1)
            assert all(line.startswith("Mana: Forest ") for line in lines[index - 6:index]), (seed, index + 1)
            assert lines[index + 1].startswith("Resolve Stack: Jolrael, Mwonvuli Recluse (")
            end = next((i for i in range(index + 1, len(lines)) if lines[i].startswith("Turn: Turn ")), len(lines))
            attack = next(i for i in range(index + 1, end) if lines[i].startswith("Combat: Ai(1)") and " to attack " in lines[i])
            assert len(re.findall(r"(?:Grizzly Bears|Jolrael, Mwonvuli Recluse|Cat Token) \(\d+\)", lines[attack])) >= 2
            damage = [int(match.group(1)) for line in lines[attack:end]
                      if (match := re.match(r"Damage: (?:Grizzly Bears|Jolrael, Mwonvuli Recluse|Cat Token) \(\d+\) deals (\d+) (?:combat )?damage", line))]
            assert damage and max(damage) > 2, (seed, index + 1, "missing boosted damage")
            witnesses.append({"turn": turn, "activationLine": index + 1, "resolutionLine": index + 2,
                              "attackLine": attack + 1, "forestManaPayments": 6, "damageValues": sorted(set(damage))})
        if mode == "off":
            assert not casts and not activations, (seed, mode)
        else:
            assert casts and activations, (seed, mode, "missing use witness")
        games.append({
            "seed": seed, "mode": mode, "clockSeconds": smoke["clockSeconds"],
            "casts": len(casts), "activations": len(activations), "nativeCatTriggers": len(cats),
            "exceptions": smoke["exceptions"], "lastTurn": smoke["lastTurn"],
            "clockLimited": any("Stopping slow match" in line for line in lines),
            "log": relative(smoke["log"]), "logSha256": smoke["logSha256"],
            "summary": relative(summary_path),
            "summarySha256": sha(summary_path), "activationWitnesses": witnesses,
            "castLines": [i + 1 for i in casts], "activationLines": [i + 1 for i in activations],
        })

coverage_path = ROOT / "arena/coverage/coverage.json"
coverage = read(coverage_path)
assert coverage["inventory"]["decksWithZeroBlocked"] == 74
assert coverage["cards"]["mechanicallyUnlocked"] == 88 and coverage["cards"]["strategicallyVerified"] == 78
cleared = ["MKC - Deep Clue Sea", "SLD - From Cute to Brute"]
assert all(deck in coverage["inventory"]["zeroBlockedDecks"] for deck in cleared)

output = {
    "profile": "jolrael", "engine": gate["engine"],
    "candidateSha256": gate["candidateSha256"],
    "gate": relative(gate_path), "gateSha256": sha(gate_path),
    "baseline": relative(baseline_path), "baselineChecks": 2,
    "nativeChecks": sum(current.values()), "suites": len(current),
    "priorChecks": sum(prior.values()), "priorCountsUnchanged": True, "newChecks": current["JolraelTests"],
    "decks": [{"path": relative(p), "sha256": sha(p)} for p in sorted((LAB / "decks").glob("*.dck"))],
    "games": games,
    "coverage": {"path": relative(coverage_path), "sha256": sha(coverage_path), "decksWithZeroBlocked": 74,
                 "mechanicallyUnlocked": 88, "verified": 78, "newlyClearDecks": cleared},
    "scope": "Native correctness and autonomous card-use evidence from repeated-card two-player decks; no strength claim.",
}
(LAB / "verification-summary.json").write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
print(f"Verified {output['nativeChecks']} checks / {output['suites']} suites and {len(games)} paired exposure observations")
for game in games:
    print(game["seed"], game["mode"], "casts", game["casts"], "activations", game["activations"], "cats", game["nativeCatTriggers"], "clockLimited", game["clockLimited"])
