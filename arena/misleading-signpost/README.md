# Misleading Signpost: opt-in flash rescue and mana on NeoForge 1.0.25

Use `--ai-profile=misleading-signpost`. Default AI, original scripts, installed application and unrelated work remain unchanged.

## Native behavior and rules

The original artifact has Flash, a native tap-for-blue mana ability and an optional self-entry trigger restricted to declare attackers. Its script is tagged `AI:RemoveDeck:All`. Native `ChangeCombatantsAi` declines optional triggers and lacks attacker selection; its generic defender chooser also applies player-targetability filtering even though choosing a new defender does not target that player. Native combat mutation and simple blue mana production already work.

- During an opponent's declare-attackers step, with an empty stack, flash Signpost when a targetable power-three-or-greater creature attacks us or a planeswalker we own and control.
- Prefer highest current power, then lowest card ID. First scope is a single-creature attacking band; unfamiliar bands are deferred.
- Choose the lowest-life other opponent among the engine's actual defender options, then lowest player ID. Exclude ourselves, our allies and the attacker's controller. Do not impose targeting or new attack-declaration costs on defender reselection.
- If still in hand, cast with an empty stack at the last opponent's end step immediately before our turn as a mana-rock fallback. Hold other windows, including our main phases, to retain the flash opportunity through the opponent cycle. Native sequencing and mana payment remain authoritative; no new global mana reservation is introduced.
- Bind source/attacker incarnations and the preferred defender before casting. Recheck the current opportunity before source movement or costs. Select the original optional trigger's target from the fresh board and recheck it and the offered defender list at resolution. A vanished attacker cannot force retargeting; an already-created trigger can survive its artifact leaving.
- Hold active global entry/cast/target/attack/block/damage engines, relevant movement/damage replacements, trigger suppression/doubling and unusual combat-damage assignment. Ignore phased and inactive effects and unrelated self-entry effects. Unknown face-down public sources are deferred without reading their identities.

The shared hooks are a casting veto/plan in `PermanentNoncreatureAi`, opt-in optional-trigger/defender decisions in `ChangeCombatantsAi`, and a pre-payment check in `ComputerUtil`. `MisleadingSignpostAi` recognizes only the explicit profile tag and original artifact/flash/trigger shape. It reuses the native trigger, attacker target legality, offered defenders, original combat-update effect and original blue-mana ability. It does not modify the engine, trigger scripts, mana prediction/payment or other cards. Weak references avoid retaining finished games; decisions do not consume evaluation RNG or inspect hidden opponent hands/libraries.

The planeswalker fixture exposed an overbroad uncertainty veto: Jace's native self-entry loyalty replacement cannot affect Signpost's entry. The guard excludes only unrelated `Card.Self` replacements restricted to entering the battlefield; relevant movement replacements stay deferred. This does not change any other profile's guard.

## Native evidence

**169 new native checks plus 3502 prior checks: 3671 checks across 36 suites.** The isolated build overlays only `MisleadingSignpostAi`, `ability/PermanentNoncreatureAi`, `ability/ChangeCombatantsAi` and `ComputerUtil` onto the immutable Swan Song 1.0.25 candidate. Runtime assets, profiles, jars, classes and user/cache/deck directories remain under ignored `target/`.

Fixtures cover:

- Normal controller selection, native flash/2U payment, original artifact entry, optional target selection, actual defender mutation and unchanged other attackers.
- Power threshold/ranking/stable ties, lowest-life defender/stable ties, excluded attacker controller/allies, no other opponent, restricted native options, player hexproof and Ghostly Prison reselection without a new declaration tax.
- Owned planeswalker rescue, untargetable/small/phased attackers, unrelated opponents' attacks, wrong phases, own combat and native end-step turn order.
- Trigger suppression/doubling, entry/combat engines, movement/damage replacements, inactive/phased effects, unrelated self-entry triggers and attacking bands.
- Native blue costs, insufficient funding and taxes; original tap-for-blue payment on a color-sensitive spell and reuse after controlled untapping. The same in-play mana works with the profile off.
- Fresh source/attacker/ranking/defender/phase/risk/profile/stack cancellation before costs; disappeared attackers, independent trigger survival, native Counterspell responses without refunded costs, unchanged live combat/resources/RNG/timestamps, default and other-profile boundaries, and interruption recovery.

## Autonomous custom Commander evidence

Read-only observer in native four-player Commander matches. Deliberately nonstandard repeated-card decks use Signposts, Islands, Storm Crows and Air Elementals for the actor; opponents have Forests, Centaur Coursers, Rumbling Baloths and Craw Wurms. No forced hands, phases, targets, casts, combat assignments, policy-helper calls or injected battlefield states.

The checker pairs original casts/resolutions, independently ranks observed incoming attackers and native defender choices, checks actual before/after combat, and links later blue payments to previously cast Signposts. Actual tap and untap events distinguish normal source reuse across turns from the controlled fixture untapping. Events after cutoff are excluded; pending actions, fizzles and diagnostics are recorded separately. These establish bounded decisions and card use, without a completed-game, winner or strength claim.

**253 card-use checks passed:** eleven paid/resolved Signposts, comprising three defensive flashes and eight end-step fallbacks; three original trigger resolutions actually redirect 4-power Rumbling Baloths away from us to the lowest-life eligible opponent. The same artifacts supply ten native blue-mana payments, with seven source reuses after observed native untapping. Both off controls show zero Signpost use.

| Profile | Seed | Signpost casts | Defensive flashes / redirections | End-step fallbacks | Blue mana uses | Reuses after native untapping |
| --- | --- | --- | --- | --- | --- | --- |
| on | 109011 | 7 | 0 / 0 | 7 | 7 | 5 |
| on | 109012 | 4 | 3 / 3 | 1 | 3 | 2 |
| off | 109011 | 0 | 0 / 0 | 0 | 0 | 0 |
| off | 109012 | 0 | 0 / 0 | 0 | 0 | 0 |

Seed 109011 provides fallback/mana evidence, rather than a defensive witness. Seed 109012 provides the actual flashes and defender changes. One on observation reached its 30-second clock; the other three returned before it. There were no fizzles, pending spells/triggers, late observer events or diagnostics. These intentionally nonstandard decks do not support a normal completed-game or winner claim. Planeswalker rescue, ally exclusion, banding and opponent counterplay are established separately by native fixtures.

Coverage: **46 clear decks / 184**, **56 mechanically unlocked cards = 46 verified + 10 unlocked-only**. Remaining blockers: 317 distinct cards / 464 occurrences; 266 occurrences unlocked. This removes the final blocker from `FDC - Keen Engineering`; `SLD - Odds and Ends` and `WOC - Fae Dominion` also benefit but retain other blockers. Verified means bounded native decisions plus observed card use, without a matchup-strength claim.

```powershell
./arena/misleading-signpost/Check-NeoForge-1.0.25.ps1
foreach ($mode in @('on','off')) {
    foreach ($seed in @(109011,109012)) {
        ./arena/misleading-signpost/Run-CardUseProbe.ps1 -ProfileMode $mode -Seed $seed
    }
}
./arena/misleading-signpost/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
./arena/misleading-signpost/Refresh-Verification.ps1
```

The evidence and verification manifests seal native/probe inputs and logs, candidate hash, installed original-script equality, six installed-file hashes, sixteen unrelated working-file hashes and coverage. Raw builds/logs remain under `target/`.
