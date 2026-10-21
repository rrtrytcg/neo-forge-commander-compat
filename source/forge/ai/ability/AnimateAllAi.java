package forge.ai.ability;

import forge.ai.AiAbilityDecision;
import forge.ai.AiPlayDecision;
import forge.ai.ComputerUtilCard;
import forge.ai.SpellAbilityAi;
import forge.ai.MirrorEntityAi;
import forge.ai.BiomassMutationAi;
import forge.ai.ArcaneLighthouseAi;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

public class AnimateAllAi extends SpellAbilityAi {

    @Override
    protected AiAbilityDecision canPlay(Player aiPlayer, SpellAbility sa) {
        if (forge.ai.JolraelAi.uses(sa)) { return forge.ai.JolraelAi.consider(aiPlayer, sa); }
        if (ArcaneLighthouseAi.uses(sa)) {
            return ArcaneLighthouseAi.consider(aiPlayer, sa);
        }
        if (MirrorEntityAi.uses(sa) || BiomassMutationAi.uses(sa)) {
            return super.canPlay(aiPlayer, sa);
        }
        String logic = sa.getParamOrDefault("AILogic", "");

        if ("CreatureAdvantage".equals(logic) && !aiPlayer.getCreaturesInPlay().isEmpty()) {
            // TODO: improve this or implement a better logic for abilities like Oko, the Trickster ultimate
            for (Card c : aiPlayer.getCreaturesInPlay()) {
                if (ComputerUtilCard.doesCreatureAttackAI(aiPlayer, c)) {
                    return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                }
            }
        }

        if ("Always".equals(logic)) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    @Override
    protected AiAbilityDecision checkApiLogic(Player aiPlayer, SpellAbility sa) {
        if (BiomassMutationAi.uses(sa)) {
            return BiomassMutationAi.consider(aiPlayer, sa);
        }
        return MirrorEntityAi.uses(sa) ? MirrorEntityAi.consider(aiPlayer, sa) : super.checkApiLogic(aiPlayer, sa);
    }

    @Override
    protected AiAbilityDecision doTriggerNoCost(Player aiPlayer, SpellAbility sa, boolean mandatory) {
        if (mandatory) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }
        return canPlay(aiPlayer, sa);
    }

}
