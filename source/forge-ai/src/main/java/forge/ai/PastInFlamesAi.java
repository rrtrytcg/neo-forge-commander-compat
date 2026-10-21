package forge.ai;

import forge.card.mana.ManaCost;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;

/** Opt-in graveyard-value gate for Past in Flames; flashback spell choices remain native. */
public final class PastInFlamesAi {
    private PastInFlamesAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.getApi() == ApiType.PumpAll
                && "PastInFlames".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                && "Flashback".equals(sa.getParam("KW"))
                && "Graveyard".equals(sa.getParam("PumpZone"));
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        if (!uses(sa) || !ai.getGame().getStack().isEmpty()
                || !ai.getGame().getPhaseHandler().is(PhaseType.MAIN1, ai)
                    && !ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        final List<Card> graveyardSpells = new ArrayList<>();
        for (final Card card : ai.getCardsIn(ZoneType.Graveyard)) {
            if (card.isInstantOrSorcery() && card.getManaCost().countX() == 0
                    && !"Past in Flames".equals(card.getName())) {
                graveyardSpells.add(card);
            }
        }
        if (graveyardSpells.size() < 2) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        for (final Card card : graveyardSpells) {
            // Test the real combined colored costs: first this spell, then a potential
            // flashback spell whose granted cost equals its mana cost. Payment stays native.
            final ManaCost combinedMana = ManaCost.combine(sa.getPayCosts().getTotalMana(), card.getManaCost());
            final SpellAbility combined = sa.copyWithDefinedCost(new Cost(combinedMana, false));
            combined.setActivatingPlayer(ai);
            if (ComputerUtilMana.canPayManaCost(combined, ai, 0, false)) {
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            }
        }
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }
}
