package forge.ai;

import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CounterEnumType;
import forge.game.combat.Combat;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.Set;

/**
 * Opt-in utility abilities for four single-blocker lands (profile {@code utility-lands}). Mana abilities,
 * costs, targeting legality and resolution stay native; these rules only decide when to activate.
 */
public final class UtilityLandsAi {
    private UtilityLandsAi() { }

    private static String logic(final Card host) {
        return host == null ? "" : AiOverrideProfile.get().getAiLogic(host);
    }

    /** Null when the ability is not one of ours; the caller then keeps native behaviour. */
    public static AiAbilityDecision decide(final Player ai, final SpellAbility sa) {
        final Card land = sa.getHostCard();
        if (sa.isManaAbility() || !sa.isActivatedAbility() || land == null) {
            return null;
        }
        final AiAbilityDecision core = PhyrexiasCoreAi.decide(ai, sa);
        if (core != null) {
            return core;
        }
        final Boolean yes = switch (logic(land)) {
            case "Labyrinth" -> sa.getApi() == ApiType.RemoveFromCombat ? labyrinth(ai, sa) : null;
            case "Helios" -> sa.getApi() == ApiType.PutCounter ? energyAtLastEndStep(ai, sa)
                    : sa.getApi() == ApiType.Destroy ? helios(ai, sa) : null;
            case "Stage" -> sa.getApi() == ApiType.Clone ? stage(ai, sa) : null;
            default -> null;
        };
        if (yes == null) {
            return null;
        }
        if (yes && sa.getRestrictions() != null && !sa.getRestrictions().canPlay(land, sa)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlaySa);
        }
        return yes ? new AiAbilityDecision(100, AiPlayDecision.WillPlay) : new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    private static boolean ready(final Player ai, final Card land) {
        return land.isInPlay() && ai.equals(land.getController()) && !land.isTapped() && !land.isPhasedOut();
    }

    /** Untapped lands we control other than {@code land}; a simple, public spare-mana estimate. */
    private static int otherUntappedLands(final Player ai, final Card land) {
        return CardLists.count(ai.getLandsInPlay(), c -> !c.equals(land) && !c.isTapped());
    }

    /** HELIOS energy: end step of the player right before us, with one other untapped land to spare. */
    private static boolean energyAtLastEndStep(final Player ai, final SpellAbility sa) {
        final Card land = sa.getHostCard();
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        return ready(ai, land) && ph.is(PhaseType.END_OF_TURN) && ph.getPlayerTurn().isOpponentOf(ai)
                && ai.equals(ph.getNextTurn()) && ai.getGame().getStack().isEmpty() && otherUntappedLands(ai, land) >= 1;
    }

    /** Labyrinth: after blocks on an opponent's turn, remove the biggest unblocked attacker that hurts us. */
    private static boolean labyrinth(final Player ai, final SpellAbility sa) {
        sa.resetTargets();
        final Card land = sa.getHostCard();
        final Game game = ai.getGame();
        final Combat combat = game.getCombat();
        final PhaseHandler ph = game.getPhaseHandler();
        if (!ready(ai, land) || combat == null || !ph.is(PhaseType.COMBAT_DECLARE_BLOCKERS)
                || !ph.getPlayerTurn().isOpponentOf(ai) || otherUntappedLands(ai, land) < 4) {
            return false;
        }
        Card best = null;
        int toPlayer = 0;
        for (final Card attacker : combat.getAttackers()) {
            final GameEntity defender = combat.getDefenderByAttacker(attacker);
            final boolean atUs = ai.equals(defender) || (defender instanceof Card && ai.equals(((Card) defender).getController()));
            if (!atUs || combat.isBlocked(attacker) || !combat.getBlockers(attacker).isEmpty()) {
                continue;
            }
            if (ai.equals(defender)) {
                toPlayer += ComputerUtilCombat.damageIfUnblocked(attacker, ai, combat, false);
            }
            if (sa.canTarget(attacker) && (best == null || attacker.getNetCombatDamage() > best.getNetCombatDamage())) {
                best = attacker;
            }
        }
        if (best == null || (best.getNetCombatDamage() < 4 && toPlayer < ai.getLife())) {
            return false;
        }
        sa.getTargets().add(best);
        return true;
    }

