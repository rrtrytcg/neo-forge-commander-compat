"""Verify the sealed candidate and paired raw-log card-use/payment witnesses."""
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
assert len(prior) == 50 and sum(prior.values()) == 5388
assert all(current[name] == count for name, count in prior.items())
assert len(current) == 51 and current["MoonlightBargainTests"] == 105
baseline_path = LAB / "target/verified-1.0.27-baseline/provenance.json"
baseline = read(baseline_path)
assert baseline["baseline"] and baseline["results"][0]["checks"] == 5
for item in baseline["files"] + baseline["results"]:
    assert sha(item.get("path", item.get("log"))) == item["sha256"]

games = []
for seed in (122011, 122012):
    for mode in ("off", "on"):
        summary_path = LAB / f"target/smoke/{mode}-{seed}/summary.json"
        smoke = read(summary_path)
        assert smoke["candidateSha256"] == gate["candidateSha256"]
        assert smoke["exceptions"] == 0 and smoke["exit"] == 0
        assert sha(smoke["log"]) == smoke["logSha256"]
        smoke["scope"] = "Card-use exposure with purpose-made repeated-card two-player decks; no strength claim"
        summary_path.write_text(json.dumps(smoke, indent=2) + "\n", encoding="utf-8")
        lines = Path(smoke["log"]).read_text(encoding="utf-8-sig").splitlines()
        casts = [i for i, line in enumerate(lines) if re.search(r"^Add To Stack: .*cast Moonlight Bargain", line)]
        resolutions = [i for i, line in enumerate(lines) if line.startswith("Resolve Stack: Moonlight Bargain")]
        assert len(casts) == len(resolutions)
        witnesses = []
        for cast, resolved in zip(casts, resolutions):
            assert cast < resolved
            phase = next(line for line in reversed(lines[:cast]) if line.startswith("Phase:"))
            assert "Ai(2)" in phase and phase.endswith("End step"), (seed, cast + 1, phase)
            assert all(line.startswith("Mana: Swamp ") for line in lines[cast - 5:cast]), (seed, cast + 1)
            payments = []
            # Native logging emits the Resolve Stack marker after the effect's life payments.
            for i in range(cast + 1, resolved):
                match = re.search(r"^Life:.*Ai\(1\).* (\d+) > (\d+)$", lines[i])
                if match:
                    before, after = map(int, match.groups())
                    assert before - after == 2 and after >= 10, (seed, i + 1, before, after)
                    payments.append({"line": i + 1, "before": before, "after": after})
            assert len(payments) <= 3
            if payments:
                assert payments[0]["before"] >= 14
                assert all(payments[i]["before"] == payments[i - 1]["after"] for i in range(1, len(payments)))
            next_turn = next((line for line in lines[resolved + 1:] if line.startswith("Turn:")), None)
            if next_turn:
                assert "Ai(1)" in next_turn, (seed, cast + 1, next_turn)
            witnesses.append({"castLine": cast + 1, "resolutionLine": resolved + 1, "swampManaPayments": 5,
                              "lifePaid": 2 * len(payments), "paidKeepOffers": len(payments), "payments": payments})
        if mode == "off":
            assert not casts and not resolutions
        else:
            assert casts and any(w["paidKeepOffers"] for w in witnesses), (seed, "missing paid-use witness")
        games.append({"seed": seed, "mode": mode, "clockSeconds": smoke["clockSeconds"],
                      "casts": len(casts), "resolutions": len(resolutions),
                      "lifePaid": sum(w["lifePaid"] for w in witnesses),
                      "paidKeepOffers": sum(w["paidKeepOffers"] for w in witnesses),
                      "exceptions": smoke["exceptions"], "lastTurn": smoke["lastTurn"],
                      "clockLimited": any("Stopping slow match" in line for line in lines),
                      "log": relative(smoke["log"]), "logSha256": smoke["logSha256"],
                      "summary": relative(summary_path), "summarySha256": sha(summary_path), "witnesses": witnesses})

output = {"profile": "moonlight-bargain", "engine": gate["engine"],
          "candidateSha256": gate["candidateSha256"], "gate": relative(gate_path), "gateSha256": sha(gate_path),
          "baseline": relative(baseline_path), "baselineChecks": 5,
          "nativeChecks": sum(current.values()), "suites": len(current), "newChecks": 105,
          "priorChecks": sum(prior.values()), "priorCountsUnchanged": True,
          "decks": [{"path": relative(p), "sha256": sha(p)} for p in sorted((LAB / "decks").glob("*.dck"))],
          "games": games,
          "scope": "Native correctness and card-use/payment evidence from repeated-card two-player exposure decks; no strength claim.",
          "limits": "Native match logs omit library-to-hand/graveyard movements. Exact keep choices and zones, cleanup and no-draw semantics are established by native fixtures."}
(LAB / "verification-summary.json").write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
print(f"Verified {output['nativeChecks']} checks / {output['suites']} suites and {len(games)} exposure observations")
for game in games:
    print(game["seed"], game["mode"], "casts", game["casts"], "resolved", game["resolutions"],
          "paid keeps", game["paidKeepOffers"], "life", game["lifePaid"], "clockLimited", game["clockLimited"])
