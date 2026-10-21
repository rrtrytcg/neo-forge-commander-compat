# Command Beacon opt-in

Run with `--ai-profile=command-beacon`. Original card scripts, default AI and the installed NeoForge application remain unchanged. Work and runtime assets stay in the source checkout.

## Policy

- Keep native land play and the ordinary colorless mana ability.
- Consider the return ability only in our main phase with an empty stack, an untapped, unphased, legally usable Beacon, and at least four other lands.
- Require an owned commander in the command zone with at least two previous command-zone casts (four mana of commander tax).
- Forecast native legality and payment from hand in a private `GameCopier` copy with Beacon in the graveyard. Recalculate continuous effects there. Opponents' hidden cards are masked by a new opt-in copier option; existing callers keep their original copy behavior; no opponent hidden information is used to choose.
- Native colored costs, public cost changes and cast prohibitions remain authoritative. Do not count Beacon as a mana source. Hold X spells and commanders with additional nonmana costs, attachments on Beacon, public active zone/sacrifice/tap/ability triggers and zone/tap replacements outside the native Commander Effect. Also hold projected zero-toughness creatures rather than resolve collateral state actions.
- Among payable commanders choose greatest tax savings, then lower card ID. Bind that exact commander for the native return choice and recheck the plan before any live costs are paid.
- Preserve native action ordering: if native AI selects a direct commander cast first, it does so. This policy does not force Beacon ahead of other actions.

The projection creates no live zone changes, native payment or random draws. A narrowly scoped thread-local marker skips mana-reservation heuristics only for the private projected game, whose AI memory has no reservations. It is removed in `finally`; live payment follows the original engine path. The original effect handles tap/sacrifice, commander replacement choice, return, hand casting, taxes and commander history.

## Native gate on NeoForge 1.0.25

`./arena/command-beacon/Check-NeoForge-1.0.25.ps1` overlays only the new helper and the shared hooks onto the previously verified Hunter's Insight candidate, with copied installed assets. `-Focused` runs the new suite only and writes a separate focused manifest.

Unsupported or interrupted projections hold; the experimental snapshot-restore path is deliberately deferred because it does not provide this information boundary.

The fixtures cover the normal AI selection path, original-script resolution and payment, tax and land thresholds, own main phases, stack and activation restrictions, wrong ownership/zone, missing colors, Beacon's lost mana, public taxes/prohibitions, multiple commander choices, native sacrifice/return/hand cast and unchanged cast history, projection RNG and live-state preservation, cancellation, and default/unrelated profiles.

## Autonomous custom-deck Commander probes

`Run-CardUseProbe.ps1` runs a four-player native Commander match with repeated Beacons, Plains and an Isamaru commander against removal-heavy opponents. Decks are deliberately nonstandard. No action, death, tax count, phase or activation is forced; the observer only records native events.

The evidence gate must match actual Beacon sacrifices and returns with later hand casts and resolved commanders. Profile-off controls must show zero Beacon return activations. Observation clock stops and shutdown interruptions are recorded explicitly. These probes are card-use evidence only; they do not establish game completion, winners or strength.

## Recorded evidence

- NeoForge 1.0.25: **2090 native checks across 27 suites**, including **83 new** and 2007 prior checks.
- **252 card-use checks** match original-script payment, sacrifice, command-to-hand resolution and subsequent hand casting.
- Commander seeds **100011 / 100012**, profile on: **10 / 10 activations and returns**, each followed by a native one-white hand cast and resolved commander. Command-zone cast history is preserved.
- Seed **100011**, profile off: **zero return activations**, despite natural commander recasts.
- All three observations reached their **30-second clock**, with exit 0 and no unexpected diagnostics. These are deliberately nonstandard custom-deck card-use witnesses, not completed-game or strength evidence.
- Coverage: **37 clear decks**, with **37 verified cards and 10 unlocked-only**. `OTC - Most Wanted` is newly clear; the other three Command Beacon decks retain blockers.

Reproduction:

```powershell
./arena/command-beacon/Check-NeoForge-1.0.25.ps1
./arena/command-beacon/Run-CardUseProbe.ps1 -ProfileMode on -Seed 100011
./arena/command-beacon/Run-CardUseProbe.ps1 -ProfileMode on -Seed 100012
./arena/command-beacon/Run-CardUseProbe.ps1 -ProfileMode off -Seed 100011
./arena/command-beacon/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
./arena/command-beacon/Refresh-Verification.ps1
```
