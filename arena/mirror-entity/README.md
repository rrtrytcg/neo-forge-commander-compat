# Mirror Entity opt-in combat rules

Activate with `--ai-profile=mirror-entity` (or the existing profile system property for GUI games).
Only Mirror Entity is admitted past its original warning and given new activation logic. Native
creature casting remains in charge of playing its body. Default AI, other profiles, original scripts,
preset decks, installed app and unrelated work remain unchanged.

## Rules and implementation

- Use the largest natively affordable X, requiring **X >= 3**.
- On our beginning-of-combat step, require at least two creatures that can legally attack after the
  projected effect and would gain power.
- On an opponent's declare-blockers step, require at least two actual friendly blockers gaining power.
- Hold if any affected friendly creature loses power/toughness or would die to its resulting toughness
  and marked damage. Counters and ordinary bonuses apply after the base-setting effect.
- Hold while another activation for our army is pending. A resolved effect is not repeated without
  further improvement. Other combat windows do not qualify.

`MirrorEntityAi` reuses `AnimateAi.becomeAnimated`, which applies the native base-setting and all-types
mechanics to a copy. The helper supports intrinsic changeling and ordinary constant additive P/T
statics. Other continuous effects (including variable tribal boosts such as Coat of Arms, conditional
effects, other type/keyword changes and P/T-setting statics) conservatively hold. This is a bounded
combat policy, not a general combat/tribal solver. Native mana prediction, reservations and their RNG
behavior remain intact; there are no new policy random choices or hidden-card reads.

The profile-only branch in `AnimateAllAi` uses the base AI template for native restrictions/cost
handling, then the new decision. Existing `Always` and `CreatureAdvantage` paths remain unchanged for
other cards/profiles. Native payment, taxes, activation prohibitions, resolution, blocking assignments
and end-of-turn restoration remain authoritative.

## Verification, 2026-10-17

- Arena Maven package **PASS**, zero Checkstyle violations. Its generic test phase is skipped;
  the explicit native fixture gates below run separately.
- **48 native Entity checks PASS**: timing, minimum/largest X, taxes, tapped mana, eligible attackers,
  actual blockers, friendly shrinking, counters/anthems, uncertain statics, marked damage, pending
  activations, native offense/defense payment and resolution, types, repeat prevention, end-of-turn
  cleanup, activation prohibition, normal AI selection, existing Always behavior and profile isolation.
- **69 Decree regression checks PASS**, against the same private candidate jar.
- **Eight custom-deck card-use probes PASS**: seeds 95881/95882, offense and defense setups, profile
  on/off, 20-second observations. See `CARD_USE_EVIDENCE.md` and `card-use-evidence.json`.
- **36 native-paid resolved activations:** 17 offensive and 19 defensive. All four profile-off controls
  show zero activation. The read-only observer records actual X, combat participation, P/T and types;
  the evidence gate verifies payment, at least two improved participants, no friendly P/T reduction,
  and native type changes for every completed witness.

This meets the existing correctness-plus-card-use gate for **verified** status. These deliberately
nonstandard decks need not be legal constructed decks; completion and winners are not acceptance
criteria. No Commander-precon use or strength claim is made. Logs/private jars remain under ignored
`target/`; source, candidate, deck, observer and log hashes are retained in the evidence artifacts.

## Reproduce

From the source checkout, after building the desktop arena jar:

```powershell
./arena/mirror-entity/Check-MirrorEntity.ps1 -Label final
./arena/decree-of-pain/Check-DecreeOfPain.ps1 -Label entity-regression
./arena/mirror-entity/Run-CardUseProbe.ps1 -Mode offense -ProfileMode on -Seed 95881
./arena/mirror-entity/Run-CardUseProbe.ps1 -Mode defense -ProfileMode on -Seed 95881
# Repeat both with ProfileMode off, then repeat all four with seed 95882.
./arena/mirror-entity/Check-CardUseEvidence.ps1
```

The probe launches a native `Match.startGame` with ordinary AI players and shuffled decks. Its event
subscriber reads state only; no gameplay selection, hands, blockers, phases, draws or payment are
forced. A timer only bounds observation. The runner snapshots the native-tested jar and checks its
recorded inputs before use, protecting running probes from concurrent builds. It uses isolated
`arena/runtime` paths, never the installed application.
