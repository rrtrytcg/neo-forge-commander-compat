# Viscera Seer opt-in

Run with `--ai-profile=viscera-seer`. Original scripts, default AI, installed application and unrelated research stay unchanged. The profile admits this card for play and adds only its sacrifice decision; ordinary creature casting and native scry choices are retained.

## Policy

- Sacrifice only a legally sacrificable owned creature already doomed by understood public removal/damage/shrink/exile or fully declared current combat.
- Keep useful combat trades and blockers against trample. Never sacrifice a healthy creature for speculative scry, a future block or a combo line.
- Prefer another eligible creature over Seer itself; among those choose the lowest native creature value, then lower card ID. If only Seer qualifies, sacrifice Seer itself.
- Require at least one library card. Scry does not draw, so a one-card library remains safe and drawing prohibitions do not veto scry.
- Hold public death/sacrifice/activation/scry consequences, relevant replacements and dying continuous sources when this bounded policy cannot establish a safe outcome.
- Bind the exact creature, player, source incarnation, victim incarnation and turn. Recheck the threat, library, legality and preferred-creature ranking before live costs. A changed profile, disappeared threat, blinked creature/source, game rollover or interrupted evaluation cannot reuse stale authority.
- Do not inspect library order before activation or add policy RNG. Native scry examines the actually revealed card and chooses top/bottom normally.

`DoomedCreatureAi` extracts Greater Good's existing threat and consequence checks without changing its rules. Seer adds its declared-combat restriction and omits draw/discard-only hazards. The only shared entry hooks are opt-in Scry AI phase/decision/cost checks, bound native sacrifice payment, and preflight cancellation. There is no forced gameplay or replacement cost payment.

## Native evidence on NeoForge 1.0.25

`Check-NeoForge-1.0.25.ps1` overlays the shared helper, Seer and narrowly changed hooks on the previously verified Command Beacon candidate and copied 1.0.25 assets. Focused runs write a separate manifest and are not the full gate.

The new suite covers native selection/payment/scry, both top and bottom ordering, self-sacrifice, one-card library, removal/damage/nonlethal cases, trade/trample/undeclared-combat guards, deterministic ranking, public uncertainty, native activation prohibition, stale plans, game rollover and default/unrelated profiles. Previous Greater Good fixtures also run against the extracted helper.

## Autonomous custom-deck card-use probes

Four-player native Constructed matches use deliberately nonstandard repeated-card decks. Removal opponents use Murder/Doom Blade; combat opponents use native must-attack indestructible Darksteel Juggernauts plus Ornithopters. The AI must cast Seer, choose an activation and pay the real sacrifice through normal play. The observer reads native events only; it never changes phases, forces threats, sacrifices creatures or invokes a decision/prediction helper.

The evidence checker matches real paid-sacrifice LKI to a public removal target or previously declared owned attacker/blocker, then verifies an original scry-one event, native top/bottom placement and unchanged library/hand/life. It records self-sacrifice and profile-off controls separately. Observation clock stops and shutdown diagnostics are recorded; no winner, game-completion or strength claim is made.

The initial blocker-only observer rejected an attacking-creature activation because it lacked that trace. Its logs/checker are retained under `target/diagnostics/observer-blockers-only`. The subsequent pre-preference-recheck probe set is retained under `target/diagnostics/before-preferred-recheck`; neither set supplies final verification.

## Recorded verification

- **2209 native checks across 28 suites**, including **119 new** and all 2090 prior checks. Greater Good retains its original decisions against the extracted shared helper.
- **164 card-use checks**: seven natural creature casts and **11 paid/resolved scry activations**. Removal seeds 101011/101012 contribute seven responses; combat seed 101021 contributes three declared blockers and one declared attacker. Two activations sacrifice Seer itself.
- Native scry events and ordering match **five cards kept on top and six put on bottom**, with unchanged library count, hand count and life. Both profile-off controls produce zero casts/activations.
- All five runs exit 0 with no unexpected diagnostics. The three removal observations reach the 30-second clock; both combat observations return before it. No completion, winner or strength claim is made.
- Coverage and tracker: **38 clear decks**, **38 verified cards**, **10 unlocked-only**. `TDC - Mardu Surge` is newly clear; the other three Seer decks retain blockers.

Reproduction:

```powershell
./arena/viscera-seer/Check-NeoForge-1.0.25.ps1
./arena/viscera-seer/Run-CardUseProbe.ps1 -Scenario removal -Seed 101011
./arena/viscera-seer/Run-CardUseProbe.ps1 -Scenario removal -Seed 101012
./arena/viscera-seer/Run-CardUseProbe.ps1 -Scenario combat -Seed 101021
./arena/viscera-seer/Run-CardUseProbe.ps1 -Scenario removal -Seed 101011 -ProfileMode off
./arena/viscera-seer/Run-CardUseProbe.ps1 -Scenario combat -Seed 101021 -ProfileMode off
./arena/viscera-seer/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
./arena/viscera-seer/Refresh-Verification.ps1
```
