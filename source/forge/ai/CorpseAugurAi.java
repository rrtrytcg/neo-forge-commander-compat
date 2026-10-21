package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/** Opt-in death-trigger targeting. The targeted graveyard sets X; the controller always draws. */
public final class CorpseAugurAi {
    private CorpseAugurAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.getApi() == ApiType.Draw
                && "CorpseAugur".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                && "TriggeredCardController".equals(sa.getParam("Defined"))
                && "X".equals(sa.getParam("NumCards"))
                && "TargetedPlayer$ValidGraveyard Creature.YouOwn".equals(sa.getSVar("X"));
    }

    public static boolean choose(final Player ai, final SpellAbility sa) {
        Player best = null;
        Player smallest = null;
        int bestX = -1;
        int smallestX = Integer.MAX_VALUE;
        // Seat order breaks ties deterministically; never inspect hidden card identities.
        for (final Player player : ai.getGame().getPlayers()) {
            sa.resetTargets();
            if (!sa.canTarget(player)) {
                continue;
            }
            sa.getTargets().add(player);
            final int x = AbilityUtils.calculateAmount(sa.getHostCard(), "X", sa);
            if (x < smallestX) {
                smallest = player;
                smallestX = x;
            }
            if (x <= ai.getLife() - 5 && x < ai.getCardsIn(ZoneType.Library).size() && x > bestX) {
                best = player;
                bestX = x;
            }
        }
        sa.resetTargets();
        final Player chosen = best != null ? best : smallest;
        if (chosen == null) {
            return false;
        }
        sa.getTargets().add(chosen);
        return sa.isTargetNumberValid();
    }
}
