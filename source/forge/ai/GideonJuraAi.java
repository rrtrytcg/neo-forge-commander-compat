package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/** Opt-in, bounded activation policy for Gideon Jura. */
public final class GideonJuraAi {
    private static final String LOGIC = "GideonJuraDestroyOnly";

    private GideonJuraAi() {}

    public static boolean uses(SpellAbility sa) {
        return sa != null && "Gideon Jura".equals(sa.getHostCard().getName())
                && LOGIC.equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    /** Null means the native DestroyAi should continue with its ordinary target ranking. */
    public static AiAbilityDecision considerDestroy(Player ai, SpellAbility sa) {
        if (!uses(sa)) {
            return null;
        }
        if (sa.getApi() != ApiType.Destroy || !sa.hasParam("Planeswalker")) {
            return declined();
        }

        Card source = sa.getHostCard();
        if (!source.isInPlay() || source.getCounters(CounterEnumType.LOYALTY) < 2) {
            return declined();
        }
        if (!ai.getGame().getStack().isEmpty()) {
            return declined();
        }
        if (!ai.getGame().getPhaseHandler().isPlayerTurn(ai)
                || !(ai.getGame().getPhaseHandler().is(PhaseType.MAIN1)
                        || ai.getGame().getPhaseHandler().is(PhaseType.MAIN2))) {
            return declined();
        }

        CardCollection targets = CardLists.getTargetableCards(ai.getOpponents().getCardsIn(ZoneType.Battlefield), sa);
        if (targets.isEmpty()) {
            return declined();
        }

        // Keep native DestroyAi target choice, target legality, loyalty payment, and resolution.
        return null;
    }

    /** Holds the unsupported +2 and 0 modes while this profile is active. */
    public static AiAbilityDecision declineUnsupported(SpellAbility sa) {
        if (!uses(sa) || sa.getApi() == ApiType.Destroy) {
            return null;
        }
        return declined();
    }

    private static AiAbilityDecision declined() {
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }
}
