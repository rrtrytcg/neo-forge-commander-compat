# Dispatch autonomous card-use evidence

2026-10-17. Four-player native `Match.startGame`, seeds 96011/96012, profile on/off controls,
20-second observation budget. A read-only event subscriber records the AI's normal selected spells
and actual target state before and after native resolution.

| Profile | Seed | Paid casts | Resolved target exiles | Indestructible targets | Clock reached |
| --- | --- | --- | --- | --- | --- |
| on | 96011 | 9 | 9 | 8 | yes |
| on | 96012 | 7 | 7 | 7 | no |
| off | 96011 | 0 | 0 | 0 | yes |
| off | 96012 | 0 | 0 | 0 | yes |

Accepted total: **16 paid, resolved casts and actual exiles, including 15 indestructible creatures**.
Every accepted cast had at least three own artifacts at selection and metalcraft at resolution.
Both off controls contain zero Dispatch casts. There are no pending actions or late observer events
in these accepted records. Clock stops are recorded separately; winners are not interpreted.

The target was initially untapped in all 16 custom-deck witnesses. The 61-check native fixture gate
separately verifies normal selection and original resolution against tapped creatures, attacker and
blocker priority, protection/hexproof/shroud, ward, taxes and losing metalcraft in response.

## Deck design

Actor: 12 Dispatch, 16 Darksteel Relic, 12 Steel Wall, 20 Plains.
Each of three opponents: 12 Darksteel Myr, 16 Goblin Brigand, 32 Mountain.

Cheap artifacts make metalcraft observable through normal AI play. Opponents offer ordinary creatures
and indestructible targets that must actually be exiled. These repeated-card custom decks are
deliberately nonstandard; they do not establish Commander-precon use or deck-construction validity.
There are no forced actions, opening hands, draws, phases, targets, payments or combat assignments.

## Evidence gate

- Pair each recorded Dispatch cast with its non-fizzled native resolution and source/target IDs.
- Require one initially legal opposing creature on the battlefield, native metalcraft and at least
  three artifacts when cast, plus metalcraft at resolution.
- Verify the same target ID is actually in exile and absent from the battlefield after resolution.
- Match each observed cast's target ID to the ordinary native cast log, preceded by a Plains paying W.
- Revalidate candidate, profile, source, native fixtures, observer, runner, decks, log and summary hashes.
- Exclude observer events after the clock marker; incomplete final actions never count as resolutions.

Example: seed 96011, turn 6, three artifacts, native Dispatch targets and exiles Goblin Brigand.
On turn 14, five artifacts, it targets and exiles indestructible Darksteel Myr. Seed 96012 provides
seven further independent paid/resolved exiles of Darksteel Myr.

Native checks, all three regression suites and the accepted probes use the same private candidate.
Full before/after records and hashes are in [card-use-evidence.json](card-use-evidence.json).
These establish the documented bounded decisions and autonomous card use, not matchup strength.
