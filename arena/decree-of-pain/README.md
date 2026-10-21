# Decree of Pain opt-in rules

Profile: `--ai-profile=decree-of-pain`. The original card script, preset decks, default AI and installed
NeoForge app are unchanged. The profile opts in only Decree of Pain, independently of Abzan, Adaptive
and Ahoy. Its default Commander matchup uses the original Squirreled Away and Endless Punishment decks.

## Rules

- Cast for 6BB only when expected opposing creature casualties, across all opponents, exceed ours by
  at least two. Indestructible and shield-counter survivors do not count as casualties. Regeneration
  cannot save a creature from the full spell; other uncertain destruction replacements cause a hold.
- Cycle for 3BB only when the native current creature characteristics and marked damage predict at
  least two more opposing casualties than ours after -2/-2. Zero toughness bypasses indestructible,
  shield counters and regeneration. Indestructible protects against lethal marked damage at positive
  toughness; shield counters do not protect against that state-based death.
- Prefer cycling when both modes qualify and native legality and payment checks permit cycling.
  Prohibitions and activation taxes must not suppress an otherwise qualifying full spell.
- Otherwise hold. No cycling solely to replace the card, and no emergency override of the safety gates.
- Casting requires library size at least the number of all battlefield creatures plus one. Include
  tokens and protected/phased-out survivors in this conservative upper bound. Cycling requires two
  library cards. Both leave a spare beyond the predicted draw.

Public active draw replacements and triggered ability trees containing additional draws or mill cause
a conservative hold, even when their applicability is uncertain. This protects the simple bound from
extra death-triggered draws; it can also decline harmless situations. A cycling casualty with static
abilities causes a hold because losing a lord can change other creatures' toughness. The policy does
not solve cascading static effects or future opponent responses.

## Native engine integration

`DecreeOfPainAi` supplies the profile-gated decision to `DestroyAllAi` and `DrawAi`. The cycling draw
AI's generic self-discard value veto is replaced only for this opted-in cycling decision by native cost
feasibility. Native mana costs, activation taxes, self-discard, timing, activation prohibitions,
target/zone rules, destruction, state-based actions, draw and trigger order remain authoritative.
The mandatory cycling trigger is never made optional. Prediction uses copies for the -2/-2 check;
there is no new policy RNG or opposing hidden-card inspection.

## Verification

Final arena package: **PASS**, zero Checkstyle violations. Final native gate: **69/69 PASS**;
Ahoy regression: **77/77 PASS**. The final Commander smoke (seed 95551, 180-second limit) completed
normally on turn **10** in **7.750 seconds**, with no Decree cast or cycling activation. The native
suite separately demonstrates normal AI selection and actual engine payment/resolution of both modes.
The final smoke, native suite and Ahoy regression used identical private candidate-jar hashes.

See `verification-summary.json` for final results, source/candidate/script/deck hashes and the explicit
coverage status. Native fixtures test policy entry points, normal AI ability selection, actual payment
and full resolution of both modes. Full logs and private candidate jars are under ignored `target/`.
Runners check isolated `arena/runtime` paths and snapshot the jar to avoid concurrent build changes.

The first three fixed Commander seeds (95551, 95552, 95553) completed normally on turn 10 in 8.071,
10.313 and 8.844 seconds respectively, but none used Decree. Those earlier runs predate the final
nested-trigger safety check and are historical runtime evidence only. The final candidate is checked
separately in the summary. Those smokes did not establish autonomous Decree use. The initial unlocked
status has since been promoted using the focused custom-deck evidence described below; a cast in an
original Commander precon remains unobserved and is not required for this card-use gate.

The refreshed coverage report has **21 decks with zero remaining blockers**, including Squirreled Away
and Endless Punishment: 28 cards unlocked in total, with **17 verified and 11 unlocked only**. Beast
Within's separate committed work accounts for the prior increase to 19 decks; Decree adds two.
These are the original implementation-stage totals; the current report is `arena/coverage/COVERAGE.md`.

## Custom-deck card-use evidence, 2026-10-17

See `CARD_USE_EVIDENCE.md` and `card-use-evidence.json`. The same private jar that passed 69 native
Decree checks and 77 Ahoy regression checks runs a Decree deck (4 Decrees, 56 Swamps) against three
copies of a target deck. The casting probe uses 4/5 Leatherback Baloths, which survive -2/-2. The
cycling probe uses 2/2 Grizzly Bears. No actions, hands, phases or draws are forced.

Two fixed seeds, each profile on and off, provide eight 20-second observations. The acceptance gate
is autonomous native card use, not completion, a winner or deck-construction legality. A clock stop
and its shutdown interruption are recorded rather than treated as failed earlier card-use evidence.
There are no AI implementation, profile, original script, preset-deck or installed-app changes here.

The native correctness gate plus both casting and cycling witnesses promote Decree to **verified**
for the documented bounded rules. This is not a strength claim or a Commander-precon use witness.

```powershell
./arena/decree-of-pain/Run-CardUseProbe.ps1 -Mode cast -ProfileMode on -Seed 95771
./arena/decree-of-pain/Run-CardUseProbe.ps1 -Mode cycle -ProfileMode on -Seed 95771
# Repeat with -ProfileMode off, and with seed 95772.
```

## Reproduce

After building the arena desktop jar, from the source checkout:

```powershell
./arena/decree-of-pain/Check-DecreeOfPain.ps1 -Label final
./arena/ahoy-mateys/Check-AhoyMateys.ps1 -Label decree-final-regression
./arena/decree-of-pain/Run-CommanderSmoke.ps1 -Label commander-final
```

Maven packaging uses `-DskipTests`; the explicit native fixture runners above are the test gates.
