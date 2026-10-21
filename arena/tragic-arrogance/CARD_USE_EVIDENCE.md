# Tragic Arrogance autonomous card-use evidence

2026-10-17. Four-player native `Match.startGame`, seeds 96121/96122, profile on/off controls,
20-second observation budget per run. The event observer only reads casts, boards, native sacrifices
and resolutions; it never calls the planner or chooses an action.

| Profile | Seed | Paid/resolved casts | Friendly sacrificed | Opposing sacrificed | Indestructible sacrificed |
| --- | --- | --- | --- | --- | --- |
| on | 96121 | 2 | 5 | 24 | 7 |
| on | 96122 | 2 | 1 | 13 | 5 |
| off | 96121 | 0 | 0 | 0 | 0 |
| off | 96122 | 0 | 0 | 0 | 0 |

Accepted: **4 native-paid resolutions, 37 opposing and 6 friendly sacrifices**, including
**12 indestructible permanents**. Every survivor set matches an independent exhaustive oracle for the
documented native value/count metric. Both off controls contain zero Tragic Arrogance casts.

All four observations finished within the clock. There are no pending or late observer actions in the
accepted records. Completion and printed winners are not acceptance criteria or matchup evidence.

## Per-cast margins

| Seed | Turn | Friendly losses | Opposing losses | Friendly lost value | Opposing lost value |
| --- | --- | --- | --- | --- | --- |
| 96121 | 28 | 4 | 20 | 13 | 58 |
| 96121 | 45 | 1 | 4 | 1 | 14 |
| 96122 | 26 | 1 | 10 | 1 | 32 |
| 96122 | 29 | 0 | 3 | 0 | 8 |

Each satisfies both the net-two-permanents rule and the strictly greater opposing value-loss rule.
The value metric is Forge's native permanent-list evaluation: mana value plus one per permanent.

## Custom decks

Actor: 8 Tragic Arrogance, 8 Serra Angel, 8 Ornithopter, 8 Glorious Anthem,
4 Ajani, Caller of the Pride, 24 Plains.

Each of three opponents: 12 Darksteel Myr, 16 Eager Cadet, 8 Glorious Anthem, 24 Plains.

These decks expose four-category choices, multi-type overlap and indestructible sacrifice through
ordinary AI play. Repeated copies deliberately create observable opportunities; they are not valid
Commander precons or deck-construction tests. No hands, draws, actions, phases, choices or payments
are forced.

## Independent verification

`Check-CardUseEvidence.ps1` revalidates all input/log hashes and:

- pairs each cast with its non-fizzled native resolution and empty remembered-card cleanup;
- verifies five native Plains mana payments for each 3WW cast;
- enumerates category-choice combinations independently from the production 16-state algorithm;
- checks each player's actual distinct survivors against the oracle's best/worst value and count;
- checks mandatory category coverage and that lands/unsacrificable permanents remain;
- matches every removed permanent ID to a native `GameEventCardSacrificed` event;
- verifies actual opposing-versus-friendly loss margins and zero-use off controls;
- excludes events after a clock marker if a later reproduction reaches its time budget.

The **132 native fixture checks** separately cover restrictions, protection, changing the board before
resolution, callback interruption/reset and unchanged default behavior. **296 regression checks** cover
Mirror Entity, Decree of Pain, Oreskos Explorer, Dispatch and Slaughter the Strong on the same candidate.

Full snapshots and provenance are in [card-use-evidence.json](card-use-evidence.json). This is evidence
of bounded-policy correctness and autonomous card use, not Commander-precon performance or strength.
