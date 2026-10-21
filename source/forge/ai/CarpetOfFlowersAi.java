package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/** Opt-in Carpet of Flowers casting and public Island-count trigger targeting. */
public final class CarpetOfFlowersAi {
    private CarpetOfFlowersAi() { }

    private static boolean tagged(final Card card) {
        return card != null && "Carpet of Flowers".equals(card.getName())
                && "CarpetOfFlowers".equals(AiOverrideProfile.get().getAiLogic(card));
    }

    public static boolean usesSpell(final SpellAbility sa) {
        return sa != null && sa.isSpell() && sa.getApi() == ApiType.PermanentNoncreature && tagged(sa.getHostCard());
    }

    private static boolean usesTrigger(final SpellAbility sa) {
        if (sa == null || !sa.isTrigger() || sa.getApi() != ApiType.Pump || !tagged(sa.getHostCard())
                || !sa.isCurse() || sa.getTargetRestrictions() == null || sa.getSubAbility() == null
                || sa.getSubAbility().getApi() != ApiType.Mana) {
            return false;
        }
        final String[] valid = sa.getTargetRestrictions().getValidTgts();
        return valid.length == 1 && "Opponent".equals(valid[0]);
    }

    private static int islandCount(final Player player) {
        return (int) player.getCardsIn(ZoneType.Battlefield).stream().filter(card -> card.getType().hasSubtype("Island")).count();
    }

    private static int maxOpponentIslands(final Player ai) {
        return ai.getOpponents().stream().mapToInt(CarpetOfFlowersAi::islandCount).max().orElse(0);
    }

    /** Null leaves the native permanent AI in charge; otherwise this is the approved no-payoff hold. */
    public static AiAbilityDecision checkSpell(final Player ai, final SpellAbility sa) {
        if (!usesSpell(sa)) { return null; }
        if (maxOpponentIslands(ai) == 0) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        return null;
    }

    /** Select the target yielding the most mana, using current public battlefield state. */
    public static AiAbilityDecision trigger(final Player ai, final SpellAbility sa) {
        if (!usesTrigger(sa)) { return null; }
        sa.resetTargets();
        Player best = null;
        int max = 0;
        for (final Player opponent : ai.getOpponents()) {
            if (!sa.canTarget(opponent)) { continue; }
            final int islands = islandCount(opponent);
            if (islands > max) {
                max = islands;
                best = opponent;
            }
        }
        if (best == null) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        sa.getTargets().add(best);
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }
}
