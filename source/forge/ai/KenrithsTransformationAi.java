package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.List;

/** Opt-in ordinary Elk neutralization and one-card draw. Native effects and costs are authoritative. */
public final class KenrithsTransformationAi {
    private KenrithsTransformationAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.isSpell() && sa.getApi() == ApiType.Attach && sa.getHostCard().isAura()
                && "KenrithsTransformation".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    private static boolean safeDraw(final Player ai) {
        if (!ai.canDraw() || ai.getCardsIn(ZoneType.Library).size() < 5) { return false; }
        for (final Card card : ai.getGame().getCardsIn(List.of(ZoneType.Battlefield, ZoneType.Graveyard, ZoneType.Command))) {
            if (card.isPhasedOut()) { continue; }
            for (final Trigger trigger : card.getTriggers()) {
                if (trigger.isSuppressed() || !trigger.zonesCheck(card.getZone())) { continue; }
                final TriggerType type = trigger.getMode();
                if (type == TriggerType.Drawn || type == TriggerType.SpellCast || type == TriggerType.SpellCastOrCopy
                        || type == TriggerType.SpellAbilityCast || type == TriggerType.BecomesTarget
                        || type == TriggerType.BecomesTargetOnce || type == TriggerType.Attached) { return false; }
                if ((type == TriggerType.ChangesZone || type == TriggerType.ChangesZoneAll)
                        && !"Card.Self".equals(trigger.getParam("ValidCard"))
                        && (trigger.getParamOrDefault("Destination", "Any").contains("Battlefield")
                            || "Any".equals(trigger.getParamOrDefault("Destination", "Any")))) { return false; }
            }
            for (final ReplacementEffect replacement : card.getReplacementEffects()) {
                if (replacement.isSuppressed()
                        || !replacement.getParamOrDefault("ActiveZones", "Battlefield").contains(card.getZone().getZoneType().name())
                        || !replacement.requirementsCheck(ai.getGame())) { continue; }
                if (replacement.getMode() == ReplacementType.Draw || replacement.getMode() == ReplacementType.DrawCards
                        || replacement.getMode() == ReplacementType.Moved) { return false; }
            }
        }
        return true;
    }

    private static boolean eligible(final Player ai, final SpellAbility sa, final Card card) {
        return card != null && card.getGame() == ai.getGame() && card.isInPlay() && !card.isPhasedOut()
                && card.isCreature() && card.getController().isOpponentOf(ai) && sa.canTarget(card)
                // Removing a continuous source can change other permanents as well; defer that collateral.
                && card.getStaticAbilities().stream().noneMatch(st -> !st.isSuppressed()
                    && st.getMode().contains(StaticAbilityMode.Continuous)
                    && !"Self".equals(st.getParam("AffectedDefined")))
                && DarksteelMutationAi.useful(card, sa.getHostCard(), true);
    }

    public static Card choose(final Player ai, final SpellAbility sa, final List<Card> legal) {
        if (!uses(sa) || !safeDraw(ai)) { return null; }
        final CardCollection useful = new CardCollection();
        for (final Card card : legal) { if (eligible(ai, sa, card)) { useful.add(card); } }
        return ComputerUtilCard.getBestCreatureAI(useful);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        return !uses(sa) || safeDraw(ai) && sa.isTargetNumberValid() && sa.getTargets().size() == 1
                && eligible(ai, sa, sa.getTargetCard());
    }
}

