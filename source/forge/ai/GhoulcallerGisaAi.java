package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.CostSacrifice;
import forge.game.cost.Cost;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/** Deterministic, opt-in activation timing for Ghoulcaller Gisa. */
public final class GhoulcallerGisaAi {
    private GhoulcallerGisaAi() { }

    public static boolean uses(final SpellAbility sa) {
        if (sa == null || sa.isSpell() || sa.getApi() != ApiType.Token) {
            return false;
        }
        final Card host = sa.getHostCard();
        return host != null && "Ghoulcaller Gisa".equals(host.getName())
                && "GhoulcallerGisa".equals(AiOverrideProfile.get().getAiLogic(host))
                && sa.getPayCosts() != null && sa.getPayCosts().hasSpecificCostType(CostSacrifice.class)
                && "X".equals(sa.getParam("TokenAmount"))
                && "b_2_2_zombie".equals(sa.getParam("TokenScript"));
    }

    /** Native TokenAi resolves X before the sacrifice choice exists; validate only the approved timing here. */
    public static Boolean checkPhase(final Player ai, final SpellAbility sa) {
        if (!uses(sa)) {
            return null;
        }
        return ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai) && ai.getGame().getStack().isEmpty();
    }

    /** Called after TokenAi's native timing, mana and sacrifice-cost checks. */
    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        if (!uses(sa)) {
            return null;
        }
        if (!Boolean.TRUE.equals(checkPhase(ai, sa))) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        // Legality, {B}/{T} payment, and selection of another creature remain native.
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    /** Replace only the generic sacrifice appetite check with native payability for this approved activation. */
    public static Boolean willPayCosts(final Player ai, final SpellAbility sa, final Cost cost, final Card source) {
        if (!uses(sa)) {
            return null;
        }
        return ComputerUtilCost.checkLifeCost(ai, cost, source, 4, sa)
                && ComputerUtilCost.checkDiscardCost(ai, cost, source, sa)
                && ComputerUtilCost.checkRemoveCounterCost(cost, source, sa)
                && ComputerUtilCost.canPayCost(cost, sa, ai, false);
    }
}
