package forge.ai;

import forge.card.CardType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.HashSet;
import java.util.Set;

/** Conservative public-board cast checks for table-wide choice sweepers. */
public final class ChoiceWipesAi {
    private static final String HARSH_MERCY = "Harsh Mercy";

    private ChoiceWipesAi() { }

    public static boolean uses(final SpellAbility sa) {
        if (sa == null || sa.getHostCard() == null) { return false; }
        final String hint = AiOverrideProfile.get().getAiLogic(sa.getHostCard());
        return HARSH_MERCY.equals(sa.getHostCard().getName()) && "HarshMercy".equals(hint);
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        if (!uses(sa)) { return null; }
        final boolean shouldPlay = ComputerUtilCost.canPayCost(sa, ai, false) && shouldPlayHarshMercy(ai);
        return new AiAbilityDecision(shouldPlay ? 100 : 0,
                shouldPlay ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
    }

    private static boolean shouldPlayHarshMercy(final Player ai) {
        final Set<String> protectedTypes = new HashSet<>();
        for (final Player player : ai.getGame().getPlayers()) {
            final String chosen = ComputerUtilCard.getMostProminentType(
                    player.getCardsIn(ZoneType.Battlefield), CardType.getAllCreatureTypes());
            // The native choice is undefined for an empty or changeling-only board.
            if (chosen.isEmpty()) { return false; }
            protectedTypes.add(chosen);
        }

        int friendlyLosses = 0;
        int opposingLosses = 0;
        for (final Card creature : ai.getGame().getCardsIn(ZoneType.Battlefield)) {
            if (!creature.isCreature() || creature.getType().getCreatureTypes().stream().anyMatch(protectedTypes::contains)
                    || !creature.canBeDestroyed()) {
                continue;
            }
            if (creature.getController().isOpponentOf(ai)) { opposingLosses++; }
            else { friendlyLosses++; }
        }
        return friendlyLosses == 0 && opposingLosses > 0;
    }

}
