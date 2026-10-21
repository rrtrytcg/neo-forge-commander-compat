package forge.ai;

import forge.game.ability.ApiType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/** Opt-in casting policy for the original X{G/U}{G/U} base-P/T spell. */
public final class BiomassMutationAi {
    private BiomassMutationAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.isSpell() && sa.getApi() == ApiType.AnimateAll
                && "BiomassMutation".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                && "X".equals(sa.getParam("Power")) && "X".equals(sa.getParam("Toughness"))
                && "Creature.YouCtrl".equals(sa.getParam("ValidCards"))
                && !sa.hasParam("AddAllCreatureTypes") && !sa.hasParam("Types")
                && sa.getSubAbility() == null;
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        return CombatBaseStatsAi.consider(ai, sa, BiomassMutationAi::uses);
    }
}
