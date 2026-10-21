# Mirage Mirror opt-in compatibility

Profile: `mirage-mirror`, on NeoForge 1.0.25. This is a creature-combat first pass.
Native default AI, other profiles, original card scripts, installed application
and unrelated work remain unchanged. This is not a general copy/combo planner.

## Bounded rules

- Cast the original three-mana artifact only in our second main, with a supported
  creature available whose resulting copy has at least four power.
- Offense: activate at our beginning of combat, only when the resulting copy has
  at least four power and native attack legality permits it to attack.
- Defense: after attackers are declared and before blockers, activate when the
  resulting copy can block a supported single-band attacker aimed at us or our
  owned planeswalker, and survive or trade for an equal/higher native-valued body.
  Already-blocked attackers are deferred. Native AI assigns attacks and blocks.
- Rank eligible targets by native creature value, then lowest card ID. A native
  copy of the actual source is evaluated, preserving its own counters, damage,
  tapped state and control history. Target counters, damage and tapped state are
  not copied. Becoming a copy provides no entry trigger.
- Recheck exact source, target and relevant attacker incarnations, fresh preference,
  timing, legality and native affordability before payment. Nonempty stacks defer;
  at most one paid activation per source incarnation per native turn is allowed.
  Weak game-local history avoids carrying colliding card IDs between fresh games.

Only face-up, nonlegendary, non-copy creatures with no activated abilities,
triggers, replacement effects or custom static abilities qualify. Basic native
combat keywords are admitted with their generated engine traits: flying, reach,
trample, vigilance, haste, defender, first/double strike, deathtouch, lifelink,
indestructible, hexproof and shroud. Native targeting remains authoritative, so
opposing hexproof and all shroud can still prevent selection. Unsupported active
effects can further defer a position.

Noncreature artifact, enchantment and land copies; entry/death/attack engines;
ability chains; existing copies; legendary and face-down targets; unfamiliar
bands; and uncertain continuous, targeting, damage, movement and trigger engines
are deliberately deferred. No original script or default handler is rewritten.
The ordinary native Clone AI remains unchanged outside this profile. The exact
immutable Commander Effect permission (`MayPlay` for `Card.IsCommander+YouOwn` in
the command zone) is recognized as unrelated to Mirror copying. Other continuous
effects on the same source still defer; a regression fixture asserts that boundary.

## Native implementation and evidence

A hidden-information-pruned `GameCopier` applies the original Clone effect privately.
Native affordability reuses the existing Command Beacon payment projection guard;
no live reservations, payment choice or evaluation RNG are introduced. Native
block legality and destruction prediction evaluate a single hypothetical block.
For trade value alone the private bodies are compared untapped, so the evaluator's
one-point untapped bonus does not turn an equal-body trade into a false rejection.
Actual combat characteristics and live tapping are unchanged by this comparison.

Normal payment, copying, summoning sickness/haste, native attack/block assignment,
source/target departure, original ability loss while copied and end-of-turn
restoration remain authoritative. A newly controlled Mirror can block after copying;
it cannot attack merely because the copied creature has been in play longer.
Copying haste can change that native attack restriction.

`Check-NeoForge-1.0.25.ps1` builds a private overlay on the sealed Arcane Lighthouse
candidate and runs 224 new native checks plus all 3,829 prior Commander checks:
4,053 checks across 38 suites.
Fixtures cover normal selection and actual attack/block use, copy characteristics,
counters/damage, sickness/haste, targeting and legend boundaries, costs/taxes and
prohibitions (including a ban introduced after spell selection), profile boundaries,
stale decisions, interruption, RNG/resource
invariance, pending activations and fresh-game history with deliberately colliding
public card IDs. Exact totals and hashes are sealed in `verification-summary.json`.

## Autonomous custom decks

