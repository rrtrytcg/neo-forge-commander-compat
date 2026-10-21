package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CounterEnumType;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.Collection;
import java.util.List;

/** Simple, deterministic rules enabled only by the Adaptive Enchantment override profile. */
public final class AdaptiveEnchantmentAi {
    // Fixed order breaks ties without consuming game RNG. Multitype cards count toward each type.
    private static final List<String> PERMANENT_TYPES = List.of(
            "Artifact", "Battle", "Creature", "Enchantment", "Land", "Planeswalker");

    private AdaptiveEnchantmentAi() { }

    public static boolean usesLogic(final SpellAbility sa, final String logic) {
        return logic.equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    public static String recoveryType(final Player ai, final Collection<String> validTypes) {
        String best = "";
        int most = -1;
        for (final String type : PERMANENT_TYPES) {
            if (validTypes.contains(type)) {
                final int count = recoveryCount(ai, type);
                if (count > most) {
                    most = count;
                    best = type;
                }
            }
        }
        return best;
    }

    private static int recoveryCount(final Player ai, final String type) {
        int count = 0;
        for (final Card card : ai.getCardsIn(ZoneType.Graveyard)) {
            if (card.getType().hasStringType(type)) {
                count++;
            }
        }
        return count;
    }

    public static boolean wantsRecovery(final Player ai) {
        return recoveryCount(ai, recoveryType(ai, PERMANENT_TYPES)) >= 4;
    }

    /** Candidates have already passed the engine's targeting and attachment checks. */
    public static Card manaAuraLand(final Player ai, final List<Card> candidates, final boolean preferBasic) {
        Card first = null;
        for (final Card card : candidates) {
            if (!card.isLand() || !ai.equals(card.getController())) {
                continue;
            }
            if (first == null) {
                first = card;
            }
            if (preferBasic && card.isBasicLand()) {
                return card;
            }
        }
        return first;
    }

    // Estrid, the Masked: one loyalty decision in our second main. A Mask goes on our best unmasked
    // creature while Estrid stays at two or more loyalty and nothing needs untapping; otherwise +2.

    public static AiAbilityDecision estridUntap(final Player ai, final SpellAbility sa) {
        if (sa.getRestrictions() != null && !sa.getRestrictions().canPlay(sa.getHostCard(), sa)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlaySa);
        }
        if (!estridWindow(ai, sa) || estridMaskTarget(ai, sa.getHostCard()) != null) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    /** Sets the Mask target on success; the engine still checks legality and pays loyalty. */
    public static AiAbilityDecision estridMask(final Player ai, final SpellAbility sa) {
        sa.resetTargets();
        if (!estridWindow(ai, sa)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        final Card target = estridMaskTarget(ai, sa.getHostCard());
        if (target == null || !sa.canTarget(target)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        sa.getTargets().add(target);
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    private static boolean estridWindow(final Player ai, final SpellAbility sa) {
        return ai.equals(sa.getHostCard().getController()) && ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai)
                && ai.getGame().getStack().isEmpty();
    }

    /** Null when +2 is preferred: low loyalty, something to untap, or no creature worth a Mask. */
    static Card estridMaskTarget(final Player ai, final Card estrid) {
        if (estrid.getCounters(CounterEnumType.LOYALTY) < 3) {
            return null;
        }
        SpellAbility mask = null;
        for (final SpellAbility ability : estrid.getSpellAbilities()) {
            if (ability.getApi() == ApiType.UntapAll) {
                final CardCollection tapped = CardLists.filter(ai.getCardsIn(ZoneType.Battlefield), Card::isTapped);
                if (!CardLists.getValidCards(tapped, ability.getParamOrDefault("ValidCards", ""), ai, estrid, ability).isEmpty()) {
                    return null;
                }
            } else if (ability.getApi() == ApiType.Token && ability.usesTargeting()) {
                mask = ability;
            }
        }
        if (mask == null) {
            return null;
        }
        mask.setActivatingPlayer(ai);
        final SpellAbility tgt = mask;
        final CardCollection creatures = CardLists.filter(ai.getCreaturesInPlay(), c -> !c.equals(estrid)
                && !c.getEnchantedBy().anyMatch(a -> a.hasKeyword(Keyword.UMBRA_ARMOR)) && tgt.canTarget(c));
        return creatures.isEmpty() ? null : ComputerUtilCard.getBestCreatureAI(creatures);
    }
}
