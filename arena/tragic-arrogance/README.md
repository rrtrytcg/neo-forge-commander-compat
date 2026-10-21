# Tragic Arrogance opt-in survivor policy

Activate with `--ai-profile=tragic-arrogance`. Card scripts, preset decks, default AI, existing profiles
and the installed application remain unchanged. All generated runtime files stay in the arena.

## Rules

- For us and allies, maximize the total value of distinct nonland permanents saved by the choices.
- For each opponent, minimize that saved value. A single multi-type permanent may fill several slots.
- Use Forge's native `ComputerUtilCard.evaluatePermanentList` metric: mana value plus one per permanent.
  This is a simple permanent-value policy, not a combat or combo evaluator. On equal value, save more
  vulnerable permanents on the friendly side and fewer on the opposing side.
- Lands and permanents that cannot be sacrificed already survive; choosing them adds no marginal saved
  value. Use the native sacrifice-legality check with this spell's sacrifice ability as the cause.
- Cast only if opponents would lose at least two more permanents than our side and strictly more total
  native permanent value. Native timing, costs, taxes and restrictions still apply.
- Recompute plans during resolution. Keep every mandatory native category choice legal and let the
  original script execute sacrifices, death triggers and remembered-card cleanup.

Indestructible does not prevent sacrifice. A land with another card type can satisfy a choice while
remaining exempt from the nonland sacrifice. Missing categories require no choice.

The cast gate scores the current board's projected sacrifices. It does not model future opponent
responses, death-trigger chains, recursion or the strategic effect of particular permanent abilities.
Verification covers these documented bounded rules and card use, not general matchup strength.

## Implementation

`TragicArroganceAi` uses a 16-state dynamic program: assign each card a nonempty subset of its four
qualifying categories once. Each state tracks which categories are covered, saved value and distinct
saved count. This gives an exact result for this metric without a large Cartesian search.

Forge's native choice callback does not identify its category. A small cursor follows the original
script's fixed Artifact/Creature/Enchantment/Planeswalker order, skipping empty categories and allowing
the same card to be returned more than once. The cursor is removed when the player's choices finish,
reset on a different resolving context/player or casting reevaluation, and uses weak references so
aborted games are not retained. Unexpected callback options fall back to a legal native best/worst
choice. No evaluation RNG or cast-time survivor commitment is introduced.

`RepeatEachAi` branches into the normal base template and bounded cast gate only for this profile.
`ChooseCardAi` branches into the coordinated survivor choice only for the same profile hint.
Original RepeatEach and ChooseCard behavior remains available unchanged without the profile.

## Native verification

`Check-TragicArrogance.ps1 -Label final` passes **132 native checks**, including:

- independent exhaustive Cartesian ground truth on seven small boards for friendly and enemy plans;
- mandatory coverage of all four categories, including absent categories and overlapping types;
- net-card and value cast gates, multiplayer counts, payment, taxes and sorcery timing;
- native selection, 3WW payment, original sacrifices, preserved lands and remembered-card cleanup;
- coordinated own/opponent choices, artifact and creature lands, indestructible and sacrifice protection;
- recomputation after a new permanent arrives before resolution, interrupted callback reset, game
  rollover through fresh fixtures, default-choice regression and admission/profile isolation.

`Check-Regressions.ps1` runs the earlier Mirror Entity, Decree of Pain, Oreskos Explorer, Dispatch and
Slaughter the Strong suites against the same private candidate. The offline parent/core/game/AI Maven
reactor package passes with zero Checkstyle violations. Generic Maven tests are skipped; these explicit
native fixtures supply the behavior gate.

The private candidate overlays the new planner and both modified AI classes, plus the already committed
Dispatch/Tap and ChangeZone classes, onto an arena engine snapshot. This preserves the recent fixes even
when the shared desktop jar is older than source. Unchanged sources are verification inputs, not edits.

## Reproduce

```powershell
./arena/tragic-arrogance/Check-TragicArrogance.ps1 -Label final
./arena/tragic-arrogance/Check-Regressions.ps1
./arena/tragic-arrogance/Run-CardUseProbe.ps1 -ProfileMode on -Seed 96121
./arena/tragic-arrogance/Run-CardUseProbe.ps1 -ProfileMode off -Seed 96121
./arena/tragic-arrogance/Run-CardUseProbe.ps1 -ProfileMode on -Seed 96122
./arena/tragic-arrogance/Run-CardUseProbe.ps1 -ProfileMode off -Seed 96122
./arena/tragic-arrogance/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
```

The native Match probes have read-only event subscribers. Normal AI selects actions, cards, choices,
targets, mana and combat. Repeated-card custom decks create opportunities; they are not Commander
precons or deck-construction validation. Completion and winners are not acceptance criteria for these
20-second observations. Events after a clock stop are excluded.

See [CARD_USE_EVIDENCE.md](CARD_USE_EVIDENCE.md), [card-use-evidence.json](card-use-evidence.json), and
[verification-summary.json](verification-summary.json). Raw logs, classes and private jars are ignored
under `target/`.