The two scenarios use four-player Commander, original scripts and normal native AI.
Offense supplies Serra Angel targets and ordinary ground opponents. Defense supplies
Mirrors, Walking Corpses and larger opposing ground creatures. Repeated-card,
oversized lists are intentionally invalid Commander decks used only to expose card
use. No policy helper, injected hand, forced phase, manual choice or scripted combat
is used by the observer. The observer identifies Mirrors by original printed card
identity after their current name changes, and records original casts, two-mana
activations, copied characteristics, actual native attacks/blocks and later artifact
restoration.

Each scenario uses two seeds with the profile on and off, with a 30-second game
clock. The independent checker retains exposure failures and checks native action
pairing, payment, actual characteristics and combat events. Clock-limited results
are card-use evidence, with no completion, winner, determinism or strength claim.
Raw logs, runtime copies and candidate jars stay under ignored `target/`.

The initial Commander observations exposed the native command-zone play permission
being rejected by the conservative continuous-effect guard. Six completed initial
runs show no Mirror use and remain recorded in `initial-probe-evidence.json` and
`target/card-use/`; they are non-witness diagnostics. The source-input hash guard
stopped the remaining initial controls after the fix. Final runs use
`target/card-use-final/` and the corrected, fully regressed candidate.

## Recorded results and limits

The final gate passes **4,053 native checks across 38 suites** (224 new and 3,829
prior) and **1,021 card-use checks**. Eight final observations produce 17 original
artifact casts and 79 paid/resolved copy activations: 78 offensive and one defensive.
Copied Mirrors make 13 actual native attacks and one native block. There are 73
later observations of the same source incarnation restored to its original artifact
and activation. Four off controls have zero Mirror casts or activations.

| Scenario / seed | On casts | On offense / defense activations | Actual attacks / blocks | Off Mirror use |
| --- | ---: | ---: | ---: | ---: |
| Offense 111011 | 5 | 19 / 0 | 0 / 0 | 0 |
| Offense 111012 | 4 | 11 / 0 | 11 / 0 | 0 |
| Defense 111021 | 3 | 21 / 1 | 0 / 1 | 0 |
| Defense 111022 | 5 | 27 / 0 | 2 / 0 | 0 |

Five final observations reach the 30-second clock; three return before it. These
remain card-use observations with no winner or completion claim. There are no pending
observed actions, fizzles, late observer events or native stack/payment failures.
Seed 111012 supplies the main offensive witness and 111021 the defensive witness;
the other outcomes are retained. The approved offensive rule checks a legal,
four-power attacker, not whether native AI will eventually attack. **Most offensive
activations did not lead to attacks.** This is a practical limitation of this first
pass, not evidence of improved playing strength or a reason to force attacks.
The on 111011 run casts Isamaru but never Serra Angel; the other offensive runs
supply ordinary Serra casts. Ordinary creature casting includes the native creature
commander, and the checker records default controls separately.

A native edition-metadata initialization error stopped one regression JVM before
its Assault Suit checks. Private edition files matched the baseline, and every
non-overlay jar entry plus compiled overlay bytecode was verified. The unchanged
candidate then completed all 38 suites through `Resume-NeoForge-1.0.25.ps1`.
The failure and validation record remain under `target/final-1.0.25/`; no card code
or asset was changed to bypass it.

## Reproduce

```powershell
./arena/mirage-mirror/Check-NeoForge-1.0.25.ps1
foreach ($scenario in @('offense','defense')) {
    $seeds = if ($scenario -eq 'offense') { @(111011,111012) } else { @(111021,111022) }
    foreach ($mode in @('on','off')) {
        foreach ($seed in $seeds) {
            ./arena/mirage-mirror/Run-CardUseProbe.ps1 -Scenario $scenario -ProfileMode $mode -Seed $seed
        }
    }
}
./arena/mirage-mirror/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
./arena/mirage-mirror/Refresh-Verification.ps1
```

Affected decks: CMM Eldrazi Unbound, M3C Tricky Terrain, MSC The Fantastic Four,
NEC Buckle Up and SLD Odds and Ends. Each retains other blockers. Mechanically
unlocked and verified coverage remain separate; verification applies to these
bounded rules, not every possible Mirage Mirror use.
