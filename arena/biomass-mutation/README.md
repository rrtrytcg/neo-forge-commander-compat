# Biomass Mutation opt-in combat policy

`--ai-profile=biomass-mutation` admits the original spell during play. It keeps its original
`AI:RemoveDeck:All` deck-building hint, instant timing, X{G/U}{G/U} cost and base-P/T-only effect.

## Rules

- Choose the largest natively affordable **X >= 3**, after paying both hybrid symbols and taxes.
- At our beginning of combat, require at least two creatures legally able to attack that gain power.
- On an opponent's declare-blockers step, require at least two actual friendly blockers gaining power.
- Hold if any affected friendly creature loses power or toughness, or would die to the resulting stats
  and marked damage. Counters and ordinary additive bonuses apply after the new base.
- Hold for uncertain continuous effects, while another own Biomass Mutation is pending, or when
  there is no further improvement. Other windows do not qualify.

`CombatBaseStatsAi` extracts the existing Mirror Entity evaluation without changing its algorithm.
Mirror Entity keeps its previous profile, public preview entry point and pending-activation matching.
Biomass has a separate opt-in entry and matches only its own pending spells. `AnimateAllAi` takes
the native base AI path only for the existing Mirror profile or this explicit new profile; its default
and other hint paths stay unchanged. The original spell supplies the actual effect parameters, so
Biomass preserves creature types and abilities while Mirror Entity still grants all creature types.

`Check-SharedEvaluation.ps1` mechanically compares the extracted algorithm with the committed
Mirror Entity baseline. The final verifier also removes only the new Biomass hooks in memory and
requires the rest of `AnimateAllAi` to match that verified baseline. All existing Mirror fixtures run
on the new candidate alongside the rest of the Commander regressions.

The shared preview uses the native animation helper and retains marked damage. As with the existing
Mirror policy, ordinary constant additive statics and intrinsic changeling are supported. Conditional,
variable, type/keyword-changing or base-setting continuous effects conservatively hold. This includes
uncertain lethal-by-power effects. This is a bounded combat rule, not a combat or combo solver.
Native mana prediction, reservations, hybrid payment, casting restrictions, taxes, resolution,
blocking assignments and end-of-turn cleanup remain authoritative. No new policy RNG or hidden-card
reads are added. Native preview timestamp behavior is retained from Mirror Entity.

## Reproduce on NeoForge 1.0.25

```powershell
./arena/biomass-mutation/Check-SharedEvaluation.ps1
./arena/biomass-mutation/Check-BiomassMutation.ps1
./arena/biomass-mutation/Check-Timeouts.ps1
foreach ($mode in @('offense','defense')) {
    foreach ($profile in @('on','off')) {
        foreach ($seed in @(96781,96782)) {
            ./arena/biomass-mutation/Run-CardUseProbe.ps1 -Mode $mode -ProfileMode $profile -Seed $seed
        }
    }
}
./arena/biomass-mutation/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage *> arena/biomass-mutation/target/coverage-audit.log
./arena/biomass-mutation/Refresh-Verification.ps1
```

The runner requires the prior Nesting Grounds 1.0.25 candidate and its 19-suite, 1,610-check provenance.
It checks the installed modules/assets against the compatibility audit, copies the candidate and
resources into this lane's private ignored `target/`, and explicitly compiles the shared helper,
both card wrappers and the AnimateAll hook. It runs the new suite and all 19 previous suites.
`-CardOnly` is a development gate and cannot satisfy the probe runner. Deadline checks run the
unchanged upstream tracker tests and compatibility integration fixtures on this same candidate.

The 116 native checks cover offensive timing and eligible attackers; actual defensive blockers;
minimum and maximum X; green, blue and mixed hybrid/generic payment; wrong colors and missing/tapped
mana; taxes; friendly shrinking; counters and ordinary anthems; uncertain continuous effects and
marked damage; own/opposing pending spells; allied creatures outside YouCtrl; native controller
selection, casting, actual base stats and types; duplicate-benefit refusal; end-of-turn restoration;
spell versus creature-activation restrictions; and default/unrelated-profile isolation.

## Autonomous custom-deck probes

Four-player native Constructed Matches use deliberately repeated-card decks. Offense uses Forests
and Grizzly Bears against Walls of Wood; defense uses Islands and Walls of Air against Goblin
Brigands, which must attack. These decks expose normal combat decisions; they need not satisfy
deck-construction rules. Nothing forces hands, draws, mana, phases, attackers, blockers or casts.

The read-only subscriber records the paid spell, chosen X, actual eligible attackers/blockers,
creature stats/types at resolution, and settlement at the next phase or stack action. The independent
checker matches X+2 native basic-land payments with X, checks at least two improving participants,
no friendly shrinking or lethal marked damage, actual X/X stats, preserved types and blocking
assignments, native spell resolution, and zero casts in each profile-off control.
Plain probe creatures make these arithmetic checks independent of the policy's animation preview.

Seeds, commands, decks, candidate, inputs and logs are hashed. Clock stops, pending observations,
late events and diagnostics are recorded. These are card-use witnesses; game completion, winners
and strength are not acceptance criteria. Native fixtures establish end-of-turn restoration.

Coverage remains in `arena/coverage/card-status.tsv`, `COVERAGE.md` and `coverage.json`, with verified
and unlocked-only states kept separate. Installed files, original scripts/presets, previous evidence,
and unrelated work are preserved.

## Verified result - 17 October 2026

- **116 Biomass checks** and **1,610 prior Commander checks** pass across 20 suites, including
  Mirror Entity's unchanged 48-check gate. **20 deadline checks** also pass on this private 1.0.25
  candidate. The extraction and AnimateAll baseline comparisons pass.
- Seeds **96781/96782** show **16 paid, resolved and settled casts**: offense **5/3**, defense
  **3/5**. All four off controls show zero. Natural X values range from **3 to 17**.
- All 16 spells pay native **X+2** mana: Forests on offense, Islands on defense. The checker verifies
  actual X/X stats, preserved types, no friendly shrinking, at least two improving ready attackers
  or actual blockers, and unchanged block assignments at resolution. Native fixtures additionally
  cover mixed-color/generic payment, taxes, counters, anthems and end-of-turn restoration.
- All eight runs reach their 20-second observation clocks. There are zero pending observations,
  late events or unexpected diagnostics. Five runs retain an expected `InterruptedException` at
  clock shutdown. Printed outcomes are not acceptance evidence.
- Coverage reaches **31 decks with zero remaining blocked cards**, newly including
  **PIP - Mutant Menace**. Of **40** mechanically unlocked cards, **30** are verified and **ten**
  remain unlocked-only. All five inventory occurrences of Biomass Mutation are covered.

`card-use-evidence.json` records checked observations and native payment logs.
`verification-summary.json` ties those to native/deadline results, candidate/input/log hashes,
shared-algorithm parity, original-script comparison, refreshed coverage and protected file hashes.
Full logs, candidate jars and copied resources remain under ignored `target/`.
