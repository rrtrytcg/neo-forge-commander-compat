# Corpse Augur opt-in policy

The original card is blocked by `AI:RemoveDeck:All`. Its native creature play works; the death
trigger needs targeting specific to this card. `DrawAi` evaluates X before it has selected a
graveyard and normally treats a targeted player as the draw recipient. Corpse Augur always makes
the trigger's controller draw and lose life; the target only determines the amount.

`--ai-profile=corpse-augur` admits this card during play and routes its original Draw trigger to
`CorpseAugurAi`. The hook checks the profile and the original trigger's API, Defined, NumCards and
X expression. All other Draw abilities follow the existing path.

Rules:

- Keep native creature selection, phase restrictions, combat and mana payment.
- Evaluate each legal player's X through the native amount evaluator with that player targeted.
- Choose the largest X leaving at least five life and one library card. Ties keep player order.
- If no target satisfies those reserves, choose the smallest X. The death trigger is mandatory;
  the AI cannot decline it or invent an unavailable zero. An empty graveyard is a legal target.
- Re-evaluate when making the trigger decision. The native engine re-evaluates X at resolution;
  responses can change the graveyard after the choice.

This is a bounded policy for the card's ordinary draw/life-loss amounts. It does not plan sacrifice
lines, predict future responses, or solve arbitrary draw replacements, triggered draw chains or
alternate-win combinations. Those are not claims made by the coverage status.

No original card script, preset deck, default profile, installed application or frozen candidate is
edited. Deck-building warnings stay in force. The probe decks deliberately repeat cards; their
purpose is observing autonomous card use, not valid deck construction or matchup evaluation.

## Reproduce on NeoForge 1.0.25

First create the existing 1.0.25 native compatibility baseline with
`arena/version-compat/Check-NeoForge.ps1`. Then run:

```powershell
./arena/corpse-augur/Check-CorpseAugur.ps1
./arena/corpse-augur/Check-Timeouts.ps1
foreach ($profile in @('on','off')) {
    foreach ($seed in @(96451,96452)) {
        ./arena/corpse-augur/Run-CardUseProbe.ps1 -ProfileMode $profile -Seed $seed
    }
}
./arena/corpse-augur/Check-CardUseEvidence.ps1
```

The card runner adds the new helper and freshly compiled DrawAi to a private copy of the hash-checked
1.0.25 compatibility candidate. It explicitly compiles/runs the new native suite and all sixteen prior
Commander suites. It copies baseline resources into its own runtime and adds the new profile there.
Neither the prior compatibility candidate/runtime nor any historical runner/evidence is rewritten.

The read-only probe observer records creature stack/resolution and actual death-trigger targets,
public graveyards, life, hand and library sizes. The checker independently verifies the target rule,
native 3B payment, actual creature entry, death, draws and life loss; it accounts for simultaneous
death triggers stacked before their preceding draws resolve. Off controls must have zero Augur use.
Clock stops, pending actions, excluded late events, diagnostics and input/candidate/log hashes are
recorded. No completion, winner, Commander-precon success or strength claim follows from a probe.

The observer's `sourceZone` is the stack ability's host zone. Native death triggers may retain a
battlefield last-known-information host. Death witnesses therefore pair the original native death
trigger with its previously resolved creature ID; they do not mistake that LKI zone for the live zone.

Results are recorded in `verification-summary.json` and `card-use-evidence.json` after verification.

## Verified result — 17 October 2026

- **139 native Corpse Augur checks**, including actual 3B payment, creature entry, legal native
  removal and death-trigger resolution. The five resolution fixtures cover opposing graveyards,
  the dead Augur itself, zero draw, mandatory minimum fallback and one library card remaining.
- **1,229 prior Commander checks** pass on the same private NeoForge 1.0.25 candidate, plus all
  **20 deadline checks** (11 unchanged upstream tracker tests and nine integration checks).
- Custom seeds **96451/96452**: **9/5 paid and resolved casts**, **7/2 resolved death triggers**,
  **14/7 cards drawn**, with matching life loss. Seed 96451 has three later zero-card choices
  at six life. Both profile-off controls show zero casts and zero triggers.
- Seed 96452/on reaches its 20-second observation clock; the other three runs end before their
  clocks. All four have zero pending observed abilities, zero excluded late observer events and
  no unexpected diagnostics. Game results are not acceptance evidence.

The coverage registry marks this documented bounded policy **verified**, separately from the ten
other cards that remain unlocked-only. The earlier version-compatibility snapshot remains historical;
this card's new helper and test suite are explicitly included by its own 1.0.25 candidate runner.
Coverage now has **28 decks with zero remaining blocked cards**, including newly cleared
**DRC - Eternal Might**; 37 cards are unlocked, of which 27 are verified and ten remain unlocked-only.
