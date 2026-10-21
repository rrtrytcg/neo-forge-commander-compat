# Hull Breach opt-in policy on NeoForge 1.0.27

Profile: `hull-breach`. The original script, its AI removal flag, default AI and
other profiles remain unchanged. This extends the private 1.0.27 compatibility
candidate from `88a62c5c`; it does not update the installed app.

## Rules

Cast normally from hand in either of our main phases, with an empty stack:

1. Prefer two distinct opposing, eligible permanents: one artifact and one enchantment.
2. Otherwise choose the highest native-valued eligible single opposing target.
3. Use the native removal evaluator; equal values break on stable card ID. For a
   pair, include the highest-priority permanent and then its best distinct partner.
   An artifact enchantment counts once. Never fill a slot with a friendly permanent.
4. Skip indestructible, shielded, warded or regenerating targets. Defer uncertain
   cast/target/tap/death/destruction engines and movement replacements. Variable
   power/toughness, opaque/conditional continuous effects, cloned positions,
   type/control changes, ability suppression, legend-rule/player-loss engines
   and friendly Auras on victims also defer. Ordinary fixed-stat anthems and
   combat keyword grants remain supported; the exact native Commander permission
   is recognized separately from these effects.
5. A private native copy checks affordability and resolves the original destruction
   chain. Hold if a target survives or friendly permanents would be lost, including
   lethal damage after an anthem leaves. Fixed-value anthems are supported.
6. Recheck source/target incarnations, mode, both stored and announced target chains,
   fresh preference, timing, restrictions and affordability before payment.

The forecast does not pay live costs, reserve mana, read hidden opponent hands or
sample policy RNG. Existing reservation-free native affordability is reused.
Native mandatory-target validation is retained. If the preferred choice violates
a mandatory-target restriction or a projection is uncertain, the policy holds;
it does not add a general search over alternative casting/payment lines.

The native Charm choice and its original `Destroy` or `Pump → Pump → Destroy`
chain remain intact. The existing Charm sub-check hook preserves the chosen mode
and targets; the opt-in branch additionally validates freshness. Native payment,
responses, partial target invalidation, fizzles and destruction are unchanged.
Targeting, effect templates and both the raw chosen mode and announced chain are
checked to prevent a stale/tampered plan being rebuilt into a different effect.

## Evidence

**4,298 native checks across 40 suites, 20 deadline checks and 406 card-use checks
passed** on the same private candidate. The 238 Hull Breach checks extend 4,060
prior checks. Native responses can invalidate one target or all targets; the
original engine handles the remaining destruction or fizzle.

| Exposure / seed | Opt-in casts | Default casts | Actual opposing permanents destroyed |
| --- | ---: | ---: | ---: |
| Artifact / 112011 | 4 | 0 | 4 |
| Enchantment / 112021 | 9 | 0 | 9 |
| Both / 112031 | 4 | 0 | 7 |

The 17 paid/resolved casts comprise four artifact-only, ten enchantment-only and
three combined casts. All three default controls show zero Hull Breach casts,
and all six observations retain ordinary native creature casting. Three runs
reach the original 30-second clock and three return earlier. There are no pending
observed actions, fizzles, late observer events or unexpected native diagnostics.

See [verification-summary.json](verification-summary.json) for the sealed final
candidate and checks, and [card-use-evidence.json](card-use-evidence.json) for the
individual observations. Native fixtures exercise 238 new checks, all three paid
original modes, native target legality and mandatory-target restrictions, colored
mana and taxes, protected/friendly targets, response legality, collateral losses,
interruption, profile changes, exact incarnation changes, fresh bans, plan
manipulation and unchanged default admission. All prior Commander suites and the
native 1.0.27 combat/deadline checks run against the same candidate.

Custom decks expose artifact-only, enchantment-only and combined removal with
on/off controls. The observer reads native events and public card states only;
it never calls the policy, chooses targets, edits phases or injects actions.
Repeated-card 120-card decks and an off-color commander are intentional exposure
fixtures, not legal Commander lists. Observation clocks do not establish game
completion, trustworthy winners or playing strength. Unsuccessful observations,
if any, are retained separately rather than counted as witnesses.

The native fixture class reuses the prior Darksteel Mutation fixture setup only;
it loads the original Hull Breach script and goes through normal Charm/controller
selection and the AI's actual cast/payment entry point. Historical test runners,
frozen jars, previous candidates and unrelated concurrent work are untouched.

## Reproduce

```powershell
./arena/hull-breach/Check-NeoForge-1.0.27.ps1 -Focused
./arena/hull-breach/Check-NeoForge-1.0.27.ps1 -RunOnly
./arena/hull-breach/Check-Timeouts.ps1
foreach ($scenario in @('artifact','enchantment','both')) {
    $seed = switch ($scenario) { artifact {112011} enchantment {112021} both {112031} }
    foreach ($mode in @('on','off')) {
        ./arena/hull-breach/Run-CardUseProbe.ps1 -Scenario $scenario -ProfileMode $mode -Seed $seed
    }
}
python ./arena/hull-breach/check-card-use.py
./arena/Audit-Deck.ps1 -All -Coverage
python ./arena/hull-breach/refresh.py
```

The new runner builds an isolated overlay on the preserved 1.0.27 candidate and
copies resources into a separate runtime. It rejects changed installed assets
and stale source inputs. Reproducing a finalized build requires a fresh lane;
historical proof artifacts should not be overwritten. Raw runtime outputs and
the preservation baseline remain ignored under `target/`.

Affected decks: COM Mirror Mastery, C13 Nature of the Beast, MSC The Fantastic Four
and 40K Tyranid Swarm. Each retains other blockers. Coverage distinguishes
mechanically unlocked cards from cards with verified bounded rules and native use.
