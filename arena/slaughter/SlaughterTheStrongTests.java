import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/**
 * Bounded fixture for the Slaughter the Strong AI rules, opted in by the NeoForge override profile
 * (aiLogic=Slaughter the Strong=SlaughterTheStrong).
 *
 *  - Rule 1 (cast gate): cast only when at least two opponents' board strength (sum of power +
 *    toughness over their creatures) leads ours by at least 8.
 *  - Rule 2 (keep set): among our creatures keep the subset with total power <= 4 that maximizes
 *    total power, then total toughness, then battlefield order.
 *
 * Builds real Forge games, calls the decision entry points directly, and asserts the upstream
 * default (no profile) is unchanged. Consumes no game RNG.
 */
class SlaughterTheStrongTests {
    static Game game;
    static Player ai;
    static int passed;

    static Card add(String name, Player p, ZoneType zone) {
        Card card = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), p);
        card.setGameTimestamp(p.getGame().getNextTimestamp());
        p.getZone(zone).add(card);
        return card;
    }

    static Card creature(String name, Player p) {
        return add(name, p, ZoneType.Battlefield);
    }

    static void newGame() {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LobbyPlayerAi l = new LobbyPlayerAi("Ai(" + (i + 1) + ")-test", null);
            l.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(l));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        Match match = new Match(rules, regs, "slaughter-the-strong fixtures");
        game = new Game(regs, rules, match);
        game.setAge(GameStage.Play);
        ai = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
    }

    static SpellAbility slaughter(Player owner) {
        Card card = add("Slaughter the Strong", owner, ZoneType.Hand);
        SpellAbility sa = card.getSpellAbilities().get(0);
        sa.setActivatingPlayer(owner);
        if (sa.getSubAbility() != null) {
            sa.getSubAbility().setActivatingPlayer(owner);
        }
        return sa;
    }

    static Player opp(int i) {
        return game.getPlayers().get(i);
    }

    static void check(boolean cond, String name) {
        if (!cond) {
            throw new AssertionError(name);
        }
        passed++;
        System.out.println("PASS " + name);
    }

    static int totalPower(CardCollection c) {
        int s = 0;
        for (Card k : c) {
            s += k.getNetPower();
        }
        return s;
    }

    static int totalToughness(CardCollection c) {
        int s = 0;
        for (Card k : c) {
            s += k.getNetToughness();
        }
        return s;
    }

    // ---- Rule 1: when to cast ----
    static void rule1() {
        newGame();
        creature("Grizzly Bears", opp(1));
        creature("Grizzly Bears", opp(1)); // 8
        creature("Grizzly Bears", opp(2));
        creature("Grizzly Bears", opp(2)); // 8
        check(SpecialCardAi.SlaughterTheStrong.wantsCast(ai), "R1: cast when two opponents lead by 8");

        newGame();
        creature("Grizzly Bears", opp(1));
        creature("Grizzly Bears", opp(1)); // 8
        creature("Grizzly Bears", opp(2)); // 4 only
        check(!SpecialCardAi.SlaughterTheStrong.wantsCast(ai), "R1: no cast when only one opponent leads");

        newGame();
        creature("Grizzly Bears", opp(1));
        creature("Grizzly Bears", opp(1)); // 8
        creature("Grizzly Bears", opp(2));
        creature("Savannah Lions", opp(2)); // 4 + 3 = 7
        check(!SpecialCardAi.SlaughterTheStrong.wantsCast(ai), "R1: a lead of 7 does not count");

        newGame();
        creature("Grizzly Bears", ai);
        creature("Grizzly Bears", ai); // ours 8
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 4; j++) {
                creature("Grizzly Bears", opp(1 + i)); // 16, lead 8
            }
        }
        check(SpecialCardAi.SlaughterTheStrong.wantsCast(ai), "R1: lead measured against our own board");
    }

    // ---- Rule 2: what to keep ----
    static void rule2() {
        newGame();
        Card courser = creature("Centaur Courser", ai); // 3/3
        Card b1 = creature("Grizzly Bears", ai); // 2/2
        Card b2 = creature("Grizzly Bears", ai); // 2/2
        SpellAbility sa = slaughter(ai);
        CardCollection keep = SpecialCardAi.SlaughterTheStrong.keepSet(ai, sa);
        check(keep.size() == 2 && keep.contains(b1) && keep.contains(b2) && !keep.contains(courser),
                "R2: prefers two 2s (power 4) over a single 3");
        check(totalPower(keep) == 4, "R2: keep set uses the full power budget");
        check(SpecialCardAi.SlaughterTheStrong.totalPowerBudget(sa) == 4, "R2: budget read from WithTotalPower");

        newGame();
        Card nighthawk = creature("Vampire Nighthawk", ai); // 2/3
        creature("Grizzly Bears", ai);
        creature("Grizzly Bears", ai);
        CardCollection keep2 = SpecialCardAi.SlaughterTheStrong.keepSet(ai, slaughter(ai));
        check(keep2.size() == 2 && keep2.contains(nighthawk) && totalPower(keep2) == 4 && totalToughness(keep2) == 5,
                "R2: at equal power, keeps the higher-toughness pair");

        newGame();
        Card g1 = creature("Grizzly Bears", ai); // 2/2
        Card g2 = creature("Grizzly Bears", ai); // 2/2
        Card big = creature("Loxodon Hierarch", ai); // 4/4
        CardCollection keep3 = SpecialCardAi.SlaughterTheStrong.keepSet(ai, slaughter(ai));
        check(keep3.size() == 2 && keep3.contains(g1) && keep3.contains(g2) && !keep3.contains(big),
                "R2: battlefield order breaks an exact power/toughness tie");

        newGame();
        creature("Felothar the Steadfast", ai); // 0/5
        creature("Grizzly Bears", ai);
        creature("Grizzly Bears", ai);
        CardCollection keep4 = SpecialCardAi.SlaughterTheStrong.keepSet(ai, slaughter(ai));
        check(keep4.size() == 3 && totalPower(keep4) == 4, "R2: keeps zero-power creatures for free");

        newGame();
        Card dragon = creature("Shivan Dragon", ai); // 5/5
        Card bear = creature("Grizzly Bears", ai);
        CardCollection keep5 = SpecialCardAi.SlaughterTheStrong.keepSet(ai, slaughter(ai));
        check(keep5.size() == 1 && keep5.contains(bear) && !keep5.contains(dragon),
                "R2: a 5-power creature cannot be kept");
    }

    // ---- profile loader ----
    static void loader() {
        newGame();
        Card card = add("Slaughter the Strong", ai, ZoneType.Hand);
        AiOverrideProfile.setActive("");
        check(AiOverrideProfile.get().getAiLogic(card).isEmpty(), "loader: no logic override without a profile");
        AiOverrideProfile.setActive("abzan-armor");
        check("SlaughterTheStrong".equals(AiOverrideProfile.get().getAiLogic(card)),
                "loader: profile supplies the forced logic");
        check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(card), "loader: profile un-blocks in-game play");
        AiOverrideProfile.setActive("");
    }

    // ---- wiring through ChooseCardAi / canPlay ----
    static void wiring() {
        newGame();
        creature("Centaur Courser", ai); // 3/3
        creature("Grizzly Bears", ai);
        creature("Grizzly Bears", ai);
        SpellAbility sa = slaughter(ai);
        CardCollection options = new CardCollection(ai.getCreaturesInPlay());

        AiOverrideProfile.setActive("abzan-armor");
        Card chosen = SpellApiToAi.Converter.get(sa).chooseSingleEntity(ai, sa, options, false, null, null);
        check(chosen != null && "Grizzly Bears".equals(chosen.getName()),
                "wiring: profile routes the keep choice to Rule 2");

        AiOverrideProfile.setActive("");
        Card classic = SpellApiToAi.Converter.get(sa).chooseSingleEntity(ai, sa, options, false, null, null);
        check(classic != null && "Centaur Courser".equals(classic.getName()),
                "wiring: no profile keeps the upstream choice");

        newGame();
        for (int i = 0; i < 3; i++) {
            add("Plains", ai, ZoneType.Battlefield);
        }
        creature("Grizzly Bears", opp(1));
        creature("Grizzly Bears", opp(1)); // 8
        creature("Grizzly Bears", opp(2));
        creature("Grizzly Bears", opp(2)); // 8
        SpellAbility asym = slaughter(ai);
        AiOverrideProfile.setActive("abzan-armor");
        check(SpellApiToAi.Converter.get(asym).canPlayWithSubs(ai, asym).willingToPlay(),
                "wiring: profile lets the AI cast against two stronger opponents");

        newGame();
        for (int i = 0; i < 3; i++) {
            add("Plains", ai, ZoneType.Battlefield);
        }
        SpellAbility even = slaughter(ai);
        check(!SpellApiToAi.Converter.get(even).canPlayWithSubs(ai, even).willingToPlay(),
                "wiring: profile blocks the cast on an even board");
        AiOverrideProfile.setActive("");
    }

    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(7301));
        AiOverrideProfile.setActive("");

        rule1();
        rule2();
        loader();
        wiring();

        System.out.println("DONE " + passed + " checks");
    }
}
