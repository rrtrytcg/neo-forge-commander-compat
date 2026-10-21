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

/** Native fixtures for the opt-in Divine Reckoning cast rule. */
public class DivineReckoningTests {
    static Game game;
    static Player ai;
    static Card source;
    static SpellAbility spell;
    static int passed;

    static void check(boolean ok, String label) {
        if (!ok) { throw new AssertionError(label); }
        passed++; System.out.println("PASS " + label);
    }
    static Player p(int i) { return game.getPlayers().get(i); }
    static Card add(String name, Player owner, ZoneType zone) {
        Card c = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); c.setSickness(false); return c;
    }
    static void add(String name, Player owner, ZoneType zone, int count) { for (int i=0; i<count; i++) { add(name, owner, zone); } }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static void setup() {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i=0; i<2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Divine Reckoning fixture " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Divine Reckoning native fixtures")); game.setAge(GameStage.Play);
        ai = p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        add("Plains", ai, ZoneType.Battlefield, 4);
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library, 20); }
        source = add("Divine Reckoning", ai, ZoneType.Hand); spell = source.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai, spell).willingToPlay(); }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    static void safeResolution() {
        AiOverrideProfile.setActive("divine-reckoning"); setup();
        add("Hill Giant", ai, ZoneType.Battlefield); add("Grizzly Bears", ai, ZoneType.Battlefield);
        add("Craw Wurm", p(1), ZoneType.Battlefield); add("Grizzly Bears", p(1), ZoneType.Battlefield, 3); refresh();
        AiAbilityDecision root = SpellApiToAi.Converter.get(spell).chkDrawback(ai, spell);
        check(DivineReckoningAi.consider(ai, spell) != null && root.willingToPlay(),
                "profile admits a favorable native creature-choice forecast");
        check(selected() != null && selected().getHostCard() == source,
                "normal AI selection offers the favorable profile-enabled spell");
        check(ComputerUtil.playStack(spell, ai, game), "native four-mana payment puts Divine Reckoning on the stack");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(game.getStack().isEmpty(), "native choice-and-destroy resolution completes");
        check(ai.getCreaturesInPlay().size() == 1 && ai.getCreaturesInPlay().get(0).getName().equals("Hill Giant"),
                "native chooser keeps our best creature and destroys our weaker creature");
        check(p(1).getCreaturesInPlay().size() == 1 && p(1).getCreaturesInPlay().get(0).getName().equals("Craw Wurm"),
                "native chooser keeps the opponent's best creature and destroys the rest");
    }
    static void holds() {
        AiOverrideProfile.setActive("divine-reckoning");
        setup(); add("Grizzly Bears", ai, ZoneType.Battlefield, 2); add("Grizzly Bears", p(1), ZoneType.Battlefield, 2); refresh();
        check(!willing(), "holds when opposing and friendly predicted losses are equal");

        setup(); add("Hill Giant", ai, ZoneType.Battlefield); add("Grizzly Bears", ai, ZoneType.Battlefield, 3);
        add("Hill Giant", p(1), ZoneType.Battlefield); add("Grizzly Bears", p(1), ZoneType.Battlefield, 2); refresh();
        check(!willing(), "holds when predicted friendly creature losses outweigh opposing losses");

        setup(); add("Hill Giant", ai, ZoneType.Battlefield); add("Grizzly Bears", ai, ZoneType.Battlefield);
        add("Craw Wurm", p(1), ZoneType.Battlefield); add("Grizzly Bears", p(1), ZoneType.Battlefield, 3); refresh();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        check(!willing(), "preserves native second-main preference");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p(1));
        check(!willing(), "holds outside our turn");
    }
    static void affordabilityAndIsolation() {
        AiOverrideProfile.setActive("divine-reckoning"); setup();
        add("Hill Giant", ai, ZoneType.Battlefield); add("Grizzly Bears", ai, ZoneType.Battlefield);
        add("Craw Wurm", p(1), ZoneType.Battlefield); add("Grizzly Bears", p(1), ZoneType.Battlefield, 3);
        for (Card land : new ArrayList<>(ai.getLandsInPlay())) { ai.getZone(ZoneType.Battlefield).remove(land); }
        add("Plains", ai, ZoneType.Battlefield, 3); refresh();
        check(ai.getLandsInPlay().size() == 3 && !ComputerUtilCost.canPayCost(spell, ai, false)
                        && (selected() == null || selected().getHostCard() != source),
                "native printed mana cost still gates the spell");

        for (String profile : List.of("", "ghoulcaller-gisa", "moonlight-bargain")) {
            AiOverrideProfile.setActive(profile); setup();
            add("Hill Giant", ai, ZoneType.Battlefield); add("Grizzly Bears", ai, ZoneType.Battlefield);
            add("Craw Wurm", p(1), ZoneType.Battlefield); add("Grizzly Bears", p(1), ZoneType.Battlefield, 3); refresh();
            check(DivineReckoningAi.consider(ai, spell) == null
                    && (selected() == null || selected().getHostCard() != source),
                    "profile isolation keeps Divine Reckoning blocked under '" + profile + "'");
        }
        AiOverrideProfile.setActive("divine-reckoning");
        setup();
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(add("Dispatch", ai, ZoneType.Hand)),
                "profile does not admit unrelated cards");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261021));
        safeResolution(); holds(); affordabilityAndIsolation();
        AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Divine Reckoning checks");
    }
}
