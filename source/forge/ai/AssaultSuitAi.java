package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

import java.util.List;

/**
 * Bounded opt-in Assault Suit policy. Native equip costs, timing, attachment legality and the
 * original control-change effect remain authoritative; only the AI's equip target and its
 * acceptance of the optional handoff are chosen here.
 */
public final class AssaultSuitAi {
    private static final int RE_EQUIP_COST = 3;

    private AssaultSuitAi() { }

    /**
     * Largest legal trample creature first, then the largest flyer, then the largest first striker,
     * then the largest eligible creature overall. Creatures that cannot attack or deal damage are
     * not worth the equip.
     */
    public static Card chooseEquipTarget(final Player ai, final List<Card> legal) {
        Card best = null;
        int bestTier = Integer.MAX_VALUE;
        for (final Card c : legal) {
            if (!c.isCreature() || c.getController() != ai) {
                continue;
            }
            if (ComputerUtilCard.isUselessCreature(ai, c) || c.getNetPower() <= 0
                    || !ComputerUtilCombat.canAttackNextTurn(c)) {
                continue;
            }
            final int tier = tier(c);
            if (best == null || tier < bestTier || (tier == bestTier && isLarger(c, best))) {
                best = c;
                bestTier = tier;
            }
        }
        return best;
    }

    private static int tier(final Card c) {
        if (c.hasKeyword(Keyword.TRAMPLE)) {
            return 0;
        }
        if (c.hasKeyword(Keyword.FLYING)) {
            return 1;
        }
        if (c.hasKeyword(Keyword.FIRST_STRIKE) || c.hasDoubleStrike()) {
            return 2;
        }
        return 3;
    }

    private static boolean isLarger(final Card a, final Card b) {
        if (a.getNetPower() != b.getNetPower()) {
            return a.getNetPower() > b.getNetPower();
        }
        if (a.getNetToughness() != b.getNetToughness()) {
            return a.getNetToughness() > b.getNetToughness();
        }
        final int evalA = ComputerUtilCard.evaluateCreature(a);
        final int evalB = ComputerUtilCard.evaluateCreature(b);
        if (evalA != evalB) {
            return evalA > evalB;
        }
        return a.getName().compareTo(b.getName()) < 0;
    }

    /**
     * The optional handoff is accepted only when at least {3} is still available to re-equip.
     * The equipped creature's own mana abilities are excluded because control of it is what the
     * trigger gives away.
     */
    public static boolean hasReequipMana(final Player ai, final Card suit) {
        int available = ComputerUtilMana.getAvailableManaEstimate(ai, true);
        final Card equipped = suit.getEquipping();
        if (equipped != null && equipped.getController() == ai) {
            available -= manaFrom(ai, equipped);
        }
        return available >= RE_EQUIP_COST;
    }

    private static int manaFrom(final Player ai, final Card src) {
        int max = 0;
        for (final SpellAbility ma : src.getManaAbilities()) {
            ma.setActivatingPlayer(ai);
            if (!ma.canPlay()) {
                continue;
            }
            final int cost = ma.getPayCosts().getCostMana() != null
                    ? ma.getPayCosts().getCostMana().convertAmount() : 0;
            final int produced = ma.getParamOrDefault("Produced", "").split(" ").length;
            final int amount = AbilityUtils.calculateAmount(src, ma.getParamOrDefault("Amount", "1"), ma);
            max = Math.max(max, produced * amount - cost);
        }
        return max;
    }
}
