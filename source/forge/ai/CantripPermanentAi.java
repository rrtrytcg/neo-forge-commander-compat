package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.List;

/** Opt-in safety veto for a permanent's ordinary mandatory one-card entry draw.
 * Native permanent selection, ETB evaluation, draw resolution and mana payment remain authoritative. */
public final class CantripPermanentAi {
    private CantripPermanentAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.isSpell() && sa.getApi() == ApiType.PermanentNoncreature
                && "SafeCantripPermanent".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    private static boolean supported(final Card card) {
        if (!card.isArtifact() || card.getTriggers().size() != 1) { return false; }
        final var trigger = card.getTriggers().get(0);
        final SpellAbility draw = trigger.ensureAbility();
        return trigger.getMode() == TriggerType.ChangesZone
                && "Any".equals(trigger.getParam("Origin"))
                && "Battlefield".equals(trigger.getParam("Destination"))
                && "Card.Self".equals(trigger.getParam("ValidCard"))
                && !trigger.hasParam("OptionalDecider") && draw != null && draw.getApi() == ApiType.Draw
                && "You".equals(draw.getParam("Defined")) && "1".equals(draw.getParam("NumCards"))
                && !draw.usesTargeting() && draw.getSubAbility() == null;
    }

    public static boolean safe(final Player ai, final SpellAbility sa) {
        if (Thread.currentThread().isInterrupted() || !supported(sa.getHostCard())
                || !ai.canDraw() || ai.getCardsIn(ZoneType.Library).size() < 5) { return false; }
        // Public active effects only; never inspect library identities or an opponent's hand.
        for (final Card card : ai.getGame().getCardsIn(List.of(ZoneType.Battlefield, ZoneType.Graveyard, ZoneType.Command))) {
            if (card.isPhasedOut()) { continue; }
            for (final var trigger : card.getTriggers()) {
                if (trigger.isSuppressed() || !trigger.zonesCheck(card.getZone())) { continue; }
                final TriggerType type = trigger.getMode();
                if (type == TriggerType.Drawn || type == TriggerType.AbilityTriggered || type == TriggerType.SpellCast
                        || type == TriggerType.SpellCastOrCopy || type == TriggerType.SpellAbilityCast) { return false; }
                if ((type == TriggerType.ChangesZone || type == TriggerType.ChangesZoneAll)
                        && !"Card.Self".equals(trigger.getParam("ValidCard"))
                        && (trigger.getParamOrDefault("Destination", "Any").contains("Battlefield")
                            || "Any".equals(trigger.getParamOrDefault("Destination", "Any")))) { return false; }
            }
            for (final var replacement : card.getReplacementEffects()) {
                if (replacement.isSuppressed()
                        || !replacement.getParamOrDefault("ActiveZones", "Battlefield").contains(card.getZone().getZoneType().name())
                        || !replacement.requirementsCheck(ai.getGame())) { continue; }
                if (card.isImmutable() && "Commander Effect".equals(card.getName())) { continue; }
                final ReplacementType type = replacement.getMode();
                if (type == ReplacementType.Draw || type == ReplacementType.DrawCards) { return false; }
                if (type == ReplacementType.Moved
                        && (zone(replacement.getParamOrDefault("Origin", "Any"), "Stack")
                            && zone(replacement.getParamOrDefault("Destination", "Any"), "Battlefield")
                        || zone(replacement.getParamOrDefault("Origin", "Any"), "Library")
                            && zone(replacement.getParamOrDefault("Destination", "Any"), "Hand"))) { return false; }
            }
            if (card.isInPlay() && card.getStaticAbilities().stream().anyMatch(st -> !st.isSuppressed()
                    && (st.getMode().contains(StaticAbilityMode.Panharmonicon)
                        || st.getMode().contains(StaticAbilityMode.DisableTriggers)))) { return false; }
        }
        return true;
    }

    private static boolean zone(final String zones, final String expected) {
        return "Any".equals(zones) || zones.contains(expected);
    }

    /** A fresh veto before any source/stack mutation or cost; never interferes with an in-play mana ability. */
    public static boolean preflight(final Player ai, final SpellAbility sa) {
        return !uses(sa) || safe(ai, sa);
    }
}
