package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

import java.util.List;

/** Opt-in minimum-life board clear. Native costs, timing, state actions and resolution stay authoritative. */
public final class ToxicDelugeAi {
    private ToxicDelugeAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.getApi() == ApiType.PumpAll
                && "ToxicDeluge".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                && "-X".equals(sa.getParam("NumAtt")) && "-X".equals(sa.getParam("NumDef"));
    }

    private static boolean deathTriggers(final Player ai) {
        // Public active zone triggers may change the board, life or library after casualties.
        // Do not guess their consequences, or inspect hidden cards looking for them.
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

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        final SpellAbility root = sa.getRootAbility();
        root.setXManaCostPaid(0);
        int maximum = 0;
        for (final Card card : ai.getGame().getCardsIn(ZoneType.Battlefield)) {
            if (card.isCreature() && !card.isPhasedOut()) {
                maximum = Math.max(maximum, card.getNetToughness());
            }
        }
        maximum = Math.min(maximum, ai.getLife() - 5);
        if (maximum > 0 && !deathTriggers(ai)) {
            for (int x = 1; x <= maximum; x++) {
                if (!ai.canPayLife(x, false, sa)) {
                    continue;
                }
                int friendlyCount = 0;
                int enemyCount = 0;
                long friendlyValue = 0;
                long enemyValue = 0;
                boolean known = true;
                for (final Card card : ai.getGame().getCardsIn(ZoneType.Battlefield)) {
                    if (!card.isCreature() || card.isPhasedOut()) {
                        continue;
                    }
                    final int dies = CreatureShrinkAi.casualty(card, sa, x);
                    if (dies < 0 || (dies > 0 && card.getStaticAbilities().stream()
                            .anyMatch(st -> st.getMode().contains(StaticAbilityMode.Continuous)))) {
                        known = false;
                        break;
                    }
                    if (dies > 0) {
                        final int value = ComputerUtilCard.evaluateCreature(card);
                        if (card.getController().isOpponentOf(ai)) {
                            enemyCount++;
                            enemyValue += value;
                        } else {
                            friendlyCount++;
                            friendlyValue += value;
                        }
                    }
                }
                if (known && enemyCount >= friendlyCount + 2 && enemyValue > friendlyValue) {
                    root.setXManaCostPaid(x);
                    if (ComputerUtilCost.canPayCost(sa, ai, false)) {
                        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                    }
                }
            }
        }
        root.setXManaCostPaid(0);
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }
}
