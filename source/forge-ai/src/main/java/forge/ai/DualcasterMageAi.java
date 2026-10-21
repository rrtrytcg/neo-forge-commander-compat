package forge.ai;

import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

import java.util.List;

/** Opt-in target guard for Dualcaster Mage's spell-copy trigger. */
public final class DualcasterMageAi {
    private DualcasterMageAi() { }

    public static boolean uses(SpellAbility sa) {
        return sa != null && "DualcasterMage".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    public static AiAbilityDecision consider(Player ai, SpellAbility sa) {
        if (sa.getTargetRestrictions() != null) {
            for (GameEntity candidate : sa.getTargetRestrictions().getAllCandidates(sa)) {
                if (candidate instanceof Card stackCard && isOpponentInstantOrSorcery(ai, stackCard.getCastSA())
                        && sa.canTarget(stackCard) && stackCard.getCastSA().canBeTargetedBy(sa)) {
                    sa.resetTargets();
                    sa.getTargets().add(stackCard.getCastSA());
                    return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                }
            }
        }
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    public static SpellAbility chooseTarget(Player ai, List<SpellAbility> candidates) {
        return candidates.stream().filter(spell -> isOpponentInstantOrSorcery(ai, spell)).findFirst().orElse(null);
    }

    private static boolean isOpponentInstantOrSorcery(Player ai, SpellAbility spell) {
        return spell != null && spell.getHostCard() != null && spell.getActivatingPlayer() != null
                && spell.getActivatingPlayer().isOpponentOf(ai)
                && (spell.getHostCard().isInstant() || spell.getHostCard().isSorcery())
                && !spell.cantBeCopied();
    }
}
