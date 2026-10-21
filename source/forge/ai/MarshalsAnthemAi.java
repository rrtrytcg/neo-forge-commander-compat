package forge.ai;

import forge.game.GameEntityCounterTable;
import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCopyService;
import forge.game.card.CardLists;
import forge.game.cost.Cost;
import forge.game.keyword.KeywordInterface;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementLayer;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Opt-in useful-return cap; payment and the original reanimation trigger remain native. */
public final class MarshalsAnthemAi {
    private MarshalsAnthemAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && "MarshalsAnthem".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    public static boolean survives(final Card creature, final Card anthem) {
        final Card copy = CardCopyService.getLKICopy(creature);
        ComputerUtilCard.applyStaticContPT(creature.getGame(), copy, null);
        return copy.getNetToughness() + (anthem.isInZone(ZoneType.Battlefield) ? 0 : 1) > 0;
    }

    private static boolean entryBlocked(final Card card) {
        if (ComputerUtil.isETBprevented(card)) { return true; }
        final Map<AbilityKey, Object> params = AbilityKey.mapFromAffected(card);
        params.put(AbilityKey.CardLKI, card);
        params.put(AbilityKey.Origin, ZoneType.Graveyard);
        params.put(AbilityKey.Destination, ZoneType.Battlefield);
        params.put(AbilityKey.EffectOnly, true);
        final GameEntityCounterTable counters = new GameEntityCounterTable();
        params.put(AbilityKey.CounterTable, counters);
        params.put(AbilityKey.CounterMap, counters.column(card));
        for (final ReplacementEffect replacement : card.getGame().getReplacementHandler()
                .getReplacementList(ReplacementType.Moved, params, ReplacementLayer.Other)) {
            final SpellAbility effect = replacement.ensureAbility();
            if (effect != null && effect.getApi() == ApiType.ChangeZone
                    && effect.hasParam("Destination") && !"Battlefield".equals(effect.getParam("Destination"))) {
                return true;
            }
        }
        return false;
    }

    /** Keep native most-expensive-permanent ranking and one useful copy of each conflicting legend. */
    public static CardCollection useful(final Player ai, final SpellAbility sa, final Iterable<Card> options) {
        final SpellAbility targetAbility;
        if (sa.getApi() == ApiType.ChangeZone) {
            targetAbility = sa;
        } else {
            targetAbility = sa.getHostCard().getTriggers().get(0).ensureAbility().copy(ai);
        }
        final CardCollection remaining = CardLists.getTargetableCards(new CardCollection(options), targetAbility);
        remaining.removeIf(card -> !card.isCreature() || card.getOwner() != ai
                || !survives(card, sa.getHostCard()) || entryBlocked(card)
                || (card.getType().isLegendary() && !card.ignoreLegendRule() && ai.isCardInPlay(card.getName())));
        final CardCollection result = new CardCollection();
        final Set<String> legends = new HashSet<>();
        while (!remaining.isEmpty()) {
            final Card best = ComputerUtilCard.getMostExpensivePermanentAI(remaining);
            remaining.remove(best);
            if (best.getType().isLegendary() && !best.ignoreLegendRule() && !legends.add(best.getName())) { continue; }
            result.add(best);
        }
        return result;
    }

    public static boolean configure(final Player ai, final SpellAbility sa, final KeywordInterface keyword) {
        sa.setOptionalKeywordAmount(keyword, 0);
        final int useful = useful(ai, sa, ai.getCardsIn(ZoneType.Graveyard)).size();
        final Cost kicker = new Cost(keyword.getOriginal().split(":")[1], false);
        final SpellAbility probe = sa.copy(ai);
        probe.setPayCosts(sa.getPayCosts().copy());
        int affordable = -1;
        for (int amount = 0; amount <= useful; amount++) {
            probe.setOptionalKeywordAmount(keyword, amount);
            if (!ComputerUtilCost.canPayCost(probe, ai, false)) { break; }
            affordable = amount;
            probe.getPayCosts().add(kicker);
        }
        sa.setOptionalKeywordAmount(keyword, Math.max(0, affordable));
        return affordable > 0 || (affordable == 0 && ai.getCreaturesInPlay().size() >= 2);
    }
}
