package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CounterEnumType;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

import java.util.List;

/** Opt-in X choice for Gaze of Granite. Native costs, timing, state actions and resolution stay authoritative. */
public final class GazeOfGraniteAi {
    private GazeOfGraniteAi() { }

    public static boolean uses(final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.DestroyAll
                || !"GazeOfGranite".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))) {
            return false;
        }
        final String valid = sa.getParamOrDefault("ValidCards", "");
        return valid.contains("Permanent.nonLand") && valid.contains("cmcLEX")
                && "Count$xPaid".equals(sa.getSVar("X"));
    }

    private static boolean deathTriggers(final Player ai) {
        // Public active-zone triggers can change the board, life or library after casualties.
        // Do not guess their consequences.
        for (final Card card : ai.getGame().getCardsIn(List.of(ZoneType.Battlefield, ZoneType.Graveyard, ZoneType.Command))) {
            for (final Trigger trigger : card.getTriggers()) {
                if (trigger.isSuppressed() || !trigger.zonesCheck(card.getZone())
                        || (trigger.getMode() != TriggerType.ChangesZone && trigger.getMode() != TriggerType.ChangesZoneAll)) {
                    continue;
                }
                final String origin = trigger.getParamOrDefault("Origin", "Any");
                final String destination = trigger.getParamOrDefault("Destination", "Any");
                if ((origin.contains("Battlefield") || origin.equals("Any"))
                        && (destination.contains("Graveyard") || destination.contains("Exile") || destination.equals("Any"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canDestroy(final Card card) {
        return !card.hasKeyword(Keyword.INDESTRUCTIBLE)
                && card.getCounters(CounterEnumType.SHIELD) == 0 && !card.hasSVar("SacMe");
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        final SpellAbility root = sa.getRootAbility();
        root.setXManaCostPaid(0);
        if (deathTriggers(ai)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        int maximum = 0;
        for (final Card card : ai.getGame().getCardsIn(ZoneType.Battlefield)) {
            if (!card.isLand() && !card.isPhasedOut()) {
                maximum = Math.max(maximum, card.getCMC());
            }
        }
        int bestX = 0;
        int bestScore = 0;
        for (int x = 1; x <= maximum; x++) {
            root.setXManaCostPaid(x);
            if (!ComputerUtilCost.canPayCost(sa, ai, false)) {
                continue;
            }
            final CardCollection enemies = new CardCollection();
            final CardCollection friends = new CardCollection();
            boolean known = true;
            for (final Card card : ai.getGame().getCardsIn(ZoneType.Battlefield)) {
                if (card.isLand() || card.isPhasedOut() || card.getCMC() > x || !canDestroy(card)) {
                    continue;
                }
                // Losing a continuous source can change protection, toughness and other
                // casualties after this destruction event. Hold this X rather than guess.
                if (card.getStaticAbilities().stream().anyMatch(st -> !st.isSuppressed()
                        && st.getMode().contains(StaticAbilityMode.Continuous))) {
                    known = false;
                    break;
                }
                if (card.getController().isOpponentOf(ai)) {
                    enemies.add(card);
                } else {
                    friends.add(card);
                }
            }
            if (!known || enemies.isEmpty()) {
                continue;
            }
            final int score = ComputerUtilCard.evaluatePermanentList(enemies)
                    - ComputerUtilCard.evaluatePermanentList(friends);
            if (score > bestScore) {
                bestScore = score;
                bestX = x;
            }
        }
        if (bestX > 0 && bestScore > 0) {
            root.setXManaCostPaid(bestX);
            if (ComputerUtilCost.canPayCost(sa, ai, false)) {
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            }
        }
        root.setXManaCostPaid(0);
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }
}
