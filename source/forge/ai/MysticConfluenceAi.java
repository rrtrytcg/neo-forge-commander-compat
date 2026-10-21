package forge.ai;

import forge.game.ability.ApiType;
import forge.game.ability.effects.CharmEffect;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.List;

/** Conservative, deterministic mode selection for the opt-in Mystic Confluence profile. */
public final class MysticConfluenceAi {
    private MysticConfluenceAi() { }

    public static boolean uses(SpellAbility sa) {
        return "MysticConfluence".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    public static AiAbilityDecision consider(Player ai, SpellAbility sa) {
        // Match CharmAi's normal preflight cleanup so repeated evaluations cannot retain stale modes.
        sa.setChosenList(null);
        sa.setSubAbility(null);
        List<AbilitySub> choices = CharmEffect.makePossibleOptions(sa);
        AbilitySub counter = find(choices, ApiType.Counter);
        AbilitySub bounce = find(choices, ApiType.ChangeZone);
        AbilitySub draw = find(choices, ApiType.Draw);
        if (bounce == null || draw == null) {
            return decline();
        }

        // Ask each mode's native AI to choose and assess its own legal target.
        boolean counterUseful = counter != null && useful(ai, sa, counter);
        Card bounceTarget = CardLists.getTargetableCards(ai.getGame().getCardsIn(ZoneType.Battlefield), bounce).stream()
                .filter(Card::isCreature)
                .filter(card -> card.getController().isOpponentOf(ai))
                .max((left, right) -> Double.compare(ComputerUtilCard.evaluateCreature(left), ComputerUtilCard.evaluateCreature(right)))
                .orElse(null);
        bounce.resetTargets();
        boolean bounceUseful = bounceTarget != null && bounce.canTarget(bounceTarget);
        if (bounceUseful) bounce.getTargets().add(bounceTarget);
        if (!counterUseful && !bounceUseful) {
            sa.setChosenList(null);
            sa.setSubAbility(null);
            return decline();
        }

        List<AbilitySub> selected = new ArrayList<>(3);
        if (counterUseful) selected.add(counter);
        if (bounceUseful) selected.add(bounce);
        while (selected.size() < 3) selected.add(draw);

        sa.setChosenList(selected);
        sa.setSubAbility(null);
        CharmEffect.chainAbilities(sa, selected);
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    private static boolean useful(Player ai, SpellAbility root, AbilitySub mode) {
        root.setSubAbility(null);
        mode.setActivatingPlayer(ai);
        AiAbilityDecision decision = SpellApiToAi.Converter.get(mode).canPlayWithSubs(ai, mode);
        return decision.willingToPlay();
    }

    private static AbilitySub find(List<AbilitySub> choices, ApiType api) {
        return choices.stream().filter(mode -> mode.getApi() == api).findFirst().orElse(null);
    }

    private static AiAbilityDecision decline() {
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }
}
