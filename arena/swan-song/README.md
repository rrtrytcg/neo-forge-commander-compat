# Swan Song: opt-in bounded counter policy on NeoForge 1.0.25

Use `--ai-profile=swan-song`. Default AI, original scripts, the installed application and unrelated work remain unchanged.

## Native behavior found

The original script has `AI:RemoveDeck:All`, a native Counter spell targeting an enchantment/instant/sorcery, and the original subability giving its controller a blue 2/2 flying Bird. The native counter-response path runs before the normal removed-card filter, so default AI can already counter spells with Swan Song despite that tag. Generic `CounterAi` selects the top opposing counterable spell and uses its usual value/preferences, including random choices for cheap spells. Its token drawback is accepted without valuing the opposing Bird.

This work preserves that existing default counter-path behavior. Off controls are expected to show native Swan Song use. The opt-in change supplies bounded decisions and Bird safety; it does not repair or generalize admission for other counterspells. The profile explicitly lists only Swan Song for runtime admission and the `SwanSong` logic tag. Deck-building hints and original scripts stay intact.

## Rules and implementation

- Consider only the actual top opposing, legally targetable, natively counterable spell.
- Counter mana value four or greater, using the native card's stack mana value with actual paid X.
- For a cheaper spell, allow an understood direct lethal damage/life-loss effect against us, or simple destruction/damage/exile threatening our owned commander or a useful owned nontoken creature with mana value at least three. Cheap exceptions require a single-spell stack and a simple effect with no subability or unless cost; other combinations are deferred. Native no-loss and creature-threat prediction remain authoritative.
- Hold active token/counter/movement replacements, global entry/token/cast/target/counter triggers and continuous keyword/stat/ability enhancements. These conservative holds cover token doubling, haste, enhanced Birds and dangerous entry effects. Ignore inactive, phased and face-down printed effects; do not inspect hidden opponent hands/libraries. Self-entry triggers on unrelated permanents do not stop play.
- Hold during our combat before blockers, so a new flying blocker cannot invalidate our attack. A new Bird cannot join already-declared blocks.
- Bind the exact source and target spell incarnations and decision reason. Recheck the current top spell, reason, public safety, target and native timing before any source movement or payment. Cancel stale plans before costs.
- Keep native blue payment, costs/taxes, targeting, stack handling, counter resolution and the original Bird creation. Once paid, native opponent responses remain possible. Countering Song gives no Bird, does not refund its mana, and can let the original spell resolve.

The implementation overlays only `SwanSongAi`, `ability/CounterAi` and `ComputerUtil` onto the immutable Skeletal Scrying 1.0.25 candidate. The two shared hooks are an opt-in decision branch and a pre-payment check. It reuses the native counter-response path and `DoomedCreatureAi` creature-threat prediction, without modifying those shared threat rules, the mana engine or any card effect. Weak references avoid retaining finished games. The policy consumes no evaluation RNG and does not add a stack solver or search hidden information.

## Native evidence

**188 new checks plus 3314 prior checks: 3502 checks across 35 suites.** All assets, profiles, classes, engine jars and user/cache/deck directories remain isolated under ignored `target/`.

Fixtures exercise normal native counter selection and original payment/resolution, mana value four, exact X, all three opposing seats, enchantments and sorceries, illegal creature spells, uncounterability, own spells, top-of-stack restriction and cheap nonemergency holds. Emergency fixtures cover lethal/nonlethal damage, another opponent's danger, native inability to lose, useful creature/commander rescue, cheap exile, indestructible targets, cheap creatures and tokens. Bird guards cover doubling, haste, entry damage, lords, counter/cast engines, phased/inactive effects, unrelated self-entry triggers and our pre-/post-blocker timing.

Further fixtures cover insufficient blue mana, native taxes, stale source/target/top/life/reason/profile changes, exact target binding, no live mana/token effects during evaluation, unchanged RNG, hidden-information independence, default/unrelated-profile behavior, countering Song itself and interruption recovery. Native off-profile selection intentionally remains possible, including against a cheap Divination.

