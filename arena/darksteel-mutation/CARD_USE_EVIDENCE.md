# Darksteel Mutation autonomous card-use evidence

The original card script was used in four-player native `Match.startGame` runs. A read-only event
subscriber recorded selected targets at stacking and their actual state at resolution. No hands,
draws, actions, phases, targets, mana or choices were forced. The observer never calls the policy.

| Opponent deck | Profile | Seed | Paid/resolved casts | Clock stop | Pending / late records |
| --- | --- | --- | --- | --- | --- |
| Threats | on | 96341 | 3 | yes | 0 / 0 |
| Threats | on | 96342 | 7 | yes | 0 / 0 |
| Threats | off | 96341 | 0 | yes | 0 / 0 |
| Threats | off | 96342 | 0 | yes | 0 / 0 |
| Utility | on | 96341 | 5 | yes | 0 / 0 |
| Utility | on | 96342 | 8 | yes | 0 / 0 |
| Utility | off | 96341 | 0 | yes | 0 / 0 |
| Utility | off | 96342 | 0 | yes | 0 / 0 |

**23 casts paid and resolved:** 10 targeted already-indestructible Darksteel Juggernauts with at
least two power; 13 targeted 1/1 Llanowar Elves with functional mana abilities. Every cast attached
the original Aura to the observed opposing creature, set its actual base and final stats to 0/1 in
these unbuffed decks, made it an artifact Insect with indestructible, and removed its original
abilities. All four off controls cast zero Mutations. No unexpected exception diagnostics occurred.

`Check-CardUseEvidence.ps1` independently checks the two Plains mana activations for each native cast,
matches stacking/resolution by ability/source/target IDs, rejects friendly or repeated targets,
checks actual before/after types, stats, keywords and abilities, and verifies all recorded file hashes.
It excludes observer events after the clock-stop/done marker and requires the probe jars to match
the exact native-tested candidate. The 87 native checks establish targeting policy, layers, payment
and lifecycle boundaries; the probes supply autonomous use witnesses. Prior-profile regressions pass
563 checks on the same candidate.

## Deliberately nonstandard decks

- Actor: 24 Darksteel Mutation, 12 Eager Cadet, 24 Plains.
- Each threat opponent: 24 Darksteel Juggernaut, 12 Darksteel Myr, 24 Wastes.
- Each utility opponent: 24 Llanowar Elves, 12 Royal Assassin, 16 Forest, 8 Swamp.

Repeated cards expose normal AI selection opportunities; the decks are not valid Commander decks.
Royal Assassin ability removal is covered by the native fixtures; these utility-game witnesses all
targeted Llanowar Elves. Every run used a 20-second observation clock after initialization and hit
that clock. Clock stops and any shutdown interruption are recorded in the run summaries. Printed
results/winners are not used. No completed-game, Commander-precon or strength claim is made.

See [card-use-evidence.json](card-use-evidence.json) for the paired snapshots, payment records, commands,
seeds, candidate/deck/profile/observer/log hashes and diagnostics. Raw runs are retained locally under
ignored `target/card-use/<mode>-<profile>-<seed>/`; the committed evidence carries their hashes and
the relevant observations. Reproduce with the commands in [README.md](README.md).
