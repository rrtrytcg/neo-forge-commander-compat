# Timely Ward opt-in

Use `--ai-profile=timely-ward`. Original card scripts, preset decks, default AI and the installed application remain unchanged. Native timing, legality, payment, layers and resolution remain authoritative.

## Policy

- Proactively protect a useful owned creature during our second main phase with an empty stack. Prefer a commander, otherwise the highest native creature value, then lower stable ID.
- Reactively protect only an owned commander which is already doomed by understood ordinary destruction/damage on the stack or fully declared current combat, and which actually benefits from the original Aura's indestructibility.
- Skip opponents, allies, phased/untargetable/useless creatures, zero toughness, existing indestructibility and an existing Timely Ward.
- Do not claim indestructibility saves exile, bounce, sacrifice, shrinking to zero, wither/infect damage or mixed/unknown stack effects. Hold public cast/target/entry/death/damage consequences and relevant replacements when this bounded policy cannot establish safety.
- For combat, require the declared-blockers window and finalized blocked status. Forecast the original combat first, then preview the original Aura's layers and map the preview creature into a native Combat copy for the survival check. Wither/infect combat is deferred.
- Recheck current preferred target, ownership, phasing, usefulness, threat, native timing and protection before source/stack/cost mutation. Native selection and cost payment retain all mana and legality checks.
- No policy RNG, hidden-library inspection, forced gameplay or replacement card script. Native payment forecasting can use its existing behavior; the policy does not add another payment forecast to evaluation.

## Shared mechanics and timing

The existing Darksteel Mutation layer preview is exposed as a callback and reused by Timely Ward. Both original Darksteel Mutation decisions and Kenrith's Transformation's stricter downgrade checks retain their rules and regression suites. Public no-rescue prediction and consequence guards reuse `DoomedCreatureAi`.

The normal AI's initial timing check precedes target selection. Timely Ward's original flash permission depends on targeting a commander, so this profile prepares its proposed target before that check. The existing native timing validator still runs against that target; noncommanders do not gain this permission. Its opt-in Attach decision avoids the generic flash-Aura random casting chance and combat rejection which ignores the newly granted indestructibility.

## Native fixtures

The NeoForge 1.0.25 lane overlays the helper and shared hooks onto the prior verified Kenrith's Transformation candidate in a private runtime. It runs 147 new original-script checks plus all 2366 previous checks.

Fixtures include normal controller selection without pre-filled flash targets; owned commander preference and noncommander protection; native 2W/taxed/missing-color payment; actual Aura attachment and original indestructibility; commander survival after native Murder, Lightning Bolt, destroy-all and attacking/blocking combat damage; exile/bounce/sacrifice/shrink/wither holds; unknown interactions; existing protection; native layer timestamps/Humility; native fizzle; interrupted/stale selection; no evaluation RNG or live preview changes; and default/unrelated profile controls.

## Autonomous Commander probes

Native four-player Commander matches use deliberately nonstandard repeated-card decks. The actor has Isamaru, Hound of Konda in the command zone, repeated Timely Wards, ordinary creatures and Plains. Proactive opponents use ordinary creatures; rescue opponents use Naturalize and Murder to create repeated protection/removal opportunities. These decks are card-use instruments and are not represented as legal precons.

The observer reads native engine events only. It never changes hands, phases, targets, commanders, choices, payments or resolution, and never calls policy or prediction helpers. Evidence matches actual native mana, original attachment/indestructibility, proactive second-main timing, commander-only flash response to public removal, the removal's resolution, and the same commander's later battlefield presence after native priority/state-based handling. On/off controls use the same seeds and candidate.

A bounded clock stop is recorded explicitly. Card-use observations establish neither normal completion, trustworthy winners nor matchup strength.

## Reproduce

```powershell
./arena/timely-ward/Check-NeoForge-1.0.25.ps1
foreach ($scenario in @('proactive','rescue')) {
    foreach ($mode in @('on','off')) {
        foreach ($seed in @(103011,103012)) {
            ./arena/timely-ward/Run-CardUseProbe.ps1 -Scenario $scenario -ProfileMode $mode -Seed $seed
        }
    }
}
./arena/timely-ward/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
./arena/timely-ward/Refresh-Verification.ps1
```

All candidate jars, runtime settings, logs and temporary classes stay under ignored `target/` paths. Installed jars/scripts/decks and unrelated research are not outputs.

## Recorded verification

- **2513 native checks across 30 suites**: 147 new and all 2366 prior checks. Original Darksteel Mutation, Kenrith's Transformation and Viscera Seer suites retain their expected results.
- **216 card-use checks**: **19 paid casts and 11 actual original attachments**, comprising ten proactive protections and one commander flash rescue. Murder resolves against the newly indestructible commander, and a later native event confirms the same incarnation remains on the battlefield.
- The rescue opponents also cause **eight native fizzles** by removing targets in response. Those casts spend mana but do not attach or provide protection and are reported separately from successful use. No pending casts are counted as successes.
- All four profile-off controls show **zero use**. All eight observations exit 0 with no unexpected diagnostics. Seven reach the 30-second clock; the proactive/off/103012 control returns before it. These observations establish card use only, with no completion, winner or strength claim.
- Refreshed coverage: **41 clear decks**, **40 verified cards**, **10 unlocked-only**, 323 distinct remaining blockers and 481 remaining occurrences. `DSC - Miracle Worker` and `WOC - Virtue and Valor` are newly clear; Arm for Battle retains four other blockers.
- Installed baseline, original packaged script, source/test/probe hashes and report inputs are recorded in `verification-summary.json`. Unrelated research remains unchanged. This implementation task creates no commit.
