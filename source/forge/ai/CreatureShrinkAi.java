package forge.ai;

import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.replacement.ReplacementLayer;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;

import java.util.Map;

/** Shared bounded -X/-X casualty preview. Does not predict responses or death-trigger chains. */
public final class CreatureShrinkAi {
    private CreatureShrinkAi() { }

    static boolean destroyReplacement(final Card card, final SpellAbility sa, final boolean regeneration) {
        final Map<AbilityKey, Object> params = AbilityKey.mapFromAffected(card);
        params.put(AbilityKey.Cause, sa);
        params.put(AbilityKey.Regeneration, regeneration);
        for (final ReplacementLayer layer : ReplacementLayer.values()) {
            if (!card.getGame().getReplacementHandler().getReplacementList(ReplacementType.Destroy, params, layer).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 1 = removed, 0 = survives, -1 = uncertain. Callers must account for dying static abilities. */
    public static int casualty(final Card card, final SpellAbility sa, final int amount) {
        if (card.isPhasedOut()) {
            return 0;
        }
        final Card copy = CardCopyService.getLKICopy(card);
        copy.setDamage(card.getDamage());
        copy.addPTBoost(-amount, -amount, Long.MAX_VALUE, 0);
        if (copy.getNetToughness() <= 0) {
            return 1; // Zero toughness bypasses indestructibility, shields and regeneration.
        }
        if (copy.getDamage() <= 0 || copy.getDamage() < copy.getLethal() || !card.canBeDestroyed()
                || card.getShieldCount() > 0) {
            return 0;
        }
        if (card.hasKeyword("CARDNAME can't be destroyed by lethal damage unless lethal damage dealt by a single source is marked on it.")) {
            return -1;
        }
        return destroyReplacement(card, null, true) ? -1 : 1;
    }
}
