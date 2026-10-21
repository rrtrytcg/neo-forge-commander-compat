package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/** Opt-in, pre-attack hand-size animation. Casting and the second-draw trigger remain native. */
public final class JolraelAi {
    private JolraelAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.isActivatedAbility() && sa.getApi() == ApiType.AnimateAll
                && "Jolrael".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    private static AiAbilityDecision hold() {
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        if (!uses(sa) || !"Jolrael, Mwonvuli Recluse".equals(source.getName())
                || !source.isInPlay() || source.isPhasedOut() || source.getController() != ai
                || ai.getGame().getCardState(source) != source
                || !ai.getGame().getPhaseHandler().is(PhaseType.COMBAT_BEGIN, ai)
                || !ai.getGame().getStack().isEmpty() || sa.getActivationsThisTurn() > 0
                || !"X".equals(sa.getParam("Power")) || !"X".equals(sa.getParam("Toughness"))
                || !"Creature.YouCtrl".equals(sa.getParam("ValidCards"))
                || !"Count$ValidHand Card.YouOwn".equals(sa.getSVar("X"))
                || sa.getSubAbility() != null || !CombatBaseStatsAi.previewKnown(ai, true)) {
            return hold();
        }
        // The empty-stack rule also prevents doubling up on another Jolrael's pending activation.
        // Recompute from native layered stats: counters and fixed additive effects still apply.
        int attackers = 0;
        int gain = 0;
        for (final Card creature : ai.getCreaturesInPlay()) {
            if (creature.isPhasedOut()) { continue; }
            final Card after = CombatBaseStatsAi.preview(ai, creature, sa);
            if (after.getNetPower() < creature.getNetPower()
                    || after.getNetToughness() < creature.getNetToughness()
                    || after.getNetToughness() <= 0) {
                return hold();
            }
            if (CombatUtil.canAttack(creature) && after.getNetPower() > creature.getNetPower()) {
                attackers++;
                gain += after.getNetPower() - creature.getNetPower();
            }
        }
        return attackers >= 2 && gain >= 4 && ComputerUtilCost.canPayCost(sa, ai, false)
                ? new AiAbilityDecision(100, AiPlayDecision.WillPlay) : hold();
    }

    /** Called before native payment in both AI execution entry points; no stored plans. */
    public static boolean preflight(final Player ai, final SpellAbility sa) {
        return !uses(sa) || consider(ai, sa).willingToPlay();
    }
}
