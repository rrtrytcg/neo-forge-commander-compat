"""Derive local-only Commander smoke decks from the untouched Adaptive precon."""
from pathlib import Path
import hashlib
import json
import re
REPO = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
source = REPO / "forge-gui/res/quest/commanderprecons/Adaptive Enchantment [C18] [2018].dck"
replacements = {
    "Arixmethes, Slumbering Isle": "Market Festival",
    "Empyrial Storm": "Utopia Sprawl",
    "Genesis Storm": "Shimmerwilds Growth",
    "Hydra Omnivore": "Trace of Abundance",
    "Martial Coup": "Glittering Frost",
    "Phyrexian Rebirth": "Verdant Haven",
    "Loyal Unicorn": "Wolfwillow Haven",
    "Loyal Drake": "Abundant Growth",
    "Loyal Guardian": "Gift of Paradise",
    "Daxos of Meletis": "Sheltered Aerie",
    "Bant Charm": "Urban Utopia",
}
section = ""
lines = []
seen = set()
for line in source.read_text().splitlines():
    if line.startswith("["):
        section = line
    if line.startswith("Name="):
        line = "Name=Land Mana Aura Family Smoke"
    match = re.match(r"(\d+) ([^|]+)(?:\||$)", line)
    if match:
        count, name = match.groups()
        if section == "[Commander]":
            line = "1 Tuvasa the Sunlit"
        elif section == "[Main]":
            if name == "Tuvasa the Sunlit":
                line = "1 Estrid, the Masked"
            elif name in replacements:
                seen.add(name)
                line = "1 " + replacements[name]
    lines.append(line)
assert seen == set(replacements)
counts = [int(re.match(r"(\d+) ", l)[1]) for l in lines if re.match(r"\d+ ", l)]
assert sum(counts) == 100
out = HERE / "decks"
out.mkdir(exist_ok=True)
for label in ["A", "B"]:
    (out / f"FamilySmoke_{label}.dck").write_text("\n".join(lines).rstrip() + "\n")
(HERE / "smoke-deck-provenance.json").write_text(json.dumps({
    "source": str(source.relative_to(REPO)),
    "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
    "commander": "Tuvasa the Sunlit", "cards": sum(counts), "replacements": replacements,
}, indent=2))
print("Created two local 100-card Commander decks; upstream deck unchanged.")