## Custom Commander probes

Read-only event observer in autonomous four-player Commander matches. Deliberately nonstandard repeated-card decks make decisions observable: actor Swan Songs and Islands; opponent Islands and free Ornithopters plus Tidings/Concentrate in the value scenario, or Divination/Counsel of the Soratami in the cheap scenario. The free creatures let native AI empty its hand enough to want a draw spell. No forced hands, phases, casts, targets, responses or policy-helper calls.

The checker independently pairs native opposing casts, exact Song targets and blue mana payment, countered cards entering the graveyard without resolving, unchanged draw resources and the original Bird going only to the target's controller with actual 2/2 stats, blue color and flying. A subsequent native zone-change event confirms Song's destination. Resolved uncountered draw spells establish actual opportunities in cheap holds. Events after cutoff are excluded; pending spells, fizzles and diagnostics are recorded separately.

The gate requires native counters and Birds across the value runs in each mode, plus actual cheap draws resolving under opt-in and native cheap counters in off controls. Individual seeds may have no opportunities; those zero-exposure observations remain included. These establish card use and the intended decision difference, without a completed-game, winner or strength claim.

**637 card-use checks passed.** The on-profile value witnesses show six paid/resolved counters and six original Birds. Cheap opt-in runs show zero Song casts while 52 original opposing draw spells resolve; native fixtures independently establish an affordable Song is held for a cheap nonemergency spell. Default controls retain six value counters and twelve cheap counters, each with the original Bird. These are decision/card-use observations, not strength comparisons.

| Scenario | Profile | Seed | Song counters / Birds | Opposing draws resolved | Clock reached |
| --- | --- | --- | --- | --- | --- |
| value | on | 108011 | 0 / 0 | 0 | yes |
| value | on | 108012 | 6 / 6 | 0 | no |
| value | off | 108011 | 0 / 0 | 0 | yes |
| value | off | 108012 | 6 / 6 | 0 | no |
| cheap | on | 108011 | 0 / 0 | 27 | yes |
| cheap | on | 108012 | 0 / 0 | 25 | yes |
| cheap | off | 108011 | 5 / 5 | 11 | yes |
| cheap | off | 108012 | 7 / 7 | 11 | yes |

The value seed 108011 had no draw-spell exposure in either mode; those zero observations remain included. Six runs reached their 30-second clock and two returned before it. There were no fizzles, pending spells, late observer events or diagnostics. Cheap emergency exceptions, exact X and Bird uncertainty boundaries are covered by native fixtures, rather than these narrower autonomous witnesses. One initial off-run was manually interrupted during initialization before observer events; its partial log is retained under `target/interrupted-first-off-run/` and replaced by the final recorded run.

Coverage: **45 clear decks / 184**, **55 mechanically unlocked cards = 45 verified + 10 unlocked-only**. Remaining blockers: 318 distinct cards / 467 occurrences; 263 occurrences unlocked. This removes the last registry blocker from `EOC - Counter Intelligence`. `C16 - Stalwart Unity` and `LTC - Elven Council` also benefit but retain other blockers. Verified means bounded native decisions plus observed card use, without a matchup-strength claim; the discovered native counter-path admission quirk is explicitly preserved.

```powershell
./arena/swan-song/Check-NeoForge-1.0.25.ps1
foreach ($scenario in @('song','cheap')) {
    foreach ($mode in @('on','off')) {
        foreach ($seed in @(108011,108012)) {
            ./arena/swan-song/Run-CardUseProbe.ps1 -Scenario $scenario -ProfileMode $mode -Seed $seed
        }
    }
}
./arena/swan-song/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
./arena/swan-song/Refresh-Verification.ps1
```

`card-use-evidence.json` and `verification-summary.json` seal native/probe inputs and logs, the candidate, original-script equality, six installed baseline hashes, sixteen unrelated working-file hashes and coverage. Raw builds/logs remain under `target/`.
