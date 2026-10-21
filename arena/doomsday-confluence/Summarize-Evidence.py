"""Verify sealed gates and paired native card-use witnesses; preserve raw logs."""
import hashlib
import json
import re
from pathlib import Path

LAB = Path(__file__).resolve().parent
ROOT = LAB.parents[1]


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest().upper()


def relative(path):
    return str(Path(path).resolve().relative_to(ROOT)).replace("\\", "/")


gate_path = LAB / "target/final-1.0.27/provenance.json"
gate = read(gate_path)
assert gate["engine"] == "NeoForge 1.0.27" and not gate["baseline"]
assert sha(gate["candidate"]) == gate["candidateSha256"]
for item in gate["files"] + gate["results"]:
    assert sha(item.get("path", item.get("log"))) == item["sha256"]
prior = {r["suite"]: r["checks"] for r in read(Path(gate["base"]) / "provenance.json")["results"]}
current = {r["suite"]: r["checks"] for r in gate["results"]}
assert len(prior) == 51 and sum(prior.values()) == 5493
assert all(current[name] == count for name, count in prior.items())
assert len(current) == 52 and current["DoomsdayConfluenceTests"] == 140
baseline_path = LAB / "target/final-1.0.27-baseline/provenance.json"
baseline = read(baseline_path)
assert baseline["baseline"] and baseline["results"][0]["checks"] == 3
for item in baseline["files"] + baseline["results"]:
    assert sha(item.get("path", item.get("log"))) == item["sha256"]

games = []
for seed in (123011, 123012):
    for mode in ("off", "on"):
        summary_path = LAB / f"target/bounded/{mode}-{seed}/summary.json"
        smoke = read(summary_path)
        assert smoke["candidateSha256"] == gate["candidateSha256"]
        assert smoke["exceptions"] == 0 and smoke["exit"] == 0
        assert sha(smoke["log"]) == smoke["logSha256"]
        smoke["scope"] = "Card-use exposure with purpose-made repeated-card three-player decks; no strength claim"
        summary_path.write_text(json.dumps(smoke, indent=2) + "\n", encoding="utf-8")
        lines = Path(smoke["log"]).read_text(encoding="utf-8-sig").splitlines()
        casts = [i for i, line in enumerate(lines) if re.search(r"^Add To Stack: .*cast Doomsday Confluence", line)]
        resolutions = [i for i, line in enumerate(lines) if line.startswith("Resolve Stack: Doomsday Confluence")]
        assert len(casts) == len(resolutions)
        assert not any("Stopping slow match" in line for line in lines)
        witnesses = []
        for cast, resolved in zip(casts, resolutions):
            assert cast < resolved
            phase = next(line for line in reversed(lines[:cast]) if line.startswith("Phase:"))
            assert "Ai(1)" in phase and phase.endswith("Main phase, postcombat"), (seed, cast + 1, phase)
            description = lines[resolved]
            x = int(re.search(r"\(X=(\d+)\)$", description)[1])
            sacrifice = description.count("Each player sacrifices a nonartifact creature.")
            discard = description.count("Each opponent discards a card.")
            tokens = description.count("Create a 3/3 black Dalek artifact creature token with menace.")
            assert x > 0 and sacrifice <= 1 and discard <= 1 and sacrifice + discard + tokens == x
            mana = []
            index = cast - 1
            while index >= 0 and lines[index].startswith("Mana:"):
                mana.append({"line": index + 1, "text": lines[index]})
                index -= 1
            mana.reverse()
            assert all(re.match(r"^Mana: (Swamp|Sol Ring) ", item["text"]) for item in mana)
            produced = sum(2 if "Mana: Sol Ring " in item["text"] else 1 for item in mana)
            assert produced == 2 * x + 1, (seed, cast + 1, x, produced)
            events = [{"line": i + 1, "text": lines[i]} for i in range(cast + 1, resolved)
                      if lines[i].startswith(("Discard:", "Zone Change:"))]
            discarded = sum(item["text"].startswith("Discard:") for item in events)
            sacrificed = sum("Walking Corpse" in item["text"] and "Graveyard from Battlefield" in item["text"] for item in events)
            assert discarded >= discard and sacrificed >= sacrifice
            witnesses.append({"castLine": cast + 1, "resolutionLine": resolved + 1,
                              "phase": phase, "x": x, "sacrificeModes": sacrifice, "discardModes": discard,
                              "tokenModes": tokens, "manaProduced": produced, "manaPayments": mana,
                              "observedSacrifices": sacrificed, "observedDiscards": discarded,
                              "consequenceEvents": events, "cast": lines[cast], "resolution": description})
        if mode == "off":
            assert not casts and not resolutions
        else:
            assert casts, (seed, "missing autonomous card-use witness")
            assert all(any(w[key] > 0 for w in witnesses) for key in ("sacrificeModes", "discardModes", "tokenModes"))
        games.append({"seed": seed, "mode": mode, "clockSeconds": smoke["clockSeconds"],
                      "casts": len(casts), "resolutions": len(resolutions),
                      "exceptions": smoke["exceptions"], "lastTurn": smoke["lastTurn"], "clockLimited": False,
                      "log": relative(smoke["log"]), "logSha256": smoke["logSha256"],
                      "summary": relative(summary_path), "summarySha256": sha(summary_path), "witnesses": witnesses})

output = {"profile": "doomsday-confluence", "engine": gate["engine"],
          "candidateSha256": gate["candidateSha256"], "gate": relative(gate_path), "gateSha256": sha(gate_path),
          "baseline": relative(baseline_path), "baselineChecks": 3,
          "nativeChecks": sum(current.values()), "suites": len(current), "newChecks": 140,
          "priorChecks": sum(prior.values()), "priorCountsUnchanged": True,
          "decks": [{"path": relative(p), "sha256": sha(p)} for p in sorted((LAB / "decks").glob("*.dck"))],
          "games": games,
          "scope": "Native correctness and autonomous card-use evidence from repeated-card three-player exposure decks; no strength claim."}
output["excludedAttempt"] = {
    "reason": "Wall-only and Craw-Wurm-only opponents hit the unchanged 120-second clock in their first control game. Interrupted each batch, then used low-creature-density opponents with Underworld Dreams to finish through draw-trigger life loss. No evidence from either excluded attempt is counted.",
    "logs": [{"path": relative(p), "sha256": sha(p)}
             for folder in ("smoke", "paired") for p in sorted((LAB / f"target/{folder}").glob("*/game.log"))],
}
coverage = read(ROOT / "arena/coverage/coverage.json")
assert coverage["inventory"]["decksWithZeroBlocked"] == 76
assert "WHO - Masters of Evil" in coverage["inventory"]["zeroBlockedDecks"]
assert coverage["cards"]["mechanicallyUnlocked"] == 90 and coverage["cards"]["strategicallyVerified"] == 80
output["coverage"] = {"decksClearBefore": 75, "decksClearAfter": 76, "decks": 184,
                      "cleared": "WHO - Masters of Evil", "unlocked": 90, "verified": 80}
(LAB / "verification-summary.json").write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
print(f"Verified {output['nativeChecks']} checks / {output['suites']} suites and {len(games)} exposure observations")
