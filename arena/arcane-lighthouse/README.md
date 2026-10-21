# Arcane Lighthouse opt-in compatibility

Profile: `arcane-lighthouse` on NeoForge 1.0.25. The original card script, default AI,
other profiles and installed application are unchanged. This is a bounded policy,
not a general two-action planner or a strength claim.

The ability runs only in our main phase with an empty stack. It needs an opposing
hexproof/shroud creature worth at least 200 under the native creature evaluator,
and a straightforward, single-target destroy or battlefield-to-exile spell in our
hand. The spell must have only a fixed mana cost, no subability, no custom AILogic
and no unrecognized effect parameters. Original target restrictions still apply.
Creatures rank by native value, then card ID; eligible removal cards rank by ID.
Already targetable threats do not justify activating Lighthouse.

A hidden-information-pruned native game copy evaluates the sequence. Lighthouse
is tapped in the copy before either payment. The existing private payment guard
is reused through a small package-private mana-spending wrapper: native cost
adjustment includes activation taxes, native mana production actually spends the
activation mana in the copy, and native affordability then checks the removal
with its colored requirements and spell taxes. The original AnimateAll effect
removes protection in the copy. Nothing changes on the live board during evaluation.
Native greedy payment can conservatively hold a line requiring a different payment
choice. No alternate live payment is forced or reserved by this policy.

Destruction cannot justify activation against indestructibility, regeneration
shields or shield counters. Public regeneration engines conservatively defer
such destruction lines. The copier does not retain regeneration shields, so those
public facts are checked before copying. Active target/cast/tap/death engines and
movement/tap replacements are deferred; Ward is deliberately deferred. Unrelated
entry-only triggers and inactive effects do not block this line. Unsupported
removal, X, extra costs, allied/own targets and uncertain projections hold.

The exact land, removal card and threat incarnations and current preferred choice
are rechecked before native costs. Original activation/payment, duration, all
opponents affected, protection regrant prohibition and state-based handling remain
native. The policy does not force the follow-up. The unmodified AI chooses its own
removal target/timing after resolution; native exile AI may wait until second main
when we have no creatures. Normal land selection and the original tap-for-colorless
ability are not intercepted, including when this profile is off.

## Native evidence

`Check-NeoForge-1.0.25.ps1` compiles an isolated overlay on the sealed Misleading
Signpost candidate. The 158 new checks cover ordinary controller selection and
actual destruction/exile against hexproof/shroud, joint funding and colored costs,
activation/spell taxes and prohibitions, protection/indestructibility/shields,
allies and small threats, unsupported effects, deterministic ties, stale-plan
cancellation, interruption, hidden-information and RNG/resource invariance,
source/target departure, original multi-opponent effect and cleanup, later entrants,
profile boundaries and native colorless mana reuse. The full gate also runs all
3,671 prior Commander checks: 3,829 checks across 37 suites.

## Autonomous custom-deck evidence

`Run-CardUseProbe.ps1` runs four-player Commander using original cards and normal
AI. The observer only reads native events and public getters; it never calls the
policy, injects hands, advances phases, chooses targets or forces actions.
Repeated-card, oversized decks are intentional exposure tools and are not valid
Commander deck lists. Two seeds run with the profile on and off. The checker pairs
paid activation/resolution, actual protection changes, subsequent native removal
and actual graveyard/exile moves. It also checks original colorless payments and
reuse after observed native untaps. Native results and hashes are recorded in
`card-use-evidence.json` and `verification-summary.json`.

The independent checker passes **201 card-use checks**. Seed 110012 with the profile
on supplies three paid/resolved Lighthouse activations and four follow-up removals:
one Murder destruction and three Despark exiles, against one hexproof and three
shroud creatures. The same land activates again after two observed native untaps.
Its original colorless mana also pays for Walking Corpse in both on/off modes.
Both off controls have zero special activations or removals. Seed 110011 has no
Lighthouse land plays in either mode and is retained explicitly as a non-witness.
All four observations reach the clock, with no fizzles, pending observed actions,
late observer events or native payment failures. Default land choice prefers
colored basics in many hands; no land-selection override was added to manufacture
exposure.

Observations are bounded to 30 seconds of game execution. They establish card use,
not normal completion, trustworthy winners or playing strength. Missing casts and
clock stops remain reported rather than treated as completed games.

## Reproduce

```powershell
./arena/arcane-lighthouse/Check-NeoForge-1.0.25.ps1
foreach ($mode in @('on','off')) {
    foreach ($seed in @(110011,110012)) {
        ./arena/arcane-lighthouse/Run-CardUseProbe.ps1 -ProfileMode $mode -Seed $seed
    }
}
./arena/arcane-lighthouse/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
./arena/arcane-lighthouse/Refresh-Verification.ps1
```

The five affected decks are C14 Built from Scratch, Forged in Stone and Sworn to
Darkness, CMM Eldrazi Unbound and SOC Silverquill Influence. Each retains other
blockers. Mechanically unlocked and verified coverage remain separate. Generated
candidate jars, runtime copies and raw logs stay under ignored `target/`.
