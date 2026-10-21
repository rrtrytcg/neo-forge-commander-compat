# Hunter's Insight: opt-in unblocked combat draw

Use `--ai-profile=hunters-insight`. Original scripts and deck-building hints, preset decks, the
installed app and default AI remain unchanged. The original Effect script otherwise has no AILogic
and is rejected by native EffectAi even after admission.

## Rules

- Own combat only, after blockers are actually declared and before damage, with an empty stack.
- Target a legal, unblocked attacker dealing ordinary single-step damage to an opposing player or
  planeswalker. Use native combat prediction, require at least three damage/cards, and leave at least
  four cards in the library. Choose the largest safe draw, with card ID breaking equal-score ties.
- Defer first/double strike and infect, public draw/damage replacements and draw/damage/cast/target
  triggers, and continuous P/T sources that can invalidate the forecast when this spell is spent.
  Existing Insight damage-draw effects also make additional-combat forecasts conservative holds.
- Recheck the actual selected target, combat window and safe amount before any source/mana mutation.
  Native targeting, instant legality, payment, taxes, damage, effect creation and mandatory draws remain
  authoritative. No generic targeting or draw safety is bypassed for unrelated cards.
- At most one actual cast per player/combat. Only successful native payment and stack insertion mark
  that combat used; evaluating or cancelling a spell does not. Countering it still consumes a cast.
  Weak Combat keys and integer seat IDs avoid retaining games or leaking card-ID authorization.

The new EffectAi branch is before its generic RNG draw and is profile/shape gated. Default EffectAi
retains its original RNG and decision path. The policy adds no RNG and reads no hidden opponent cards.

## Evidence and limits

The new suite contains 72 native checks: blocker declaration, timing, ownership/defender type, minimum
draw and library boundaries, highest safe target, phasing and pending removal, split-step/infect and
public uncertainty holds, color-sensitive mana and taxes, stale target/window/library cancellation,
evaluation versus actual-cast accounting, original deck hints and default/unrelated profile boundaries.

Both player and planeswalker fixtures use normal AI selection/payment and native combat assignment,
damage and delayed triggered draws. The spell itself draws nothing when it resolves; the original
effect draws the amount of actual combat damage afterward. The full 1.0.25 gate requires 2007 checks
across 26 suites: 72 new plus 1935 prior Commander regressions.

Accepted results: **2007/2007 native checks** and **175 card-use checks**. Seeds 99011/99012 produced
**16 casts, 14 matched delayed draw resolutions and 48 cards drawn** (eight casts, seven draws and
24 cards per seed). Casts without a recorded delayed draw are not counted as draw witnesses. The
off control produced zero card use. All three runs ended within their 30-second clocks with native
exit 0 and no unexpected diagnostics; winner/completion results are not acceptance claims.

Coverage: **36 clear decks**, up from 34; 327 distinct blockers across 495 occurrences; 46 unlocked
cards, comprising **36 verified** and 10 unlocked-only. This clears `NEC - Upgrades Unleashed` and
`SCD - Draconic Destruction`; `C20 - Arcane Maelstrom` retains other blockers.

Custom probes use autonomous native four-player matches with nonstandard decks: repeated Insight,
Phantom Monster/Air Elemental and colored lands against ground walls. The observer reads events and
zones without selecting actions or calling AI decision/prediction logic. It matches casts, attacker
IDs, actual combat damage and original delayed draw resolutions. Two on seeds and an off control
use the same tested candidate. Observation clocks and shutdown interruptions are recorded. Card use,
not completion, winners or strength, is the acceptance criterion.

`card-use-evidence.json` and `verification-summary.json` record accepted counts, input/log/candidate
hashes and clocks. The checker asserts one cast per combat, safe unblocked targets and exact
damage-to-trigger-to-hand/library reconciliation. Unlocked and verified registry states stay separate.

## Reproduce

```powershell
./arena/hunters-insight/Check-NeoForge-1.0.25.ps1
./arena/hunters-insight/Run-CardUseProbe.ps1 -Seed 99011
./arena/hunters-insight/Run-CardUseProbe.ps1 -Seed 99012
./arena/hunters-insight/Run-CardUseProbe.ps1 -Seed 99011 -ProfileMode off
./arena/hunters-insight/Check-CardUseEvidence.ps1
# After both gates pass, register verified status and refresh tracking.
./arena/Audit-Deck.ps1 -All -Coverage
./arena/hunters-insight/Refresh-Verification.ps1
```

`Check-NeoForge-1.0.25.ps1 -Focused` is only an iteration helper; its separate focused manifest cannot
satisfy the full gate required by the probes or verification refresh. All build/runtime outputs are private.
