package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CounterEnumType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.phase.PhaseType;

/** Conservative public-board forecast for the opt-in Divine Reckoning profile. */
public final class DivineReckoningAi {
    private static final String PROFILE = "divine-reckoning";
    private static final String CARD_NAME = "Divine Reckoning";

    private DivineReckoningAi() { }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        if (!uses(sa)) { return null; }
        final boolean play = shouldPlay(ai);
        return new AiAbilityDecision(play ? 100 : 0,
                play ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
    }

    private static boolean uses(final SpellAbility sa) {
        return sa != null && sa.getHostCard() != null && CARD_NAME.equals(sa.getHostCard().getName())
                && PROFILE.equals(AiOverrideProfile.get().getName());
    }

    private static boolean shouldPlay(final Player ai) {
        if (ai == null || !ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai)
                || !ai.getGame().getStack().isEmpty()) {
            return false;
        }

        int friendlyLoss = 0;
        int opposingLoss = 0;
        for (final Player player : ai.getGame().getPlayers()) {
            if (!player.getController().isAI()) { return false; }
            final CardCollection creatures = player.getCreaturesInPlay();
            final Card kept = ComputerUtilCard.getBestAI(creatures);
            final CardCollection lost = new CardCollection();
            for (final Card creature : creatures) {
                if (creature == kept || !creature.canBeDestroyed() || creature.getShieldCount() > 0
                        || creature.getCounters(CounterEnumType.SHIELD) > 0) {
                    continue;
                }
                lost.add(creature);
            }
            final int value = ComputerUtilCard.evaluateCreatureList(lost);
            if (player.isOpponentOf(ai)) { opposingLoss += value; }
            else { friendlyLoss += value; }
        }
        return opposingLoss > friendlyLoss;
    }
}
