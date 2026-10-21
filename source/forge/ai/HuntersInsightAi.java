package forge.ai;

import forge.game.GameEntity;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Opt-in single-step unblocked combat draw. Native targets, costs, damage and draw triggers remain authoritative. */
public final class HuntersInsightAi {
    // Values contain seat IDs only, never a reference back to a game or Combat key.
    private static final Map<Combat, Set<Integer>> CAST = Collections.synchronizedMap(new WeakHashMap<>());
    private HuntersInsightAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.isSpell() && sa.getApi() == ApiType.Effect && sa.getHostCard() != null
                && "HuntersInsight".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                && "Creature.YouCtrl".equals(sa.getParam("ValidTgts"))
                && "TrigDamage".equals(sa.getParam("Triggers"))
                && "Targeted".equals(sa.getParam("RememberObjects"));
    }

    private static boolean uncertain(final Player ai) {
        for (final Card card : ai.getGame().getCardsIn(List.of(ZoneType.Battlefield, ZoneType.Graveyard, ZoneType.Command))) {
            if (card.isPhasedOut()) { continue; }
            for (final Trigger trigger : card.getTriggers()) {
                if (trigger.isSuppressed() || !trigger.zonesCheck(card.getZone())) { continue; }
                final TriggerType type = trigger.getMode();
                if (type == TriggerType.Drawn || type == TriggerType.DamageDone || type == TriggerType.DamageAll
                        || type == TriggerType.DamageDoneOnce || type == TriggerType.DamageDealtOnce
                        || type == TriggerType.DamageDoneOnceByController || type == TriggerType.DamagePreventedOnce
                        || type == TriggerType.SpellCast || type == TriggerType.SpellCastOrCopy
                        || type == TriggerType.SpellAbilityCast || type == TriggerType.SpellCopy
                        || type == TriggerType.SpellAbilityCopy || type == TriggerType.BecomesTarget
                        || type == TriggerType.BecomesTargetOnce) { return true; }
            }
            for (final ReplacementEffect replacement : card.getReplacementEffects()) {
                final String zones = replacement.getParamOrDefault("ActiveZones", "Battlefield");
                if (replacement.isSuppressed() || !zones.contains(card.getZone().getZoneType().name())
                        || !replacement.requirementsCheck(ai.getGame())) { continue; }
                final ReplacementType type = replacement.getMode();
                if (type == ReplacementType.Draw || type == ReplacementType.DrawCards
                        || type == ReplacementType.DamageDone || type == ReplacementType.DealtDamage
                        || type == ReplacementType.AssignDealDamage) { return true; }
            }
            // Defer continuous P/T sources: spending this spell can change hand/graveyard
            // counts before damage (e.g. Maro or Runechanter's Pike).
            if (card.isInPlay() && card.getStaticAbilities().stream().anyMatch(st -> !st.isSuppressed()
                    && st.getMode().contains(StaticAbilityMode.Continuous)
                    && (st.hasParam("AddPower") || st.hasParam("SetPower") || st.hasParam("AddToughness")
                        || st.hasParam("SetToughness") || st.hasParam("SetPowerToughness")))) { return true; }
        }
        return false;
    }

    private static boolean window(final Player ai) {
        final Combat combat = ai.getGame().getCombat();
        return combat != null && ai.getGame().getPhaseHandler().is(PhaseType.COMBAT_DECLARE_BLOCKERS, ai)
                && ai.getGame().getStack().isEmpty() && ai.canDraw()
                && !CAST.getOrDefault(combat, Collections.emptySet()).contains(ai.getId()) && !uncertain(ai);
    }

    private static int forecast(final Player ai, final SpellAbility sa, final Card card) {
        final Combat combat = ai.getGame().getCombat();
        if (combat == null || card == null || !card.isInPlay() || card.isPhasedOut()
                || card.getController() != ai || !card.isCreature() || !sa.canTarget(card)
                || !combat.isAttacking(card) || combat.getBandOfAttacker(card) == null
                || !Boolean.FALSE.equals(combat.getBandOfAttacker(card).isBlocked())
                || card.hasFirstStrike() || card.hasDoubleStrike()) { return 0; }
        final GameEntity defender = combat.getDefenderByAttacker(card);
        final Player owner = defender instanceof Player ? (Player) defender
                : defender instanceof Card && ((Card) defender).isPlaneswalker() ? ((Card) defender).getController() : null;
        if (owner == null || !owner.isOpponentOf(ai)) { return 0; }
        final int damage = ComputerUtilCombat.damageIfUnblocked(card, defender, combat, false);
        return damage >= 3 && ai.getCardsIn(ZoneType.Library).size() - damage >= 4 ? damage : 0;
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        sa.resetTargets();
        if (!window(ai)) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        Card best = null;
        int bestDraw = 0;
        for (final Card card : ai.getGame().getCombat().getAttackers()) {
            final int draw = forecast(ai, sa, card);
            if (draw > bestDraw || draw > 0 && draw == bestDraw && card.getId() < best.getId()) {
                best = card; bestDraw = draw;
            }
        }
        if (best == null) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        sa.getTargets().add(best);
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        return !uses(sa) || window(ai) && sa.isTargetNumberValid() && sa.getTargets().size() == 1
                && forecast(ai, sa, sa.getTargetCard()) > 0;
    }

    /** Called only after normal native payment and stack insertion, never during evaluation. */
    public static void cast(final Player ai, final SpellAbility sa) {
        if (uses(sa) && ai.getGame().getCombat() != null) {
            CAST.computeIfAbsent(ai.getGame().getCombat(), key -> new HashSet<>()).add(ai.getId());
        }
    }
}
