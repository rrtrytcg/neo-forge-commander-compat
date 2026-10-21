# Skeletal Scrying: opt-in protected graveyard draw on NeoForge 1.0.25

Use `--ai-profile=skeletal-scrying`. Default AI, original scripts and the installed application remain unchanged.

## Rules and implementation

- Cast with an empty stack, in our second main or the opponent end step immediately before our turn, with at most three cards in hand including Scrying.
- Choose the largest natively affordable X of three or two, leaving at least five life and four library cards. Respect native maximum-hand room; hold X zero or one.
- Exile basic lands first, then ordinary instants/sorceries, using stable card IDs within each group. Preserve creatures, other permanents, nonbasic lands and cards with graveyard reuse, including native external play permissions.
- Hold uncertain active draw, life-loss, exile, casting and relevant zone-change interactions. Ignore inactive/phased effects. Defer actual mana abilities with extra resource costs or subabilities that could change the projected reserves.
- Bind the exact source and graveyard card incarnations. Recheck timing, X, current safe cards and native affordability before source movement or costs; cancel stale plans without spending resources.
- Keep native mana payment, exile legality and costs, original draw and life loss, spell resolution and subsequent counterplay. Countering the spell does not refund its paid mana or exiles.

`SkeletalScryingAi` recognizes the opted-in original Draw-X/LoseLife-X spell and its exact own-graveyard exile cost. Hooks in `AiController` and `DrawAi` select bounded X before generic maximum-X evaluation and retain native cost/drawback validation. `ComputerUtil` checks the bound plan before casting and supplies its exact exile choices only after native legality/target exclusions. Other profiles use the original paths.

Affordability reuses Command Beacon's guarded native forecast in one private game copy with hidden opponent information pruned. The policy reads library counts, not identities/order, and consumes no evaluation RNG or live mana reservations. Plans use weak references and do not retain completed games.

NeoForge 1.0.25 already classifies Millikin, Deranged Assistant and Chromatic Sphere as non-mana abilities because they move library cards. Their separate native behavior is preserved. Chromatic Star, City of Brass and Mana Confluence illustrate real mana/payment consequences conservatively deferred here. This is bounded compatibility, not an optimizer for arbitrary graveyard/draw engines or future responses.

## Native evidence

The isolated build overlays `SkeletalScryingAi`, `ability/DrawAi`, `AiController` and `ComputerUtil` onto the immutable Prophetic Prism 1.0.25 candidate. All runtime assets, profiles, classes, engine jar and user/cache/deck directories stay under ignored `target/`.

**234 new native checks plus 3080 prior checks: 3314 checks across 34 suites.** New fixtures cover:

- Normal controller selection, native XB payment, exact original exile/draw/life loss, X two/three, mana/graveyard/life/library boundaries and colored insufficiency.
- Basic-first payment, protected creatures/permanents/nonbasic lands, Flashback/Retrace/Punishing Fire and external Crucible of Worlds permissions.
- Own second main, all four end-step seats, unsafe windows, nonempty stack, hand limits, native taxes/reductions and draw prevention.
- Public uncertainty, inactive/phased effects, resource-changing mana costs and unchanged native non-mana classification.
- Stale hand/life/library/mana/source/card/phase/X/profile cancellation before costs, including leave-and-return incarnations and interruption recovery.
- Native Counterspell response: paid resources stay spent, original draws/life loss do not occur.
- Native Stitcher's Supplier self-mill feeding a later ordinary Scrying cast, hidden-information independence, unchanged RNG/reservations and default/unrelated-profile boundaries.

## Custom-deck evidence

The autonomous four-player Commander probes use deliberately nonstandard repeated-card decks. The actor has Swamps, Scrying, Stitcher's Supplier and Doom Blade; opponents have passive Walls, Ornithopters and Islands. Native Supplier triggers fill the graveyard. The observer only records native events; it never calls policy helpers, injects graveyards or forces phases, hands, casts or payments.

The checker pairs actual native casts and resolutions, validates paid mana and exact exiled cards independently from raw card metadata, checks basic-first payment and retained protected cards, and verifies original draw/life-loss deltas and actual hand identities. Supplier's original three-card mill is checked separately. Events after clock cutoff are excluded. These are card-use observations, with no completed-game, winner or strength claim.

The first, more draw-heavy deck produced two Scrying resolutions in seed 107011 and none in 107012: its hand stayed above the approved three-card limit. Those exploratory logs and the original deck are retained under `target/initial-draw-heavy-probes/`. The final deck uses fewer Scryings and more cheap Suppliers; the policy and engine were unchanged.

**166 card-use checks passed.** The final deck produced three paid/resolved X3 casts, nine cards drawn, nine life lost and nine Swamps exiled. Each cast retained protected graveyard creatures. Both off controls showed zero Scrying use. All four runs reached their 30-second clock, with no fizzles, pending casts/mills, late observer events or diagnostics.

| Profile | Seed | Scrying casts/resolutions | Cards drawn / life lost / basic exiles | Native Supplier mills |
| --- | --- | --- | --- | --- |
| on | 107011 | 1 | 3 / 3 / 3 | 10 |
| on | 107012 | 2 | 6 / 6 / 6 | 7 |
| off | 107011 | 0 | 0 / 0 / 0 | 11 |
| off | 107012 | 0 | 0 / 0 / 0 | 4 |

These autonomous witnesses all used X3 in our second main and paid with basic lands. X2, end-step casting, ordinary-spell exiles, external reuse permissions, reserve boundaries and counterplay are covered separately by native fixtures. The resolution event fires before the engine removes an instant from the stack; a matched subsequent native zone-change event confirms each Scrying reaches the graveyard.

Coverage: **44 clear decks / 184**, **54 mechanically unlocked cards = 44 verified + 10 unlocked-only**. Remaining blockers: 319 distinct cards / 470 occurrences; 260 occurrences unlocked. This removes the final blocker from `C17 - Vampiric Bloodlust`; `C14 - Sworn to Darkness` also benefits but retains 14 other blockers. The registry's verified status means bounded native decisions plus observed card use, without a matchup-strength claim.

Run from the repository:

```powershell
./arena/skeletal-scrying/Check-NeoForge-1.0.25.ps1
foreach ($mode in @('on','off')) {
    foreach ($seed in @(107011,107012)) {
        ./arena/skeletal-scrying/Run-CardUseProbe.ps1 -ProfileMode $mode -Seed $seed
    }
}
./arena/skeletal-scrying/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
./arena/skeletal-scrying/Refresh-Verification.ps1
```

`card-use-evidence.json` and `verification-summary.json` seal native/probe inputs and logs, the candidate, installed hashes, original-script equality, unrelated-file hashes and coverage. Raw logs and build outputs remain under `target/`.
