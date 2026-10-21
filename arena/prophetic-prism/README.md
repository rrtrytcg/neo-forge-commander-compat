# Prophetic Prism: opt-in cantrip and mana fixing on NeoForge 1.0.25

Use `--ai-profile=prophetic-prism`. Default AI, original scripts and installed application are unchanged.

## Rules and implementation

- Reuse native permanent selection, timing, entry-trigger evaluation and mana payment.
- Before casting, require an ordinary mandatory one-card entry draw, permission to draw and at least five library cards, leaving four afterward.
- Hold uncertain active draw/cast/entry interactions, including damage on drawing, draw replacement, additional entry triggers, trigger suppression and triggered-ability countering. Ignore phased/inactive effects and replacements whose zones cannot affect this entry or draw.
- Recheck the current public position before any source/stack mutation or payment. Cancellation leaves the card and mana untouched.
- Once cast, the original mandatory entry trigger and draw resolve natively, including any subsequent opponent counterplay. The guard does not suppress triggers or promise safety against future responses.
- Filtering uses the Prismatic Lens implementation unchanged: spend one other mana, tap Prism and produce a needed color through native prediction and payment. It does not increase mana or produce true colorless mana. An already-in-play Prism remains usable even when its casting draw would be unsafe.

The profile enables `ignoreRemoveDeckDuringPlay=Prophetic Prism`, `oneForOneManaFilters=Prophetic Prism` and the `SafeCantripPermanent` AI logic. `CantripPermanentAi` is a structural safety veto, without a named-card conditional. It recognizes an artifact with exactly one ordinary self-entry Draw-one trigger. It never replaces native selection, costs, the original draw or filtering. The only shared hooks are a veto in `PermanentNoncreatureAi` and a fresh check in `ComputerUtil` before casting mutations. The profile does not opt Lens or other cantrips into new behavior.

The uncertainty guard deliberately holds relevant-zone movement replacements and active global entry/draw/cast engines instead of estimating their combined consequences. It does not inspect hidden opponent cards or library identities and consumes no evaluation RNG. This is bounded compatibility evidence, not a strength claim or a solver for all cantrip combinations.

## Native evidence

The build overlays only `CantripPermanentAi`, `ability/PermanentNoncreatureAi` and `ComputerUtil` onto the immutable Prismatic Lens 1.0.25 candidate. Assets, classes, engine jar, profiles and user/cache/deck directories are isolated under ignored `target/`. No installed files are written.

**194 new native checks plus 2886 prior checks: 3080 checks across 33 suites.** The prior Lens suite remains unchanged. New checks cover:

- Normal AI selection, native casting/payment, artifact entry and the original exactly-one-card trigger resolution.
- Five-card boundary, zero-to-four-card holds and native ETB draw gates already working without the policy.
- Native taxes and reductions; insufficient funding; all five actual filter colors; true colorless requirements; two independently funded Prisms and untapping/reuse.
- Draw prevention, Underworld Dreams/Fate Unraveler, Notion Thief/Alms Collector, Consecrated Sphinx, Panharmonicon/Elesh Norn and Strict Proctor holds.
- Inactive/phased effects ignored; unrelated Etherium Sculptor, Rest in Peace and Grafdigger's Cage remain allowed.
- Fresh library, draw-permission and effect changes cancel before costs or source movement; still-safe changes remain allowed.
- In-play filtering with an empty library and drawing prohibited; unchanged default/unrelated-profile boundaries; no RNG or persistent cancellation state.

## Custom-deck evidence

Read-only event observer in autonomous four-player Commander matches, using repeated-card decks to make entry draws and colored payments observable. Deck construction is deliberately nonstandard. The actor has Wastes, Prism and ordinary creatures needing different colors; the three opponents have passive Walls and Islands. No policy calls, forced phases, draws, hands, targets or payment.

The observer records actual native casts/resolutions, the original entry Draw trigger and before/after hand IDs, library counts and life. Paid mana records identify the Prism, its original Any-color output, its separate activation cost and the color spent on the receiving spell. The evidence checker excludes events after cutoff and records interruptions, pending actions and source reuse across turns. This is card-use evidence only; no completed-game, winner or strength claim.

**366 card-use checks passed:** 13 paid/resolved Prism casts, 13 matched original entry draws and 25 colored payments covering W/U/B/R/G. Both off controls show zero Prism use. All four observations reach their 30-second clock, with no fizzles, pending casts/draws, late observer events or diagnostics.

| Profile | Seed | Prism casts/resolutions | Original entry draws | Colored payments | Reused Prism identities |
| --- | --- | --- | --- | --- | --- |
| on | 106011 | 5 | 5 | 13 (W/U/B/G) | 2 |
| on | 106012 | 8 | 8 | 12 (W/U/B/R/G) | 1 |
| off | 106011 | 0 | 0 | 0 | 0 |
| off | 106012 | 0 | 0 | 0 | 0 |

The runner's generic `casts`/`resolved` fields count actor spells, while the evidence checker's `prismCasts` counts paired Prism spell resolutions and `draws` separately counts their original entry triggers.

Coverage: **43 clear decks / 184**, **53 mechanically unlocked cards = 43 verified + 10 unlocked-only**. Remaining blockers: 320 distinct cards / 472 occurrences; 258 occurrences unlocked. This fixes Prism in `BRC - Mishra's Burnished Banner`, `COM - Mirror Mastery`, `COM - Political Puppets` and `DMC - Painbow`. All four retain other blockers, so no extra deck becomes clear this round.

Run from the repository:

```powershell
./arena/prophetic-prism/Check-NeoForge-1.0.25.ps1
foreach ($mode in @('on','off')) {
    foreach ($seed in @(106011,106012)) {
        ./arena/prophetic-prism/Run-CardUseProbe.ps1 -ProfileMode $mode -Seed $seed
    }
}
./arena/prophetic-prism/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
./arena/prophetic-prism/Refresh-Verification.ps1
```

`card-use-evidence.json` and `verification-summary.json` seal the candidate, native inputs/logs, probe inputs/logs, original-script equality, installed asset baseline, unrelated-file baseline and coverage. Raw builds/logs remain under `target/`.
