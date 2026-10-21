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

/** Reproduces the blocked-card defect while checking native tapped-creature removal. */
public class GideonJuraBaselineTests {
    static Game game;
    static Player ai;
    static Card gideon;
    static SpellAbility minusTwo;
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
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static void setup() {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i=0; i<2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Gideon baseline " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Gideon Jura baseline")); game.setAge(GameStage.Play);
        ai = p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        for (int i=0; i<5; i++) { add("Plains", ai, ZoneType.Battlefield); }
        gideon = add("Gideon Jura", ai, ZoneType.Battlefield); gideon.setCounters(CounterEnumType.LOYALTY, 6);
        add("Grizzly Bears", p(1), ZoneType.Battlefield).setTapped(true);
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library); }
        minusTwo = gideon.getSpellAbilities().stream().filter(sa -> sa.getApi() == ApiType.Destroy).findFirst().orElseThrow();
        minusTwo.setActivatingPlayer(ai); refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(minusTwo).canPlayWithSubs(ai, minusTwo).willingToPlay(); }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
        AiOverrideProfile.setActive(""); setup();
        check(willing(), "baseline: native Destroy AI accepts Gideon's legal -2 against a tapped opponent creature");
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        check(chosen == null || chosen.stream().noneMatch(sa -> sa.getHostCard() == gideon),
                "baseline: RemoveDeck prevents Gideon from normal AI selection");
        AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Gideon Jura baseline checks");
    }
}
