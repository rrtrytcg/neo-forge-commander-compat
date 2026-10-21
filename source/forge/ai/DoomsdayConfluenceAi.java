package forge.ai;

import forge.game.ability.ApiType;
import forge.game.ability.effects.CharmEffect;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;

/** Opt-in XXB refuel: one safe edict, one multiplayer discard, otherwise repeated native Daleks. */
public final class DoomsdayConfluenceAi {
    private DoomsdayConfluenceAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.isSpell() && sa.getApi() == ApiType.Charm
                && "Doomsday Confluence".equals(sa.getHostCard().getName())
                && "DoomsdayConfluence".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    private static void clear(final SpellAbility sa) {
        sa.setChosenList(null);
        sa.setSubAbility(null);
        sa.setXManaCostPaid(null); // native Charm X calculation otherwise reuses the old value
    }

    private static AiAbilityDecision hold(final SpellAbility sa) {
        clear(sa); sa.setXManaCostPaid(0);
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    private static boolean original(final SpellAbility sa) {
        if (!"X".equals(sa.getParam("CharmNum")) || !"X".equals(sa.getParam("Announce"))
                || !"Count$xPaid".equals(sa.getSVar("X")) || !"True".equals(sa.getParam("CanRepeatModes"))
                || !"DBSac,DBToken,DBDiscard".equals(sa.getParam("Choices"))
                || sa.getPayCosts() == null || !sa.getPayCosts().isOnlyManaCost()
                || sa.getPayCosts().getTotalMana().countX() != 2) { return false; }
        final var modes = sa.getAdditionalAbilityList("Choices");
        if (modes == null || modes.size() != 3 || modes.stream().anyMatch(m -> m.getSubAbility() != null)) { return false; }
        return modes.get(0).getApi() == ApiType.Sacrifice && "Player".equals(modes.get(0).getParam("Defined"))
                && "Creature.nonArtifact".equals(modes.get(0).getParam("SacValid"))
                && modes.get(1).getApi() == ApiType.Token && "b_3_3_a_dalek_menace".equals(modes.get(1).getParam("TokenScript"))
                && modes.get(2).getApi() == ApiType.Discard && "Opponent".equals(modes.get(2).getParam("Defined"))
                && "TgtChoose".equals(modes.get(2).getParam("Mode"));
    }

    private static boolean nonartifactCreature(final Card card) {
        return card.isCreature() && !card.isArtifact() && !card.isPhasedOut();
    }

    private static boolean safeSacrifice(final Player ai, final SpellAbility mode) {
        for (final Player player : ai.getGame().getPlayers()) {
            if (!player.isOpponentOf(ai) && player.getCreaturesInPlay().stream().anyMatch(DoomsdayConfluenceAi::nonartifactCreature)) {
                return false;
            }
        }
        return ai.getOpponents().stream().anyMatch(player -> player.getCreaturesInPlay().stream()
                .anyMatch(card -> nonartifactCreature(card) && card.canBeSacrificedBy(mode, true)));
    }

    public static AiAbilityDecision decide(final Player ai, final SpellAbility sa) {
        if (!uses(sa)) { return null; }
        clear(sa);
        final Card source = sa.getHostCard();
        if (!original(sa) || !source.isInZone(ZoneType.Hand) || source.getOwner() != ai || source.getController() != ai
                || ai.getGame().getCardState(source) != source
                || !ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai)
                || !ai.getGame().getStack().isEmpty() || !sa.checkRestrictions(ai)) { return hold(sa); }
        final int x = ComputerUtilCost.setMaxXValue(sa, ai, false);
        if (x < 1) { return hold(sa); }
        sa.setXManaCostPaid(x);
        final List<AbilitySub> modes = CharmEffect.makePossibleOptions(sa);
        for (final AbilitySub mode : modes) { mode.setActivatingPlayer(ai); }
        final List<AbilitySub> chosen = new ArrayList<>();
        if (safeSacrifice(ai, modes.get(0))) { chosen.add(modes.get(0)); }
        if (chosen.size() < x && ai.getOpponents().stream().filter(player -> !player.getCardsIn(ZoneType.Hand).isEmpty()).count() >= 2) {
            chosen.add(modes.get(2));
        }
        while (chosen.size() < x) { chosen.add(modes.get(1)); }
        sa.setChosenList(chosen);
        CharmEffect.chainAbilities(sa, chosen); // native printed order, with a fresh clone for every repeat
        if (!ComputerUtilCost.canPayCost(sa, ai, false)) { return hold(sa); }
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    /** Rebuild the native X/mode chain from fresh public state before any cost or source mutation. */
    public static boolean preflight(final Player ai, final SpellAbility sa) {
        return !uses(sa) || decide(ai, sa).willingToPlay();
    }
}
