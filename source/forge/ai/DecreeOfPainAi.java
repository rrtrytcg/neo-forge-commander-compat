package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.player.Player;
import forge.game.replacement.*;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;

import java.util.*;

/** Conservative, opt-in owner rules. Native costs, legality and resolution remain authoritative. */
public final class DecreeOfPainAi {
    private DecreeOfPainAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && "DecreeOfPain".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    private static AiAbilityDecision decision(final boolean play) {
        return new AiAbilityDecision(play ? 100 : 0, play ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
    }

    private static boolean active(final ReplacementEffect re) {
        return !re.isSuppressed() && re.zonesCheck(re.getHostCard().getGame().getZoneOf(re.getHostCard()));
    }

    private static boolean libraryEffect(final SpellAbility sa, final Set<SpellAbility> seen) {
        if (sa == null || !seen.add(sa)) {
            return false;
        }
        if (sa.getApi() == ApiType.Draw || sa.getApi() == ApiType.Mill || libraryEffect(sa.getSubAbility(), seen)) {
            return true;
        }
        for (final SpellAbility additional : sa.getAdditionalAbilities().values()) {
            if (libraryEffect(additional, seen)) {
                return true;
            }
        }
        for (final List<? extends SpellAbility> alternatives : sa.getAdditionalAbilityLists().values()) {
            for (final SpellAbility alternative : alternatives) {
                if (libraryEffect(alternative, seen)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean safeDraw(final Player ai, final int upperBound) {
        if (ai.getCardsIn(ZoneType.Library).size() <= upperBound || !ai.canDraw(upperBound)) {
            return false;
        }
        for (final Card card : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            for (final ReplacementEffect re : card.getReplacementEffects()) {
                if (active(re) && (re.getMode() == ReplacementType.Draw || re.getMode() == ReplacementType.DrawCards)) {
                    return false;
                }
            }
            // Extra triggered draws/mill may exceed the simple bound (e.g. multiple death-draw engines).
            // Public triggers only; do not inspect opponents' hidden cards or guess optional choices.
            for (final Trigger trigger : card.getTriggers()) {
                if (trigger.isSuppressed() || !trigger.zonesCheck(card.getZone())) {
                    continue;
                }
                if (libraryEffect(trigger.ensureAbility(), Collections.newSetFromMap(new IdentityHashMap<>()))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** -1 is an uncertain casualty count; do not turn uncertainty into an advantage. */
    public static int casualty(final Card card, final SpellAbility sa, final boolean cycling) {
        if (card.isPhasedOut()) {
            return 0;
        }
        if (!cycling) {
            if (!card.canBeDestroyed() || card.getCounters(CounterEnumType.SHIELD) > 0) {
                return 0;
            }
            // No regeneration for the spell. Other replacement behavior is outside this policy.
            return CreatureShrinkAi.destroyReplacement(card, sa, false) ? -1 : 1;
        }
        return CreatureShrinkAi.casualty(card, sa, 2);
    }

    private static boolean advantage(final Player ai, final SpellAbility sa, final boolean cycling) {
        int own = 0;
        int opponents = 0;
        for (final Card card : ai.getGame().getCardsIn(ZoneType.Battlefield)) {
            if (!card.isCreature() || card.isPhasedOut()) {
                continue;
            }
            final int dies = casualty(card, sa, cycling);
            if (dies < 0) {
                return false;
            }
            if (cycling && dies > 0 && !card.getStaticAbilities().isEmpty()) {
                // A dying lord can cause further deaths after static abilities are recalculated.
                return false;
            }
            if (card.getController().equals(ai)) {
                own += dies;
            } else if (card.getController().isOpponentOf(ai)) {
                opponents += dies;
            }
        }
        return opponents >= own + 2;
    }

    public static boolean cycleQualifies(final Player ai, final SpellAbility sa) {
        return safeDraw(ai, 1) && advantage(ai, sa, true);
    }

    public static AiAbilityDecision cycle(final Player ai, final SpellAbility sa) {
        return decision(cycleQualifies(ai, sa));
    }

    public static AiAbilityDecision cast(final Player ai, final SpellAbility sa) {
        int creatures = 0;
        for (final Card card : ai.getGame().getCardsIn(ZoneType.Battlefield)) {
            if (card.isCreature()) {
                creatures++; // Include tokens and survivors in the conservative draw bound.
            }
        }
        if (!safeDraw(ai, creatures) || !advantage(ai, sa, false)) {
            return decision(false);
        }
        if (cycleQualifies(ai, sa)) {
            for (final SpellAbility option : sa.getHostCard().getAllPossibleAbilities(ai, false)) {
                if (option.isCycling()) {
                    final SpellAbility cycle = option.copy(ai);
                    if (cycle.canCastTiming(ai) && cycle.canPlay() && cycle.checkRestrictions(ai)
                            && ComputerUtilCost.canPayCost(cycle, ai, false)) {
                        return decision(false); // Prefer an eligible, legal, payable cycling activation.
                    }
                }
            }
        }
        return decision(true);
    }
}
