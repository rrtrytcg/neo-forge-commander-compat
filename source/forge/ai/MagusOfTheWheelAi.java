package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.cost.CostPartMana;
import forge.game.cost.CostSacrifice;
import forge.game.cost.CostTap;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/** Opt-in safety rule for Magus of the Wheel's self-sacrificing wheel. */
public final class MagusOfTheWheelAi {
    private MagusOfTheWheelAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.getHostCard() != null
                && "Magus of the Wheel".equals(sa.getHostCard().getName())
                && "MagusOfTheWheel".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    private static boolean originalAbility(final SpellAbility sa) {
        if (!uses(sa) || sa.getApi() != ApiType.Discard || sa.isSpell()
                || !"Player".equals(sa.getParam("Defined")) || !"Hand".equals(sa.getParam("Mode"))) {
            return false;
        }
        final SpellAbility draw = sa.getSubAbility();
        if (draw == null || draw.getApi() != ApiType.Draw || !"7".equals(draw.getParam("NumCards"))) {
            return false;
        }
        final Cost cost = sa.getPayCosts();
        return cost != null && cost.getCostParts().size() == 3 && cost.hasTapCost()
                && cost.getCostPartByType(CostPartMana.class) != null
                && cost.getTotalMana().getCMC() == 2 && cost.getTotalMana().getGenericCost() == 1
                && cost.hasSpecificCostType(CostTap.class) && cost.hasSpecificCostType(CostSacrifice.class);
    }

    private static boolean approvedState(final Player ai, final SpellAbility sa) {
        if (!originalAbility(sa) || ai != sa.getActivatingPlayer()) { return false; }
        final Card source = sa.getHostCard();
        if (source.getController() != ai || !source.isInZone(ZoneType.Battlefield) || source.isTapped()
                || !ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai)
                || !ai.getGame().getStack().isEmpty()
                || ai.getCardsIn(ZoneType.Hand).size() > 2
                || ai.getCardsIn(ZoneType.Library).size() < 7
                || ai.getOpponents().isEmpty()) {
            return false;
        }
        for (final Player opponent : ai.getOpponents()) {
            if (opponent.getCardsIn(ZoneType.Hand).size() < 4) { return false; }
        }
        return sa.checkRestrictions(ai);
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        final boolean play = approvedState(ai, sa) && sa.canPlay()
                && ComputerUtilCost.canPayCost(sa, ai, false);
        return new AiAbilityDecision(play ? 100 : 0,
                play ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
    }

    /** Replaces only the generic sacrifice-value veto; the complete printed cost remains engine-paid. */
    public static Boolean willPayCosts(final Player payer, final SpellAbility sa, final Cost cost) {
        if (!uses(sa)) { return null; }
        return cost == sa.getPayCosts() && approvedState(payer, sa)
                && ComputerUtilCost.canPayCost(sa, payer, false);
    }
}
