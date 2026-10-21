# Diagnostic pattern — a legal action vetoed by a generic cost heuristic

Reusable failure category for the NeoForge AI work. First diagnosed on **Wall of Limbs**
(see `wall-of-limbs/README.md`).

## The pattern

The AI's decision logic wants to perform an action that is legal and, by the card's own rules,
correct — but a **generic cost heuristic** refuses to pay for it, so the ability is never activated.

It is distinct from "the card's effect logic is wrong". Here the effect logic is right; the veto
happens one step later, at the cost seam.

## Symptom

For an activated ability / spell, the AI decision is **`AiPlayDecision.CostNotAcceptable`**
rather than `CantPlayAi` or `TargetingFailed`. `CostNotAcceptable` means:
`checkAiLogic`/`checkApiLogic` said yes, then `willPayCosts(...)` said no.

To confirm, instrument the decision entry point (no code change needed in the engine):

```java
AiAbilityDecision d = SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa);
System.out.println(d.decision() + " rating=" + d.rating());
```

`CostNotAcceptable` -> this pattern. `CantPlayAi` -> the effect logic itself, which is a different bug.

## Where it lives

- `SpellAbilityAi.canPlayWithoutRestrict` — after `checkApiLogic`, calls
  `willPayCosts(ai, sa, cost, source)`; a `false` here becomes `CostNotAcceptable`.
- Per-ability overrides such as `LifeLoseAi.willPayCosts`.
- The helpers it calls: `ComputerUtilCost.checkLifeCost`, `checkDiscardCost`,
  **`checkSacrificeCost`**, `checkRemoveCounterCost`, `checkDamageCost`.

## Two observed instances

### A. Cost-derived amount evaluated before the cost is paid

`Sacrificed$CardPower` (and similar `<PaidList>$...` values) resolve through
`SpellAbility.getPaidList(...)`, which is **empty before the cost is paid**. So at decision time
`X` is `0`, and a `checkApiLogic` of the form `if (amount <= 0) return CantPlayAi;` disables the card
entirely.

Observed on `Wall of Limbs` (`X$ Sacrificed$CardPower`), whose default AI therefore never activated it.
Fix: derive the amount from the source when the paid list is not yet populated (the cost sacrifices the
host), keeping the paid-list value if it is non-zero.

### B. `checkSacrificeCost` rejects a strategically profitable sacrifice

For `Sac<1/CARDNAME>` (`payCostFromSource`) on a creature, `ComputerUtilCost.checkSacrificeCost`
only allows the sacrifice when the AI is at end of turn with a cheap creature, is in danger, or the
creature is already dying. A card whose whole point is to sacrifice itself for value is therefore
vetoed outside those states — the rule says activate, `willPayCosts` returns `false` anyway.

Observed on `Wall of Limbs`' drain ability. Fix: in the ability's `willPayCosts`, keep the other
`check*Cost` calls and drop only the sacrifice veto.

## The technique

1. **Confirm the seam** — the decision is `CostNotAcceptable`, not `CantPlayAi`.
2. **Gate on the profile.** Do the override only when
   `AiOverrideProfile.get().getAiLogic(source)` names the card's logic, so the default AI is
   byte-identical when no profile is selected.
3. **Override at the narrowest seam, in the ability's own AI class.** Prefer the card's `*Ai`
   `willPayCosts` (keep the remaining `check*Cost` calls) over editing `ComputerUtilCost`.
4. **Do not weaken the generic helper.** `checkSacrificeCost` and friends exist to stop bad
   sacrifices for the hundreds of other cards; removing or relaxing it globally is out of scope and
   regresses them.
5. **Pin it with a gate.** Assert the positive decision (`willingToPlay()`) *and* a no-profile
   regression, as in `arena/slaughter/` and `arena/wall-of-limbs/`.

## Reuse

When another card is legal and the rules say play, but the AI declines, first check whether the
decision is `CostNotAcceptable`; if so, apply the technique above. Keep the existing narrow
per-card `willPayCosts` branch in `LifeLoseAi` as the template.

