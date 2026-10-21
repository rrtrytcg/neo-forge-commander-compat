# Treacherous Terrain opt-in on NeoForge 1.0.27

Profile: `treacherous-terrain`. Extends the sealed Abundance candidate (43 suites /
4,613 checks, including Estrid). Default AI, original scripts and the installed app
remain unchanged. Utility-land edits are excluded from the production list and candidate.

## Native defect and reuse

The original script has `AI:RemoveDeck:All`; native selection excludes both modes.
A three-check baseline on the unchanged Abundance engine confirms that exclusion
and that a forced generic RepeatEach decision accepts zero opposing lands despite
insufficient mana. The generic decision has no useful-damage threshold.
Basic landcycling already has native source discard, search legality, basic-land
selection/color fixing, hand movement and shuffle. Those mechanisms are retained.

## Rules

- Normal hand cast in our second main, empty stack, for a supported opponent-lethal
  result or at least eight aggregate damage to opponents. Count each opponent
  separately, exclude allies, and do not impose target eligibility on this spell.
- Otherwise basic-landcycle for two mana in our own first/second main, empty stack,
  below six lands in play with no land in hand. Prefer an eligible, legal, funded
  full spell. First-main cycling can supply this turn's land drop.
- Native own-library basic availability and search restrictions remain the boundary;
  no library-order lookahead or opposing hidden information is added. Choosing the
  basic still uses native mana fixing. Search is not a draw; no invented draw reserve.
- Keep us and allies alive with at least five life. Hold uncertain damage/life,
  cast/mana/search/discard triggers and relevant replacements, redirection and
  prevention. Complex source modifications, extra non-mana spell costs and unsupported
  templates also hold. These are conservative deferrals, not universal combo support.
- Resolve the original spell on a hidden-information-pruned private native game with
  native mana payment for value and safety. No synthetic damage or live payment.
  Native affordability uses the existing reservation-free projection helper.
- Fresh pre-payment recheck: source instance/timestamp, profile, original mode/shape,
  window, costs, public state, value and safety. Cancellation precedes all live costs.
  Once cast/activated, subsequent responses and resolution remain fully native.
- The exact native Commander return replacement is recognized for cycling; extra or
  altered effects are not exempted. Native commander permission remains unchanged.

Hooks: scoped helper `TreacherousTerrainAi`; opt-in entries in RepeatEachAi and
ChangeZoneAi; one preflight in ComputerUtil. No generic payment or choice rewrite.

## Evidence and limits

The clean focused suite passes 234 original-script native checks. It covers exact
seven/eight damage, lethal/cannot-lose, all opponents and allies, five-life reserve,
hexproof/nontargeting, native colored/taxed spell payment, native first-main cycling,
missing basics and empty/thin library, basic color fixing, search bans/taxes,
prevention/redirection, native Commander rules, stale cancellation, interruption,
RNG/resource integrity and unchanged defaults/profile boundaries.

An early process-control mistake overlapped a focused rebuild and a regression run;
DispatchTests failed while the candidate was being rewritten. Both sessions ended,
and the entire output was archived under `target/overlapping-runs-not-evidence`.
`initial-observation.json` records that discarded attempt. Final evidence must come
from the subsequent clean sequential gate. A per-lab exclusive runtime lock now
prevents overlapping gate/probe calls. No failed-run output certifies this policy.

The clean final gate passes **4,847 native checks across 44 suites** (234 new plus
4,613 prior), all 20 deadline checks, and all **555 card-use checks** in six paired
Commander observations. The unchanged-engine native baseline passes three checks.

| Scenario / seed | Opt-in casts | Opt-in cycles | Opposing damage | Default Terrain use |
| --- | ---: | ---: | ---: | ---: |
| Cast / 115011 | 6 | 1 | 134 | 0 |
| Cast / 115012 | 5 | 1 | 82 | 0 |
| Cycle / 115021 | 4 | 2 | 134 | 0 |

All four cycles find one native basic and shuffle exactly once, without counting
as a draw. Every observed action is paired with a native resolution and no fizzles,
late events or unexpected diagnostics. Ordinary land plays occur in every run;
ordinary Centaur Courser casts occur in both cast pairs. The three default controls
hit 30 seconds; the three opt-in runs return earlier. No winner/completion claim.

Coverage: **184 decks, 47 clear, 62 mechanically unlocked = 52 verified + 10
unlocked-only**. Land's Wrath is newly clear; the other three affected decks retain
6/3/6 blockers. Four blocked occurrences are removed, leaving 311 distinct blocked
cards / 438 occurrences. No utility-land registry changes are included.

Final native totals, candidate fingerprint and measured six-run use counts are in
`verification-summary.json` and `card-use-evidence.json` after the gate/probes pass.

Paired custom Commander decks deliberately repeat cards and use an off-color Yargle
and Multani commander. These are invalid exposure decks, not normal Commander lists.
The cast deck supplies Forest/Mountain, native Sol Ring mana and ordinary Centaur Courser casting; the
cycle deck has scarce lands and uncastable black creatures to expose cycling without
spending its mana elsewhere. Opponents have Forest and Wall of Wood. Deck composition
changes opportunities only: no policy exceptions, forced actions, phase edits or choices.

A read-only observer records native cast/activation, source discard, resolution,
revealed basic movement and shuffle events, the actor's own hand, and public life/
land/library counts. It never supplies a decision or reads hidden library order.
The independent checker pairs every observed action, verifies 6RG/two-mana payments,
per-opponent land-count damage, native search/hand/shuffle and no draw-count increase.
Controls require ordinary native actions while retaining zero Terrain use.
Clock-limited observations establish card use only: no completion, winner or strength claim.

Only the installed group of the original compatibility baseline is checked: all
21,729 app/resource hashes. The obsolete unrelated group remains ignored; deliberately
retired research folders stay deleted. Concurrent utility-land work is never compiled
or modified by this lab. Coverage remains verified vs unlocked-only, with no promotion
until native gates and autonomous witnesses pass.

## Reproduce

```powershell
./arena/treacherous-terrain/Check-NeoForge-1.0.27.ps1 -Baseline
./arena/treacherous-terrain/Check-NeoForge-1.0.27.ps1
./arena/treacherous-terrain/Check-Timeouts.ps1
./arena/treacherous-terrain/Run-CardUseProbe.ps1 -Scenario cast -Seed 115011 -ProfileMode on
./arena/treacherous-terrain/Run-CardUseProbe.ps1 -Scenario cast -Seed 115011 -ProfileMode off
./arena/treacherous-terrain/Run-CardUseProbe.ps1 -Scenario cast -Seed 115012 -ProfileMode on
./arena/treacherous-terrain/Run-CardUseProbe.ps1 -Scenario cast -Seed 115012 -ProfileMode off
./arena/treacherous-terrain/Run-CardUseProbe.ps1 -Scenario cycle -Seed 115021 -ProfileMode on
./arena/treacherous-terrain/Run-CardUseProbe.ps1 -Scenario cycle -Seed 115021 -ProfileMode off
python arena/treacherous-terrain/check-card-use.py
./arena/Audit-Deck.ps1 -All -Coverage
python arena/treacherous-terrain/refresh.py
```

The shared gate reconciles current scoped inputs against the frozen 1.0.27 sources.
An existing final gate is protected; `-Focused` is the development lane and `-RunOnly`
uses a sealed build. Raw artifacts/resources/logs stay under ignored `target/`.
