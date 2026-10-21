package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opt-in Trading Post mode choice. The card's four original activated abilities, costs, timing,
 * targets and resolution stay authoritative; this only prevents the AI from choosing an unsafe
 * sacrifice/discard mode or activating several modes at once.
 */
public final class TradingPostAi {
    private TradingPostAi() { }

    public static boolean uses(final SpellAbility sa) {
        return isMode(sa) && sa.isAbility() && sa.getHostCard() != null
                && "TradingPost".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    /** Only the sacrifice valuation veto is replaced; native API logic runs first. */
    public static boolean willPayCosts(final Player ai, final SpellAbility sa, final Cost cost) {
        final Card host = sa.getHostCard();
        final SpellAbility mode = chooseMode(ai, host, host.getSpellAbilities());
        return mode != null && mode.getApi() == sa.getApi()
                && ComputerUtilCost.checkLifeCost(ai, cost, host, 4, sa)
                && ComputerUtilCost.checkDiscardCost(ai, cost, host, sa)
                && ComputerUtilCost.checkRemoveCounterCost(cost, host, sa)
                && ComputerUtilCost.canPayCost(sa, ai, false);
    }

    private static boolean isMode(final SpellAbility sa) {
        if (sa == null || sa.getApi() == null) {
            return false;
        }
        switch (sa.getApi()) {
            case GainLife:
            case Token:
            case ChangeZone:
            case Draw:
                return true;
            default:
                return false;
        }
    }

    private static boolean payable(final Player ai, final SpellAbility sa) {
        return ComputerUtilCost.canPayCost(sa, ai, false);
    }

    /** Keep only the safest useful mode for each Trading Post in the play list. */
    public static void filterModes(final Player ai, final List<SpellAbility> list) {
        final Map<Card, List<SpellAbility>> bySource = new LinkedHashMap<>();
        for (final SpellAbility sa : list) {
            final Card host = sa.getHostCard();
            if (uses(sa)) {
                bySource.computeIfAbsent(host, key -> new ArrayList<>()).add(sa);
            }
        }
        for (final Map.Entry<Card, List<SpellAbility>> entry : bySource.entrySet()) {
            final SpellAbility chosen = chooseMode(ai, entry.getKey(), entry.getValue());
            for (final SpellAbility sa : entry.getValue()) {
                if (sa != chosen) {
                    list.remove(sa);
                }
            }
        }
    }

    private static SpellAbility chooseMode(final Player ai, final Card post, final Iterable<SpellAbility> modes) {
        if (post.isTapped()) {
            return null;
        }
        final List<Card> artifacts = new ArrayList<>();
        for (final Card card : ai.getCardsIn(ZoneType.Battlefield)) {
            if (card != post && card.isArtifact() && !card.isPhasedOut()) {
                artifacts.add(card);
            }
        }
        final List<Card> creatures = new ArrayList<>(ai.getCreaturesInPlay());
        final boolean artifactFodder = artifacts.stream().anyMatch(Card::isToken) || artifacts.size() >= 2;
        final boolean creatureFodder = creatures.stream().anyMatch(Card::isToken) || creatures.size() >= 2;
        final boolean graveyardArtifact = ai.getCardsIn(ZoneType.Graveyard).stream()
                .anyMatch(card -> card.isArtifact() && !card.isPhasedOut());
        final boolean spareCard = ai.getCardsIn(ZoneType.Hand).size() >= 2;
        SpellAbility draw = null;
        SpellAbility returnArtifact = null;
        SpellAbility goat = null;
        SpellAbility life = null;
        for (final SpellAbility sa : modes) {
            switch (sa.getApi()) {
                case Draw:
                    draw = sa;
                    break;
                case ChangeZone:
                    returnArtifact = sa;
                    break;
                case Token:
                    goat = sa;
                    break;
                case GainLife:
                    life = sa;
                    break;
                default:
                    break;
            }
        }
        if (draw != null && artifactFodder && ai.canDraw()
                && ai.getCardsIn(ZoneType.Library).size() > 4 && payable(ai, draw)) {
            return draw;
        }
        if (returnArtifact != null && graveyardArtifact && creatureFodder && payable(ai, returnArtifact)) {
            return returnArtifact;
        }
        if (goat != null && ai.getLife() > 3 && payable(ai, goat)) {
            return goat;
        }
        if (life != null && ai.getLife() <= 10 && spareCard && payable(ai, life)) {
            return life;
        }
        return null;
    }
}
