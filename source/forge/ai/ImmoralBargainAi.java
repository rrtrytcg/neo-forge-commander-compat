package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.List;

/** Profile-gated plan for Immoral Bargain's creature-for-removal exchange. */
public final class ImmoralBargainAi {
    private ImmoralBargainAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.isSpell() && sa.getApi() == ApiType.Destroy
                && "Immoral Bargain".equals(sa.getHostCard().getName())
                && "ImmoralBargain".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    private static boolean original(final SpellAbility sa) {
        if (sa.getPayCosts() == null || !sa.getPayCosts().hasSpecificCostType(forge.game.cost.CostSacrifice.class)
                || !"X".equals(sa.getTargetRestrictions().getMinTargets())
                || !"X".equals(sa.getTargetRestrictions().getMaxTargets())
                || !"Count$xPaid".equals(sa.getSVar("X"))
                || sa.getTargetRestrictions().getValidTgts().length != 1
                || !"Permanent.nonLand".equals(sa.getTargetRestrictions().getValidTgts()[0])) {
            return false;
        }
        return sa.getPayCosts().getCostParts().stream().anyMatch(part -> part instanceof forge.game.cost.CostSacrifice
                && "X".equals(part.getAmount()) && "Creature".equals(part.getType()));
    }

    private static CardCollection legalCreatures(final Player ai, final SpellAbility sa) {
        return CardLists.filter(ai.getCreaturesInPlay(), CardPredicates.canBeSacrificedBy(sa, false));
    }

    /** Sacrifice every token first, then the weakest non-token creatures while preserving the best one. */
    public static int expendableCount(final Player ai, final SpellAbility sa) {
        final CardCollection legal = legalCreatures(ai, sa);
        final long tokens = legal.stream().filter(Card::isToken).count();
        final long other = legal.size() - tokens;
        return (int) (tokens + Math.max(0, other - 1));
    }

    /** The same deterministic order is used by native payment checks and final cost payment. */
    public static Card preferredSacrifice(final Player ai, final SpellAbility sa, final CardCollection choices) {
        if (!uses(sa) || choices == null || choices.isEmpty()) { return null; }
        final CardCollection tokens = CardLists.filter(choices, Card::isToken);
        if (!tokens.isEmpty()) { return ComputerUtilCard.getWorstAI(tokens); }
        final CardCollection nonTokens = CardLists.filter(choices, card -> !card.isToken());
        final Card best = ComputerUtilCard.getBestCreatureAI(nonTokens);
        nonTokens.remove(best);
        return nonTokens.isEmpty() ? null : ComputerUtilCard.getWorstAI(nonTokens);
    }

    private static boolean mayCast(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        final boolean ownMain = ai.getGame().getPhaseHandler().is(PhaseType.MAIN1, ai)
                || ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai);
        return original(sa) && (source.isInZone(ZoneType.Hand) || source.isInZone(ZoneType.Stack))
                && source.getOwner() == ai && source.getController() == ai
                && ai.getGame().getCardState(source) == source && ownMain
                && ai.getGame().getStack().isEmpty() && sa.checkRestrictions(ai)
                && !legalCreatures(ai, sa).isEmpty()
                && !CardLists.getTargetableCards(ai.getOpponents().getCardsIn(ZoneType.Battlefield), sa).isEmpty();
    }

    /** Null means let the native Destroy AI make its normal target choices. */
    public static AiAbilityDecision check(final Player ai, final SpellAbility sa) {
        if (!uses(sa)) { return null; }
        sa.setXManaCostPaid(null);
        sa.resetTargets();
        if (!mayCast(ai, sa) || expendableCount(ai, sa) < 1) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        return null;
    }

    /** Cap native X after its protection/legality filters, retaining native target ranking. */
    public static int limitTargets(final Player ai, final SpellAbility sa, final int nativeMaximum, final List<Card> targets) {
        if (!uses(sa)) { return nativeMaximum; }
        final int x = Math.min(nativeMaximum, Math.min(expendableCount(ai, sa), targets.size()));
        sa.setXManaCostPaid(x);
        return x;
    }

    /** Native setMaxXValue returns zero for this non-mana X cost on the current engine. */
    public static int initialTargets(final Player ai, final SpellAbility sa, final int nativeMaximum) {
        if (!uses(sa)) { return nativeMaximum; }
        final int available = Math.min(expendableCount(ai, sa), forge.game.card.CardUtil.getValidCardsToTarget(sa).size());
        final int x = nativeMaximum > 0 ? Math.min(nativeMaximum, available) : available;
        sa.setXManaCostPaid(x);
        return x;
    }

    /** Payment-time recheck; target selection is rebuilt by DestroyAi immediately afterward. */
    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!uses(sa)) { return true; }
        final Integer x = sa.getXManaCostPaid();
        return mayCast(ai, sa) && x != null && x > 0 && expendableCount(ai, sa) >= x
                && CardLists.getTargetableCards(ai.getOpponents().getCardsIn(ZoneType.Battlefield), sa).size() >= x;
    }
}
