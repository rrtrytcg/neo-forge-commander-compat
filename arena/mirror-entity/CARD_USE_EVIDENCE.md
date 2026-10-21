# Mirror Entity offensive and defensive use

2026-10-17. Existing native fixtures: **48 Entity checks + 69 Decree regression checks PASS**.
Native custom-deck observations provide these autonomous activation/resolution witnesses:

| Setup | Seed | Profile on: offense | Profile on: defense | Profile off: total activations |
| --- | --- | --- | --- | --- |
| Offense | 95881 | 7 | 0 | 0 |
| Offense | 95882 | 7 | 0 | 0 |
| Defense | 95881 | 0 | 14 | 0 |
| Defense | 95882 | 3 | 5 | 0 |

The defensive setup can legitimately produce offensive opportunities when multiple Entities become
ready. The observer classifies the **actual native phase**, not the intended deck label. Total:
**17 offensive and 19 defensive activations**, all resolved. Chosen X ranges from 3 to 10 on offense,
and 3 to 18 on defense. All 36 have matching native Plains mana events for the chosen X.

## Custom decks

- Offensive actor: 4 Mirror Entity, 20 Eager Cadet, 36 Plains.
- Opposing defenders: 36 Wall of Wood, 24 Forest, on each of three opponent seats.
- Defensive actor: 4 Mirror Entity, 20 Wall of Hope, 36 Plains.
- Opposing attackers: 36 Goblin Brigand, 24 Mountain, on each of three opponent seats.

Goblin Brigand's native must-attack rule creates blocking opportunities. Wall of Hope is a 0/3 defender
that can safely block a 2/2 before being improved. Those are card rules, not forced engine actions.
The actor plays and activates Entity through ordinary native casting/selection/payment. No hands,
blockers, phases, draws, target selections or payment are injected. Deck-construction legality is
deliberately outside this focused probe.

## Actual-state examples

**Offense, seed 95881, native turn 14, our beginning of combat:** the AI chooses X=3 and pays three
Plains mana. Four attack-capable creatures gain power. Eager Cadets change from **1/1 to 3/3**, and
the recorded Elf/Goblin type checks change from false to true. Mirror Entity also becomes 3/3.

**Defense, seed 95881, native turn 17, opponent's declare-blockers step:** the AI chooses X=3 and pays
three Plains mana. Three already-assigned Wall of Hope blockers change from **0/3 to 3/3** and gain
all creature types. The actual blocking assignments remain present after resolution. Every friendly
creature has nondecreasing power/toughness.

The full before/after state for all 36 resolutions is in `card-use-evidence.json`. The evidence gate
checks at least two improved relevant participants per activation, X>=3, native cost traces, no
friendly power/toughness reduction, positive toughness and native type changes. Native fixtures
separately establish end-of-turn restoration and the more complex counter/anthem safety cases.

## Scope and provenance

Four-player native Constructed matches use Default AI and its Classic policies. Each of the two fixed
seeds runs both setups with the profile on and off, for eight 20-second observations. Completion and
winners are never acceptance criteria or used as strength evidence. Clock stops and shutdown
diagnostics are retained. Observer events after a clock-stop marker are excluded from acceptance.

`MirrorEntityProbe.java` is an arena-only launcher plus a **read-only event subscriber**, not an AI
replacement. It observes native spell-ability activation and resolution events. The ordinary game log
does not expose enough X/P/T information, so this subscriber records those facts alongside it.

All probes use candidate SHA-256
`35F2DDCC9A5E6C041487EB1C1D89763362EFCD2A505BEDEDE6AFA74D686715AE`, matching the Entity native gate and
Decree regression. Per-run summaries retain exact commands and hashes for candidate, observer,
runner, custom decks, profile and raw log. Full logs and private jars remain under `target/card-use/`.
No original scripts/decks, default profile, installed app or unrelated work was modified.
