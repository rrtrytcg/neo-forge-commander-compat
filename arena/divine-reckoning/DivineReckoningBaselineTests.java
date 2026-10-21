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

/** Confirms Divine Reckoning's native admission and RemoveDeck baseline. */
public class DivineReckoningBaselineTests {
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
            LobbyPlayerAi lobby = new LobbyPlayerAi("Divine Reckoning baseline " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Divine Reckoning baseline")); game.setAge(GameStage.Play);
        ai = p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        for (int i=0; i<4; i++) { add("Plains", ai, ZoneType.Battlefield); }
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library); }
        source = add("Divine Reckoning", ai, ZoneType.Hand); spell = source.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        add("Grizzly Bears", ai, ZoneType.Battlefield, 2);
        add("Grizzly Bears", p(1), ZoneType.Battlefield, 2);
        refresh();
    }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261021));
        AiOverrideProfile.setActive(""); setup();
        AiAbilityDecision nativeDecision = SpellApiToAi.Converter.get(spell).chkDrawback(ai, spell);
        check(nativeDecision.willingToPlay(),
                "baseline: native ChooseCardAi API decision accepts Divine Reckoning without evaluating symmetric losses");
        check(selected() == null || selected().getHostCard() != source,
                "baseline: AI:RemoveDeck blocks Divine Reckoning from normal spell selection");
        System.out.println("DONE " + passed + " Divine Reckoning baseline checks");
    }
}
