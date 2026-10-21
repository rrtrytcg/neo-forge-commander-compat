# Marshal's Anthem autonomous card-use evidence

2026-10-17. Four-player native `Match.startGame`, seeds 96231/96232, kicked and unkicked setups,
profile on/off controls, 20-second observation budgets. The subscriber only reads native events;
it never evaluates the policy, selects a card, or changes gameplay.

| Setup | Profile | Seed | Paid/resolved casts | Chosen kicks | Actual returned creatures |
| --- | --- | --- | --- | --- | --- |
| kicked | on | 96231 | 1 | 1 | 1 |
| kicked | on | 96232 | 4 | 1, 2, 2, 1 | 6 |
| kicked | off | 96231 | 0 | none | 0 |
| kicked | off | 96232 | 0 | none | 0 |
| unkicked | on | 96231 | 3 | 0, 0, 0 | 0 |
| unkicked | on | 96232 | 3 | 0, 0, 0 | 0 |
| unkicked | off | 96231 | 0 | none | 0 |
| unkicked | off | 96232 | 0 | none | 0 |

Accepted total: **11 paid/resolved enchantment casts and original ETB triggers**: five kicked casts
return **seven Archangels**, and six unkicked casts buff existing armies without wasting mana.
Every existing creature gains exactly +1/+1 when each new Anthem resolves. All four off controls
contain zero Anthem casts. Completion and winners are not matchup or strength evidence.

## Custom decks

Kicked actor: 24 Marshal's Anthem, 16 Buried Alive, 16 Archangel, 36 Plains, 16 Swamp.
Native Buried Alive spells supply the graveyard before expensive Archangels can be cast normally.
Repeated Anthems make the subsequent native reanimation decision observable.

Unkicked actor: 8 Marshal's Anthem, 24 Eager Cadet, 28 Plains.
Each of three opponents in either setup: 36 Wall of Wood, 24 Forest.

These deliberately nonstandard repeated-card decks create opportunities without forced hands, draws,
phases, actions, targets, choices or payments. They are not Commander precons or deck-construction
validation. The accepted probe files and candidate are hashed; pilot deck changes are not mixed into
the final evidence.

## What the evidence gate checks

- Pair each native cast with its enchantment resolution and each ETB trigger with its resolution.
- Match native Plains/Swamp mana events to **2WW + N times 1W**, including enough white mana.
- Independently check the largest affordable useful kick count from the recorded graveyard, untapped
  remaining lands and mana actually spent. These decks contain ordinary one-mana lands, vanilla
  nonlegendary return targets and no tax/reduction or return-prevention effects.
- Require at least two existing own creatures for every zero-kick cast, and verify their actual
  +1/+1 change without duplication when the original enchantment enters.
- Match original ETB targets to creature IDs moving from our graveyard into play alive, with one
  actual return per paid kick in these stable setups.
- Require positive-kick returns in both reanimation seeds and pure zero-kick buffs in both buff seeds.
- Revalidate native-tested inputs, candidate, profile, observer, runner, decks, logs and summaries.
- Exclude events after any clock stop and report pending/late actions separately.

The **135 native checks** separately cover colored costs, taxes/reductions, duplicate legends,
entry restrictions, static survival after the new bonus, stale decisions, changed graveyards and
default/unrelated multikicker behavior. The **428 regression checks** cover Mirror Entity, Decree
of Pain, Oreskos Explorer, Dispatch, Slaughter the Strong and Tragic Arrogance on the same candidate.

Full native snapshots and provenance: [card-use-evidence.json](card-use-evidence.json).
This establishes the documented bounded policy and autonomous card use, not general reanimation
strategy, Commander-precon performance or strength.
