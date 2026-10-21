package forge.ai;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/** Existing opt-in activation policy, using the shared combat evaluation unchanged. */
public final class MirrorEntityAi {
    private MirrorEntityAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && "MirrorEntity".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    public static Card preview(final Player ai, final Card card, final SpellAbility sa) {
        return CombatBaseStatsAi.preview(ai, card, sa);
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        return CombatBaseStatsAi.consider(ai, sa, MirrorEntityAi::uses);
    }
}
