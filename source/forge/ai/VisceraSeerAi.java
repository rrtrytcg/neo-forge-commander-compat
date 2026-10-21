package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.CostSacrifice;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Opt-in scry from a creature already doomed by public removal or declared combat. */
public final class VisceraSeerAi {
    private record Plan(WeakReference<Player> player, WeakReference<Card> creature,
                        long incarnation, long sourceIncarnation, int turn) { }
    private static final Map<SpellAbility, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private VisceraSeerAi() { }

    public static boolean uses(final SpellAbility sa) {
        if (sa == null || !sa.isActivatedAbility() || sa.getHostCard() == null || sa.getApi() != ApiType.Scry
                || !"VisceraSeer".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                || !"1".equals(sa.getParam("ScryNum")) || sa.usesTargeting() || sa.getSubAbility() != null
                || sa.getPayCosts() == null || sa.getPayCosts().getCostParts().size() != 1) { return false; }
        final CostSacrifice cost = sa.getPayCosts().getCostPartByType(CostSacrifice.class);
        return cost != null && "1".equals(cost.getAmount()) && "Creature".equals(cost.getType()) && !cost.payCostFromSource();
    }

    private static boolean uncertain(final Player ai) {
        if (DoomedCreatureAi.uncertain(ai, false)) { return true; }
        for (final Card c : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (c.isPhasedOut()) { continue; }
            for (final var t : c.getTriggers()) {
                if (!t.isSuppressed() && t.zonesCheck(c.getZone())
                        && (t.getMode() == TriggerType.Scry || t.getMode() == TriggerType.AbilityCast
                            || t.getMode() == TriggerType.AbilityResolves || t.getMode() == TriggerType.SpellAbilityCast)) { return true; }
            }
            for (final var r : c.getReplacementEffects()) {
                if (!r.isSuppressed() && r.zonesCheck(c.getZone()) && r.requirementsCheck(ai.getGame())
                        && r.getMode() == ReplacementType.Scry) { return true; }
            }
        }
        return false;
    }

    private static boolean window(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        return !Thread.currentThread().isInterrupted() && source.isInPlay() && source.getController() == ai
                && ai.getCardsIn(ZoneType.Battlefield).contains(source) && !source.isPhasedOut()
                && sa.canPlay() && sa.checkRestrictions(ai) && !ai.getCardsIn(ZoneType.Library).isEmpty() && !uncertain(ai);
    }

    private static boolean eligible(final Player ai, final SpellAbility sa, final Card c) {
        if (c == null || c.getGame() != ai.getGame() || !c.isInPlay() || !c.isCreature() || c.getController() != ai
                || !ai.getCreaturesInPlay().contains(c) || c.isPhasedOut() || !c.canBeSacrificedBy(sa, false)
                || c.getStaticAbilities().stream().anyMatch(st -> !st.isSuppressed() && st.getMode().contains(StaticAbilityMode.Continuous))) { return false; }
        // Without a pending threat, only fully declared current combat can justify a sacrifice.
        final boolean declared = ai.getGame().getCombat() != null
                && ai.getGame().getPhaseHandler().is(PhaseType.COMBAT_DECLARE_BLOCKERS)
                && ai.getGame().getCombat().getAttackers().stream().allMatch(att ->
                    ai.getGame().getCombat().getBandOfAttacker(att) != null
                    && ai.getGame().getCombat().getBandOfAttacker(att).isBlocked() != null);
        if (!declared && ai.getGame().getStack().isEmpty()) { return false; }
        return DoomedCreatureAi.isDoomed(ai, c, !declared)
                && !ComputerUtilCombat.willOpposingCreatureDieInCombat(ai, c, ai.getGame().getCombat())
                && !ComputerUtilCombat.isDangerousToSacInCombat(ai, c, ai.getGame().getCombat());
    }

    private static Card choice(final Player ai, final SpellAbility sa) {
        Card best = null;
        for (final Card c : ai.getCreaturesInPlay()) {
            if (!eligible(ai, sa, c)) { continue; }
            final boolean self = c == sa.getHostCard();
            final boolean bestSelf = best == sa.getHostCard();
            if (best == null || bestSelf && !self || self == bestSelf
                    && (ComputerUtilCard.evaluateCreature(c) < ComputerUtilCard.evaluateCreature(best)
                        || ComputerUtilCard.evaluateCreature(c) == ComputerUtilCard.evaluateCreature(best) && c.getId() < best.getId())) { best = c; }
        }
        return best;
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        PLANS.remove(sa);
        final Card best = uses(sa) && window(ai, sa) ? choice(ai, sa) : null;
        if (best == null) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        PLANS.put(sa, new Plan(new WeakReference<>(ai), new WeakReference<>(best), best.getGameTimestamp(),
                sa.getHostCard().getGameTimestamp(), ai.getGame().getPhaseHandler().getTurn()));
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    public static Card payment(final Player ai, final SpellAbility sa) {
        final Plan p = PLANS.get(sa);
        if (!uses(sa) || p == null || p.player().get() != ai || p.turn() != ai.getGame().getPhaseHandler().getTurn()
                || sa.getHostCard().getGameTimestamp() != p.sourceIncarnation() || !window(ai, sa)) { return null; }
        final Card c = p.creature().get();
        return c != null && c.getGameTimestamp() == p.incarnation() && eligible(ai, sa, c) && choice(ai, sa) == c ? c : null;
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        return !uses(sa) && !PLANS.containsKey(sa) || payment(ai, sa) != null;
    }
}
