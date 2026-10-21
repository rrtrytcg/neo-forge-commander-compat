# Toxic Deluge opt-in policy

`--ai-profile=toxic-deluge` enables the original card during play and selects its life-paid X.
The original script stays unchanged, including `AI:RemoveDeck:All` for deck building. Its cost is
**2B plus X life**, not X mana. No script `IsCurse` flag is injected.

## Rules

- Keep at least **five life** after paying X. No emergency or alternate-win exceptions.
- Consider every opposing creature and count our creatures and allies' creatures as friendly.
- Choose the **smallest positive X** removing at least two more opposing creatures than friendly
  creatures, and strictly more opposing native creature value than friendly value.
- Hold if no X qualifies, native costs are unavailable, or casualty prediction is uncertain.
- Keep native timing, mana/life payment, cost modification, spell resolution and state actions.

`PumpAllAi` has a profile-gated entry point. The local preview checks native life-payment legality,
then passes its selected X through native cost checks and normal controller selection. It never
changes live life totals or creature stats while evaluating the board. Re-evaluation clears stale X.

`CreatureShrinkAi` extracts Decree of Pain's existing -2/-2 preview into an amount-parameterized
helper. Decree still calls it with two and retains its previous decision rules. The same helper
checks zero toughness, marked damage, indestructibility, existing regeneration shields, native
destroy replacements and the exceptional single-source lethal-damage rule. Zero toughness bypasses
indestructibility, shield counters and regeneration. A dying continuous effect can change later
casualties, so Toxic Deluge conservatively holds in that case. Vigilance alone is not such an effect.

Public active death/leave-the-battlefield zone triggers are also treated conservatively; their
potential board, life and draw chains are outside this bounded policy. Unrelated ETB triggers do not
veto it. This work does not claim a general wipe planner, future-response prediction, death-trigger
solver, or support for unrelated mana/life replacement families.

## Native verification on NeoForge 1.0.25

The runner uses a hash-checked private copy of the prior Corpse Augur 1.0.25 candidate. It explicitly
compiles the new helper, Toxic Deluge policy, Decree refactor and PumpAll hook. Installed 1.0.25 module
and asset hashes must still match the recorded compatibility audit. Resources are copied into a new
private runtime. No installed user data, app file, preset deck, frozen candidate or historical runner
is written.

```powershell
./arena/toxic-deluge/Check-ToxicDeluge.ps1
./arena/toxic-deluge/Check-Timeouts.ps1
foreach ($mode in @('ordinary','indestructible')) {
    foreach ($profile in @('on','off')) {
        foreach ($seed in @(96561,96562)) {
            ./arena/toxic-deluge/Run-CardUseProbe.ps1 -Mode $mode -ProfileMode $profile -Seed $seed
        }
    }
}
./arena/toxic-deluge/Check-CardUseEvidence.ps1
```

The prior candidate is prepared by the committed Corpse Augur runner and the installed-version
compatibility workflow. The full Toxic Deluge gate includes its own suite and all seventeen prior
Commander suites. `-CardOnly` is a development gate and does not authorize the probe runner.

Native fixtures exercise minimum X; life boundaries; multiple opponents and allies; value loss;
empty/insufficient boards; public uncertainty; live payment of X=2/3/4; native 2B independent of X;
indestructibility; shield counters and existing regeneration; marked damage; actual casualties and
survivor stats; native end-of-turn cleanup; taxes; wrong color/tapped mana; sorcery timing; stable
previews and default/unrelated-profile admission. Deadline checks reuse the unchanged upstream
tracker tests and integration fixtures on this same candidate.

## Autonomous custom-deck evidence

The deliberately repeated-card decks observe card use, not deck legality or matchup strength.
Four-player native Matches use the normal controller, mana and life payment, casting and state
actions. No hands, actions, targets, phases, choices, draws or mana are forced.

The read-only observer records native life events, the spell on the stack, its global -X/-X effect,
subsequent battlefield-to-graveyard events, and a settled board at the next phase or stack action.
State-based deaths occur after the spell-resolved event; they are not inferred merely from that
event. The checker uses independent arithmetic for these plain probe creatures, independently checks
the minimum qualifying X and trade, pairs the actual life payment with X, verifies three native
Swamp payments, and matches all actual deaths and survivors. Indestructible probes require actual
indestructible casualties. Off controls may have ordinary life changes but must never use Deluge.

Seeds, commands, profile controls, decks, native candidate, inputs and logs are hashed. Clock stops,
pending observations, late events and diagnostics are recorded. A custom probe establishes card
use only; completion and winners are not acceptance criteria or strength evidence.

## Verified result — 17 October 2026

- **127 Toxic Deluge native checks**, **1,368 prior Commander checks** (including all 69 Decree
  checks after extraction), and **20 deadline checks** pass on the same private 1.0.25 candidate.
- Four profile-on probes at seeds **96561/96562** show **26 paid, resolved and settled casts**,
  **92 opposing versus four friendly deaths**, including **46 indestructible creatures**.
- Natural chosen X values are **1, 2 and 3**. All 26 casts pay exactly three native Swamp mana and
  the recorded X life, **41 life total**. All casts retain the five-life reserve. Native fixtures
  additionally verify X=4 at nine life leaves five life while still paying only three mana.
- Ordinary probes have **5/6 casts**; indestructible probes have **6/9 casts**. All four off controls
  have zero Deluge use. Every mana cost, life event, native shrink, death and settled board is paired
  and checked independently of the policy's casualty helper.
- Seven runs reach their 20-second observation clocks; ordinary/on/96562 ends before its clock.
  All eight have zero pending observed casts, zero late events excluded and no unexpected diagnostics.
  Both indestructible/on runs print an expected `InterruptedException` during clock shutdown; these
  interruptions are retained in the evidence. Printed game outcomes are not acceptance evidence.

The registry marks this bounded policy **verified**. Coverage is now **29 decks with zero remaining
blocked cards**, including newly cleared **MSC - Doom Prevails**. Of 38 unlocked cards, **28 are
verified and ten remain unlocked-only**. The historical version-compatibility snapshot is preserved;
the new helper and suite are explicitly compiled by this card's 1.0.25 runner.

Detailed inputs, log hashes and witnesses are in `verification-summary.json` and
`card-use-evidence.json`. Generated jars, full logs and copied resources stay under ignored `target/`.
