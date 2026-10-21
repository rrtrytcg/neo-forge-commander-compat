package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.combat.Combat;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/** Opt-in metalcraft removal decisions; the original spell still taps and conditionally exiles. */
public final class DispatchAi {
    private DispatchAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && "DispatchRemoval".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        sa.resetTargets();
        if (!ai.hasMetalcraft()) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        CardCollection candidates = CardLists.getTargetableCards(ai.getOpponents().getCardsIn(ZoneType.Battlefield), sa);
        candidates.removeIf(c -> !c.isCreature());
        candidates = ComputerUtil.filterAITgts(sa, ai, candidates, false);
        final Combat combat = ai.getGame().getCombat();
        CardCollection priority = new CardCollection();
        if (combat != null) {
            priority = CardLists.filter(candidates, c -> combat.isAttacking(c)
                    && ai.equals(combat.getDefenderPlayerByAttacker(c)));
            if (priority.isEmpty()) {
                priority = CardLists.filter(candidates, c -> combat.getAttackersBlockedBy(c).stream()
                        .anyMatch(attacker -> attacker.getController().equals(ai)));
            }
        }
        final Card best = ComputerUtilCard.getBestCreatureAI(priority.isEmpty() ? candidates : priority);
        if (best == null) {
            return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
        }
        sa.getTargets().add(best);
        if (!sa.isTargetNumberValid() || !ComputerUtilCost.canPayCost(sa, ai, false)) {
            sa.resetTargets();
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }
}
