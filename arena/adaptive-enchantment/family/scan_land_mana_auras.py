"""Reproducible, read-only inventory of land-capable mana Auras in this Forge checkout."""
from pathlib import Path
import hashlib
import json
import re

REPO = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent

def fields(text):
    return dict(part.strip().split("$", 1) for part in text.split("|") if "$" in part)

def scan():
    result = []
    for path in sorted((REPO / "forge-gui/res/cardsfolder").rglob("*.txt")):
        text = path.read_text(encoding="utf-8", errors="replace")
        if not re.search(r"^Types:.*\bAura\b", text, re.M):
            continue
        restrictions = re.findall(r"^K:Enchant:(.*)", text, re.M)
        land = any(re.search(r"(?:^|[, :])(?:Land|Forest|Island|Plains|Swamp|Mountain)(?:\b|$)", s)
                   for s in restrictions)
        permanent = any(s.split(":")[0] == "Permanent" for s in restrictions)
        if not (land or permanent):
            continue
        mechanics = [l for l in text.splitlines() if l.startswith(("A:", "T:", "S:", "R:", "SVar:"))]
        groups = []
        if any(re.search(r"(?:AB|DB)\$ Mana(?:[ |]|$)", l) for l in mechanics):
            if any("Mode$ TapsForMana" in l for l in mechanics):
                if any("Amount$ X" in l for l in mechanics):
                    groups.append("variable-trigger")
                elif any("Produced$ Chosen" in l for l in mechanics):
                    groups.append("chosen-color-trigger")
                elif any("Produced$ Combo Any" in l for l in mechanics):
                    groups.append("combination-trigger")
                elif any("Produced$ Any" in l for l in mechanics):
                    groups.append("flexible-trigger")
                else:
                    groups.append("fixed-trigger")
            elif any("AddAbility$" in l for l in mechanics):
                if any("RestrictValid$" in l for l in mechanics):
                    groups.append("restricted-grant")
                elif any("ValidTgts$ Player" in l for l in mechanics):
                    groups.append("targeted-grant")
                elif any("RemoveAllAbilities$" in l for l in mechanics):
                    groups.append("transform-grant")
                else:
                    groups.append("native-grant")
            else:
                groups.append("phase-mana")
        if not groups and any(token.strip() in {"AllBasicLandType", "ChosenType", "Mountain", "Island", "Swamp", "Plains", "Forest"}
                              for l in mechanics for value in re.findall(r"AddType\$ ([^|]+)", l)
                              for token in value.split("&")):
            groups.append("land-type-change")
        if any("Event$ ProduceMana" in l or "DB$ ReplaceMana" in l for l in mechanics):
            groups.append("mana-replacement")
        if not groups:
            continue
        result.append({
            "name": re.search(r"^Name:(.*)", text, re.M)[1].strip(),
            "path": path.relative_to(REPO).as_posix(),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "groups": groups,
            "enchant": restrictions,
            "ai_flags": re.findall(r"^AI:(.*)", text, re.M),
            "script": text,
        })
    return result

if __name__ == "__main__":
    cards = scan()
    (OUT / "mana-family.json").write_text(json.dumps(cards, indent=2), encoding="utf-8")
    print(f"Found {len(cards)} land-capable mana Auras")
    for group in sorted({g for c in cards for g in c["groups"]}):
        print(group + ": " + "; ".join(c["name"] for c in cards if group in c["groups"]))