    /** HELIOS One: our main phase, best opposing nonland permanent with 3 <= MV <= energy, keeping five other lands. */
    private static boolean helios(final Player ai, final SpellAbility sa) {
        sa.resetTargets();
        final Card land = sa.getHostCard();
        final Game game = ai.getGame();
        final PhaseHandler ph = game.getPhaseHandler();
        if (!ready(ai, land) || !ph.isPlayerTurn(ai) || !ph.getPhase().isMain() || !game.getStack().isEmpty()
                || ai.getLandsInPlay().size() - 1 < 5) {
            return false;
        }
        final int energy = ai.getCounters(CounterEnumType.ENERGY);
        final CardCollection options = CardLists.filter(game.getCardsIn(ZoneType.Battlefield), c -> !c.isLand()
                && c.getController().isOpponentOf(ai) && c.getCMC() >= 3 && c.getCMC() <= energy);
        // X decides both the energy paid and which mana value is targetable, so check each target at its own X.
        final CardCollection legal = new CardCollection();
        for (final Card c : options) {
            sa.setXManaCostPaid(c.getCMC());
            if (sa.canTarget(c)) {
                legal.add(c);
            }
        }
        if (legal.isEmpty()) {
            sa.setXManaCostPaid(null);
            return false;
        }
        final Card target = ComputerUtilCard.getBestAI(legal);
        sa.setXManaCostPaid(target.getCMC());
        sa.getTargets().add(target);
        return true;
    }

    /** Thespian's Stage: once, in our second main, copy a basic land of our scarcest commander colour. */
    private static boolean stage(final Player ai, final SpellAbility sa) {
        sa.resetTargets();
        final Card land = sa.getHostCard();
        final Game game = ai.getGame();
        if (!ready(ai, land) || !"Thespian's Stage".equals(land.getName()) || !game.getPhaseHandler().is(PhaseType.MAIN2, ai)
                || !game.getStack().isEmpty() || otherUntappedLands(ai, land) < 2) {
            return false;
        }
        final ColorSet identity = ai.getCommanderColorID();
        String color = null;
        int fewest = Integer.MAX_VALUE;
        for (final String c : MagicColor.Constant.ONLY_COLORS) {
            if (identity != null && !identity.hasAnyColor(MagicColor.fromName(c))) {
                continue;
            }
            final int sources = CardLists.count(ai.getLandsInPlay(), l -> l.canProduceColorMana(Set.of(c)));
            if (sources < fewest) {
                fewest = sources;
                color = c;
            }
        }
        if (color == null) {
            return false;
        }
        final String basic = MagicColor.Constant.BASIC_LANDS.get(MagicColor.Constant.ONLY_COLORS.indexOf(color));
        final CardCollection basics = CardLists.filter(game.getCardsIn(ZoneType.Battlefield), c -> c.isBasicLand()
                && !c.getType().isLegendary() && !c.equals(land) && c.getType().hasSubtype(basic) && sa.canTarget(c));
        if (basics.isEmpty()) {
            return false;
        }
        // Prefer our own basic so an opponent's board change cannot matter; otherwise any legal basic.
        final CardCollection own = CardLists.filterControlledBy(basics, ai);
        sa.getTargets().add((own.isEmpty() ? basics : own).getFirst());
        return true;
    }

    /** Dakmor Salvage dredge: replace the draw only when we need a land and the library is safe. Null = not ours. */
    public static Boolean dredge(final ReplacementEffect re, final Card host) {
        if (host == null || !"Dakmor".equals(logic(host)) || !"Draw".equals(re.getMode().name())
                || !re.getDescription().contains("Dredge")) {
            return null;
        }
        final Player ai = host.getController();
        return CardLists.count(ai.getCardsIn(ZoneType.Hand), Card::isLand) == 0
                && ai.getLandsInPlay().size() < 7 && ai.getCardsIn(ZoneType.Library).size() >= 20;
    }
}
