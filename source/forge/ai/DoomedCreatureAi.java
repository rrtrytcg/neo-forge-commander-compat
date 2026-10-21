package forge.ai;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.replacement.*;
import forge.game.spellability.*;
import forge.game.trigger.*;
import forge.game.zone.ZoneType;
import java.util.List;

/** Shared public no-rescue threat prediction and sacrifice consequence guards. */
public final class DoomedCreatureAi {
    private DoomedCreatureAi() { }
    public static boolean uncertain(final Player ai, final boolean drawDiscard) {
        for (final Card card : ai.getGame().getCardsIn(List.of(ZoneType.Battlefield, ZoneType.Graveyard, ZoneType.Command))) {
            if (card.isPhasedOut()) { continue; }
            for (final Trigger trigger : card.getTriggers()) {
                if (trigger.isSuppressed() || !trigger.zonesCheck(card.getZone())) { continue; }
                final TriggerType type = trigger.getMode();
                if (drawDiscard && (type == TriggerType.Drawn || type == TriggerType.Discarded || type == TriggerType.DiscardedAll)
                        || type == TriggerType.Sacrificed || type == TriggerType.SacrificedOnce) {
                    return true;
                }
                if (type == TriggerType.ChangesZone || type == TriggerType.ChangesZoneAll) {
                    final String origin = trigger.getParamOrDefault("Origin", "Any");
                    final String dest = trigger.getParamOrDefault("Destination", "Any");
                    if ((origin.contains("Battlefield") || origin.equals("Any"))
                            && (dest.contains("Graveyard") || dest.contains("Exile") || dest.equals("Any"))) { return true; }
                }
            }
            for (final ReplacementEffect replacement : card.getReplacementEffects()) {
                final String zones = replacement.getParamOrDefault("ActiveZones", "Battlefield");
                if (replacement.isSuppressed() || !zones.contains(card.getZone().getZoneType().name())
                        || !replacement.requirementsCheck(ai.getGame())) { continue; }
                final ReplacementType type = replacement.getMode();
                if (drawDiscard && (type == ReplacementType.Draw || type == ReplacementType.DrawCards) || type == ReplacementType.Moved) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isDoomed(final Player ai, final Card card) {
        return isDoomed(ai, card, false);
    }

    public static boolean isDoomed(final Player ai, final Card card, final boolean nonCombatOnly) {
        // The native generic threat list also includes theft and curse Auras. Restrict
        // this first policy to understood removal/damage/shrink stacks or plain combat.
        for (final SpellAbilityStackInstance entry : ai.getGame().getStack()) {
            for (SpellAbility threat = entry.getSpellAbility(); threat != null; threat = threat.getSubAbility()) {
                final ApiType api = threat.getApi();
                if (api != ApiType.Destroy && api != ApiType.DestroyAll && api != ApiType.DealDamage
                        && api != ApiType.DamageAll && api != ApiType.Pump && api != ApiType.PumpAll
                        && api != ApiType.ChangeZone && api != ApiType.ChangeZoneAll) { return false; }
            }
        }
        return ComputerUtil.predictCreatureWillDieThisTurn(ai, card, null, nonCombatOnly);
    }

}
