import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.ai.ability.RepeatEachAi;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Confirms both table-wide choice wipes share RepeatEach's unconditional baseline decision. */
public class ChoiceWipesBaselineTests {
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
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static void setup(String name, boolean selective) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i=0; i<2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Choice wipes baseline " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Choice wipes baseline")); game.setAge(GameStage.Play);
        ai = p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        for (int i=0; i<(selective ? 5 : 3); i++) { add(selective ? "Wastes" : "Plains", ai, ZoneType.Battlefield); }
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library); }
        source = add(name, ai, ZoneType.Hand); spell = source.getFirstSpellAbility(); spell.setActivatingPlayer(ai); refresh();
    }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    static boolean nativeRootWilling() throws Exception {
        var method = RepeatEachAi.class.getDeclaredMethod("canPlay", Player.class, SpellAbility.class);
        method.setAccessible(true);
        return ((AiAbilityDecision) method.invoke(SpellApiToAi.Converter.get(spell), ai, spell)).willingToPlay();
    }
    static void run() throws Exception {
        AiOverrideProfile.setActive("");
        setup("Harsh Mercy", false); add("Grizzly Bears", ai, ZoneType.Battlefield); add("Grizzly Bears", p(1), ZoneType.Battlefield); refresh();
        check(nativeRootWilling(), "baseline: RepeatEach accepts Harsh Mercy when no opposing creature would be removed");
        check(selected() == null || selected().getHostCard() != source, "baseline: RemoveDeck blocks Harsh Mercy selection");

        setup("Selective Obliteration", true); add("Selesnya Guildmage", ai, ZoneType.Battlefield); refresh();
        check(nativeRootWilling(), "baseline: RepeatEach accepts Selective Obliteration despite our multicolor permanent");
        check(selected() == null || selected().getHostCard() != source, "baseline: RemoveDeck blocks Selective Obliteration selection");
    }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
        run(); AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Choice Wipes baseline checks");
    }
}
