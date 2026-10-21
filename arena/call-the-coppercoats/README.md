# Call the Coppercoats opt-in

Use `--ai-profile=call-the-coppercoats`. Original scripts, preset decks, default AI, the installed application and unrelated research remain unchanged. Native timing, targets, Strive costs, payment and token creation remain authoritative.

## Rules

- Cast only with an empty stack during our second main phase or the end step immediately before our next turn, using the engine's next-turn order.
- Require a legal opposing player with at least three creatures. Additional opponents must each contribute at least two more Soldiers.
- Compare eligible target combinations for up to three opponents. Choose the natively affordable combination producing the most Soldiers; break ties by lower native adjusted mana cost, then stable player-ID enumeration.
- Preserve the original 2W base cost and native extra 1W for each target beyond the first. Native taxes, reductions, colored costs and payment legality stay intact.
- Hold uncertain token replacements, entry/cast/target/tap interactions, zone replacements and continuous power/toughness modifiers. This first policy does not generalize to token doublers, entry engines or emergency combat casting.
- Re-evaluate the current preferred targets, creature counts, native timing and affordability before source/stack/cost mutation. Cancel changed selections without substituting a different paid target set. If the same set remains useful, its current count may still qualify.
- Once cast, native resolution uses the current legal targets and creature counts. Partial target illegality and native fizzles are not replaced by a frozen forecast, and Strive costs are not refunded.
- No policy RNG, opponent-hand/library inspection, forced gameplay or alternate token/payment implementation.

## Native affordability and shared behavior

Target combinations are forecast in a private `GameCopier` copy with hidden information pruned. Its native cost/payment checks use the existing Command Beacon reservation guard so there are no live reservations or evaluation RNG changes. The shared guard is scoped and restored in `finally`; native live payment keeps its original reservation behavior. Command Beacon retains its policy and regression suite.

The opt-in Token AI hooks choose targets before the generic X-token phase check, which otherwise sees zero targets, and avoid the generic weakest-opponent targeting/random casting path. All other profiles use the existing Token AI behavior.

## Native fixtures

The NeoForge 1.0.25 lane overlays this helper, Token AI hooks, preflight and shared forecast wrapper onto the previous Timely Ward candidate. It runs **212 new checks** and all **2513 prior checks**.

Original-script fixtures exercise normal controller selection; single, pair and triple native Strive payments; one/two/zero-white constraints; taxes and reductions; player hexproof/allies; stable ties; own-main/end-step timing; token multipliers/entry/static risks; exact original 1/1 white Human Soldier creation; stale counts/mana/targets/ranking/phase/stack; interrupted evaluation; hidden-information independence; preserved live mana/hand/library/timestamps/RNG; and default/unrelated profiles. Native resolution fixtures cover changed counts, partially illegal targets and all targets becoming illegal after costs.

## Autonomous custom-deck Commander probes

Four-player native Commander matches use deliberately nonstandard repeated-card decks. The actor has Isamaru in the command zone and repeated Coppercoats. One mana base mixes Plains and Wastes; the other uses Plains. Opponents use ordinary Ornithopters and Walls of Mist to present multiple creature armies without token modifiers or entry triggers. These are card-use instruments, not legal-precon or strength tests.

The read-only observer records native casts, targets, creature counts, mana colors/payment, token-creation events and resulting token IDs/stats/types. It never changes hands, phases, targets, actions, payments or resolution and never calls policy/prediction helpers. The evidence checker pairs each cast with native resolution and checks the actual new Soldiers against current targeted-player creature counts. On/off controls use the same candidate and seeds. Clock stops, fizzles and pending actions are recorded separately; no completion, winner or strength claim is made.

## Reproduce

```powershell
./arena/call-the-coppercoats/Check-NeoForge-1.0.25.ps1
foreach ($scenario in @('scarce-white','plains')) {
    foreach ($mode in @('on','off')) {
        foreach ($seed in @(104011,104012)) {
            ./arena/call-the-coppercoats/Run-CardUseProbe.ps1 -Scenario $scenario -ProfileMode $mode -Seed $seed
        }
    }
}
./arena/call-the-coppercoats/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
./arena/call-the-coppercoats/Refresh-Verification.ps1
```

All candidate jars, runtime files, temporary classes and raw logs stay under ignored `target/` paths. Installed jars, scripts and decks are never outputs.

## Recorded verification

- **2725 native checks across 31 suites**: 212 new plus all 2513 previous checks. Command Beacon's original 83 checks pass against the shared scoped payment-projection wrapper.
- **317 card-use checks**: **23 native paid/resolved casts create 214 actual original Soldiers**. Seventeen casts use one opponent; six use multiple opponents, including one three-target cast. Native payment records confirm 3/5/7 total mana and enough white mana for the original Strive symbols.
- Scarce-white seeds 104011/104012 each produce six single-target casts, creating 59/40 Soldiers. Plains seeds produce five/six casts and 42/73 Soldiers, including the multi-target witnesses. All 23 casts occur in the caster's second main phase. The last-opponent end-step rule is covered by native fixtures; no autonomous end-step witness is claimed.
- All four off controls show **zero use**. All eight observations exit 0 with no unexpected diagnostics, fizzles or pending casts. The four on observations return before their 30-second clock; all four off controls reach it. Card-use evidence only: no completion, winner or strength claim.
- Refreshed coverage: **42 clear decks**, **41 verified cards**, **10 unlocked-only**, 322 distinct remaining blockers and 478 remaining occurrences. `ONC - Rebellion Rising` is newly clear; Ruthless Regiment and Cabaretti Cacophony retain other blockers.
- Installed baseline, original packaged card script and native/probe/report hashes are recorded in `verification-summary.json`. Unrelated research remains unchanged. No commit is created by this implementation task.
