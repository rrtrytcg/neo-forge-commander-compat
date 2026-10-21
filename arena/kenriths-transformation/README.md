# Kenrith's Transformation opt-in

Use `--ai-profile=kenriths-transformation`. The original script and preset decks are unchanged. The profile admits this card during play and supplies its target decision; native casting, legality, mana payment, attachment, layers and entry draw remain authoritative.

## Rules

- Choose only legal opposing creatures across all opponents, using native threat ranking.
- Preview the original Aura through the same native layer calculation used by Darksteel Mutation. Require a lower total creature evaluation and either at least two less combat damage or lost utility.
- Do not increase either net power or net toughness. This conservative rule holds small creatures even if removing an ability might sometimes justify giving them a larger body. A same-sized 3/3 mana creature can qualify.
- Do not target an already transformed creature or cast only to cantrip. Friendly targets are outside this policy.
- Keep at least four library cards after the one-card entry draw. Hold when drawing is prohibited or public draw replacements, draw triggers, relevant cast/target/attachment triggers or other entry interactions make this bounded forecast uncertain.
- Defer targets whose continuous abilities affect other permanents; self-only effects can be evaluated by the native preview. Ordinary external buffs and counters remain in the preview.
- Recheck draw safety, target legality, current controller, phasing and downgrade immediately before normal payment. A changed position cancels before mana, source-zone or stack changes. Once the spell is cast, native resolution and mandatory triggers proceed normally.
- No library-order inspection, policy RNG, forced action, replacement spell script or alternate payment implementation.

`DarksteelMutationAi.useful(target,aura)` still follows its original rule. An explicit overload adds the stricter Elk checks; the existing Darksteel Mutation profile does not opt into them.

## Native fixtures

The 1.0.25 lane overlays only the new helper and shared hooks onto the prior Viscera Seer native candidate in a private runtime. It runs the new original-script fixtures and all 28 previous suites, including the existing Darksteel Mutation and Viscera Seer suites.

The 157 new checks cover opposing/allied targeting, harmless creatures, utility removal, indestructibility, hexproof/protection, counters and external buffs, source-layer restoration, no evaluation RNG, exact original attachment/type/color/ability removal, native 1G and taxed payment, native entry draw, library sizes zero through five, draw-risk holds, old Aura self-ETB isolation, native fizzle without drawing, and fresh cancellation before costs.

## Custom-deck evidence

Autonomous four-player native Constructed matches use deliberately nonstandard repeated-card decks. Large-threat opponents offer Colossus of Akros and Craw Wurm; utility opponents offer Llanowar Tribe alongside smaller creatures which must not be upgraded. The actor has repeated Auras, Forests and Wall of Wood.

The observer only records engine events. It never forces hands, actions, phases, targets, payments or draws and never invokes policy/prediction helpers. The checker matches normal mana logs, original attachment, actual 3/3 green Elk stats, lost abilities, and the separate original entry trigger's one-card draw. Two seeds per scenario run with the profile on and off. Clock stops and diagnostics are retained. This is card-use evidence, with no completed-game, winner or strength claim.

## Reproduce

```powershell
./arena/kenriths-transformation/Check-NeoForge-1.0.25.ps1
foreach ($scenario in @('threats','utility')) {
    foreach ($mode in @('on','off')) {
        foreach ($seed in @(102011,102012)) {
            ./arena/kenriths-transformation/Run-CardUseProbe.ps1 -Scenario $scenario -ProfileMode $mode -Seed $seed
        }
    }
}
./arena/kenriths-transformation/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
./arena/kenriths-transformation/Refresh-Verification.ps1
```

The installed application is never used as an output directory. Probe runtime and candidate jars live under ignored `target/` directories. Verification establishes this bounded policy and does not claim general competence with enchantress combos or collateral continuous effects.

## Recorded verification

- **2366 native checks across 29 suites**: 157 new plus all 2209 prior checks. Darksteel Mutation's original 87-check policy suite and Viscera Seer's 119 checks both pass unchanged.
- **476 card-use evidence checks**, covering **26 native paid casts, 26 original attachments and 26 matched entry draws**. Threat seeds 102011/102012 contribute 7/6 casts; utility seeds contribute 7/6. Actual targets become 3/3 green Elk creatures and lose their abilities, without receiving a combat-stat upgrade.
- Four profile-off controls show **zero casts, attachments and draws**. All eight observations exit 0, reach their 30-second clock and have no unexpected diagnostics. Clock-limited card-use observations do not establish completion, winners or strength.
- Refreshed coverage: **39 clear decks**, **39 verified cards**, **10 unlocked-only**, 324 distinct remaining blockers and 484 remaining occurrences. `FDC - Tramplesaurus Rex` is newly clear; Aura of Courage and Virtue and Valor retain other blockers.
- The installed baseline and original packaged card script are checked by `Refresh-Verification.ps1`. Existing uncommitted Viscera Seer work and unrelated research are preserved; no commit is created by this implementation task.
