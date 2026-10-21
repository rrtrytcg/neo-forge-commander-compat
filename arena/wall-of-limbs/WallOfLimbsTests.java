import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/**
 * Bounded fixture for the Wall of Limbs AI rules, opted in by the NeoForge override profile
 * (aiLogic=Wall of Limbs=WallOfLimbs).
 *
 *  - Trigger 1: drain (X = the wall's power) of 6 or more.
 *  - Trigger 2: the drain is lethal to any opponent, even below 6.
 *
 * Builds real Forge games, calls the ability decision directly, and asserts the upstream default
 * (no profile) is unchanged. Consumes no game RNG.
 */
class WallOfLimbsTests {
    static Game game;
    static Player ai;
    static int passed;

    static Card add(String name, Player p, ZoneType zone) {
        Card card = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), p);
        card.setGameTimestamp(p.getGame().getNextTimestamp());
        p.getZone(zone).add(card);
        return card;
    }

    static Card wall(Player p, int power) {
        Card card = add("Wall of Limbs", p, ZoneType.Battlefield);
        if (power > 0) {
            card.setCounters(CounterEnumType.P1P1, power);
        }
        return card;
    }

    static SpellAbility ability(Card card, Player owner) {
        for (SpellAbility sa : card.getSpellAbilities()) {
            if (sa.getApi() == ApiType.LoseLife) {
                sa.setActivatingPlayer(owner);
                return sa;
            }
        }
        return null;
    }

    static void newGame() {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LobbyPlayerAi l = new LobbyPlayerAi("Ai(" + (i + 1) + ")-test", null);
            l.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(l));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        Match match = new Match(rules, regs, "wall-of-limbs fixtures");
        game = new Game(regs, rules, match);
        game.setAge(GameStage.Play);
        ai = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        for (int i = 0; i < 7; i++) {
            add("Swamp", ai, ZoneType.Battlefield);
        }
    }

    static Player opp(int i) {
        return game.getPlayers().get(i);
    }

    static boolean willing(SpellAbility sa) {
        return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa).willingToPlay();
    }

    static void check(boolean cond, String name) {
        if (!cond) {
            throw new AssertionError(name);
        }
        passed++;
        System.out.println("PASS " + name);
    }

    static void loader() {
        newGame();
        Card card = add("Wall of Limbs", ai, ZoneType.Hand);
        AiOverrideProfile.setActive("");
        check(AiOverrideProfile.get().getAiLogic(card).isEmpty(), "loader: no logic override without a profile");
        AiOverrideProfile.setActive("abzan-armor");
        check("WallOfLimbs".equals(AiOverrideProfile.get().getAiLogic(card)),
                "loader: profile supplies the forced logic");
        check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(card), "loader: profile un-blocks in-game play");
        AiOverrideProfile.setActive("");
    }

    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(8101));
        AiOverrideProfile.setActive("");

        loader();

        // Trigger 1: drain of 6 or more.
        newGame();
        Card wall6 = wall(ai, 6);
        check(wall6.getNetPower() == 6, "setup: 6 counters give the wall power 6");
        AiOverrideProfile.setActive("abzan-armor");
        check(willing(ability(wall6, ai)), "T1: drains a full board for 6");

        newGame();
        Card wall5 = wall(ai, 5);
        AiOverrideProfile.setActive("abzan-armor");
        check(!willing(ability(wall5, ai)), "T1: a drain of 5 is not enough on its own");

        newGame();
        Card wall0 = wall(ai, 0);
        AiOverrideProfile.setActive("abzan-armor");
        check(!willing(ability(wall0, ai)), "T1: a drain of 0 is never activated");

        // Trigger 2: lethal below 6.
        newGame();
        Card wall4 = wall(ai, 4);
        opp(1).setLife(4, null);
        AiOverrideProfile.setActive("abzan-armor");
        check(willing(ability(wall4, ai)), "T2: activates when the drain is lethal (4 < 6)");

        newGame();
        Card wall4b = wall(ai, 4);
        opp(1).setLife(5, null);
        AiOverrideProfile.setActive("abzan-armor");
        check(!willing(ability(wall4b, ai)), "T2: not lethal and below 6 stays off");

        // Target selection: the lethal opponent, otherwise the lowest life.
        newGame();
        Card wall6b = wall(ai, 6);
        opp(1).setLife(20, null);
        opp(2).setLife(3, null);
        opp(3).setLife(12, null);
        SpellAbility sa6 = ability(wall6b, ai);
        AiOverrideProfile.setActive("abzan-armor");
        check(willing(sa6), "target: drain of 6 activates");
        boolean aimedAtLowest = false;
        for (Player t : sa6.getTargets().getTargetPlayers()) {
            aimedAtLowest = aimedAtLowest || t.equals(opp(2));
        }
        check(aimedAtLowest, "target: aims the drain at the lowest-life opponent");

        // Regression: with no profile the default decision is unchanged (and cannot play the card).
        newGame();
        Card wall6c = wall(ai, 6);
        AiOverrideProfile.setActive("");
        check(!willing(ability(wall6c, ai)), "regression: no profile keeps upstream behaviour");
        AiOverrideProfile.setActive("");

        System.out.println("DONE " + passed + " checks");
    }
}
