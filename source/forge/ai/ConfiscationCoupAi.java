package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CounterEnumType;
import forge.game.ability.ApiType;
import forge.game.cost.Cost;
import forge.game.cost.CostPayEnergy;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/** Narrow opt-in targeting and energy-payment policy for Confiscation Coup. */
public final class ConfiscationCoupAi {
    private static final int ENERGY_GRANTED = 4;

    private ConfiscationCoupAi() { }

    public static boolean uses(SpellAbility sa) {
        return sa != null && "ConfiscationCoup".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    /** Replace the native counter-placement check with the actual Coup payoff check. */
    public static AiAbilityDecision consider(Player ai, SpellAbility sa) {
        if (!uses(sa) || sa.getApi() != ApiType.PutCounter) { return null; }
        SpellAbility gain = sa.getSubAbility();
        if (gain == null || gain.getApi() != ApiType.GainControl) {
            return new AiAbilityDecision(0, AiPlayDecision.MissingNeededCards);
        }
        return SpellApiToAi.Converter.get(gain).canPlayWithSubs(ai, gain).willingToPlay()
                ? new AiAbilityDecision(100, AiPlayDecision.WillPlay)
                : new AiAbilityDecision(0, AiPlayDecision.MissingNeededCards);
    }

    /** Keep ControlGainAi's native ranking, but only let it consider targets payable from this spell. */
    public static void filterAffordableTargets(SpellAbility sa, CardCollection candidates) {
        if (!uses(sa)) { return; }
        candidates.removeIf(card -> !isAffordableTarget(card));
    }

    public static boolean isAffordableTarget(Card card) {
        return card != null && card.getCMC() <= ENERGY_GRANTED;
    }

    /** The native UnlessSwitched guard assumes its source is a permanent on the battlefield. */
    public static boolean mayPayEnergy(Player payer, SpellAbility sa, Cost cost) {
        if (!uses(sa) || payer == null || cost == null) { return false; }
        Card target = sa.getTargetCard();
        CostPayEnergy energy = cost.getCostEnergy();
        if (!isAffordableTarget(target) || energy == null || !sa.canTarget(target)
                || !target.getController().isOpponentOf(payer) || !target.canBeControlledBy(payer)) { return false; }
        int amount = energy.getAbilityAmount(sa);
        return amount == target.getCMC()
                && amount <= ENERGY_GRANTED
                && payer.getCounters(CounterEnumType.ENERGY) >= amount;
    }
}
