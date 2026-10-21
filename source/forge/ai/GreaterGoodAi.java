package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.CostSacrifice;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Opt-in rescue of a doomed creature. Native paid-list LKI, payment and effects stay authoritative. */
public final class GreaterGoodAi {
    private record Plan(WeakReference<Player> player, WeakReference<Card> creature,
                        long incarnation, long sourceIncarnation, int power, int turn) { }
    // Neither keys nor values keep an old game alive. No card-ID or cross-game authorization.
    private static final Map<SpellAbility, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private GreaterGoodAi() { }

    public static boolean uses(final SpellAbility sa) {
        if (sa == null || !sa.isAbility() || sa.getApi() != ApiType.Draw || sa.getHostCard() == null
                || !"GreaterGood".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                || !"X".equals(sa.getParam("NumCards")) || !"Sacrificed$CardPower".equals(sa.getSVar("X"))
                || sa.usesTargeting() || sa.getSubAbility() == null
                || sa.getSubAbility().getApi() != ApiType.Discard
                || !"3".equals(sa.getSubAbility().getParam("NumCards")) || sa.getPayCosts() == null) {
            return false;
        }
        final CostSacrifice cost = sa.getPayCosts().getCostPartByType(CostSacrifice.class);
        return cost != null && "1".equals(cost.getAmount()) && "Creature".equals(cost.getType())
                && !cost.payCostFromSource();
    }

    private static boolean uncertain(final Player ai) {
        return DoomedCreatureAi.uncertain(ai, true);
    }

    private static boolean eligible(final Player ai, final SpellAbility sa, final Card card) {
        return card != null && card.getGame() == ai.getGame() && card.isInPlay() && card.isCreature()
                && card.getController() == ai && ai.getCardsIn(ZoneType.Battlefield).contains(card)
                && !card.isPhasedOut() && card.canBeSacrificedBy(sa, false)
                && card.getNetPower() >= 4 && ai.canDraw()
                && card.getStaticAbilities().stream().noneMatch(st -> !st.isSuppressed()
                        && st.getMode().contains(StaticAbilityMode.Continuous))
                && ai.getCardsIn(ZoneType.Library).size() - card.getNetPower() >= 4
                // Passing this Draw ability as a "saviour" suppresses native destroy prediction:
                // ask for the public no-rescue outcome, then retain native combat trade guards.
                && doomed(ai, card)
                && !ComputerUtilCombat.willOpposingCreatureDieInCombat(ai, card, ai.getGame().getCombat())
                && !ComputerUtilCombat.isDangerousToSacInCombat(ai, card, ai.getGame().getCombat());
    }

    private static boolean doomed(final Player ai, final Card card) {
        return DoomedCreatureAi.isDoomed(ai, card);
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        PLANS.remove(sa);
        if (!uses(sa) || uncertain(ai)) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        Card best = null;
        for (final Card card : ai.getCreaturesInPlay()) {
            if (!eligible(ai, sa, card)) { continue; }
            if (best == null || card.getNetPower() > best.getNetPower()
                    || card.getNetPower() == best.getNetPower() && card.getId() < best.getId()) { best = card; }
        }
        if (best == null) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        PLANS.put(sa, new Plan(new WeakReference<>(ai), new WeakReference<>(best), best.getGameTimestamp(),
                sa.getHostCard().getGameTimestamp(), best.getNetPower(), ai.getGame().getPhaseHandler().getTurn()));
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    public static Card payment(final Player ai, final SpellAbility sa) {
        final Plan plan = PLANS.get(sa);
        if (!uses(sa) || plan == null || plan.player().get() != ai
                || plan.turn() != ai.getGame().getPhaseHandler().getTurn()
                || sa.getHostCard().getController() != ai || !sa.getHostCard().isInPlay()
                || !ai.getCardsIn(ZoneType.Battlefield).contains(sa.getHostCard())
                || sa.getHostCard().getGameTimestamp() != plan.sourceIncarnation() || uncertain(ai)) { return null; }
        final Card card = plan.creature().get();
        return card != null && card.getGameTimestamp() == plan.incarnation() && card.getNetPower() == plan.power()
                && eligible(ai, sa, card) ? card : null;
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        // A plan left behind by a profile switch must fail closed as well.
        return !uses(sa) && !PLANS.containsKey(sa) || payment(ai, sa) != null;
    }
}
