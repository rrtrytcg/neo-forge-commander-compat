package forge.ai;

import forge.card.mana.ManaCost;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.game.cost.Cost;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerHandler;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/** Opt-in main-phase cast rule for Ride the Avalanche; trigger choices stay native. */
public final class RideTheAvalancheAi {
    private static final String CARD_NAME = "Ride the Avalanche";
    private static final String LOGIC = "RideTheAvalanche";

    private RideTheAvalancheAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.getHostCard() != null && CARD_NAME.equals(sa.getHostCard().getName())
                && LOGIC.equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    private static AiAbilityDecision hold() {
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        if (!uses(sa)) { return null; }
        final Card source = sa.getHostCard();
        final var phase = ai.getGame().getPhaseHandler();
        if (!sa.isSpell() || sa.getApi() != ApiType.Effect || !source.isInZone(ZoneType.Hand)
                || source.getOwner() != ai || ai.getGame().getCardState(source) != source
                || !phase.is(PhaseType.MAIN1, ai) && !phase.is(PhaseType.MAIN2, ai)
                || !ai.getGame().getStack().isEmpty() || !sa.checkRestrictions(ai)
                || !sa.getPayCosts().isOnlyManaCost() || !ComputerUtilCost.canPayCost(sa, ai, false)
                || !hasBeneficialTarget(ai, source, sa) || !hasAffordableFollowup(ai, sa)) {
            return hold();
        }
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    private static boolean hasBeneficialTarget(final Player ai, final Card source, final SpellAbility root) {
        final CounterType plusOne = CounterEnumType.P1P1;
        for (final String name : root.getParamOrDefault("Triggers", "").split(",")) {
            final String script = AbilityUtils.getSVar(root, name);
            if (script.isEmpty()) { continue; }
            final Trigger trigger = TriggerHandler.parseTrigger(script, source, false, root);
            if (trigger.getMode() != TriggerType.SpellCast) { continue; }
            final SpellAbility putCounters = trigger.ensureAbility();
            if (putCounters == null || putCounters.getApi() != ApiType.PutCounter) { continue; }
            final SpellAbility aiTrigger = putCounters.copy(ai);
            for (final Card creature : ai.getCreaturesInPlay()) {
                if (!creature.isPhasedOut() && creature.canReceiveCounters(plusOne) && aiTrigger.canTarget(creature)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAffordableFollowup(final Player ai, final SpellAbility ride) {
        final CardCollectionView hand = ai.getCardsIn(ZoneType.Hand);
        for (final SpellAbility followup : ComputerUtilAbility.getSpellAbilities(hand, ai)) {
            final Card card = followup.getHostCard();
            if (!followup.isSpell() || card == ride.getHostCard() || card.isLand()
                    || !followup.getPayCosts().isOnlyManaCost() || followup.getPayCosts().getTotalMana().countX() != 0
                    || !followup.canPlay()) {
                continue;
            }
            followup.setActivatingPlayer(ai);
            if (!ComputerUtilCost.canPayCost(followup, ai, false)) { continue; }

            // Test one shared mana pool for Ride and the follow-up. The separate check above retains
            // follow-up-specific taxes and restrictions; the combined check prevents double-counting sources.
            final ManaCost combinedMana = ManaCost.combine(ride.getPayCosts().getTotalMana(), followup.getPayCosts().getTotalMana());
            final SpellAbility combined = followup.copyWithDefinedCost(new Cost(combinedMana, false));
            combined.setActivatingPlayer(ai);
            if (ComputerUtilMana.canPayManaCost(combined, ai, 0, false)) {
                return true;
            }
        }
        return false;
    }
}
