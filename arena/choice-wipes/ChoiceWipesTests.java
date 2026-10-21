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

/** Native baseline and opt-in safety checks for table-wide choice sweepers. */
public class ChoiceWipesTests {
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
    static void refresh() {
        game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
    }
    static void setup(String cardName, boolean selective) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i=0; i<2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Choice wipes fixture " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Choice wipes native fixtures")); game.setAge(GameStage.Play);
        ai = p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        add(selective ? "Wastes" : "Plains", ai, ZoneType.Battlefield, selective ? 5 : 3);
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library, 20); }
        source = add(cardName, ai, ZoneType.Hand); spell = source.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai, spell).willingToPlay(); }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    static void castAndResolve() {
        check(selected() != null && selected().getHostCard() == source, "native AI selects the safe profile-enabled spell");
        check(ComputerUtil.playStack(spell, ai, game), "native mana payment puts the original spell on the stack");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(game.getStack().isEmpty(), "native RepeatEach resolution completes");
    }
    static void harshMercy() {
        AiOverrideProfile.setActive("choice-wipes");
        setup("Harsh Mercy", false);
        add("Grizzly Bears", ai, ZoneType.Battlefield, 2);
        add("Serra Angel", p(1), ZoneType.Battlefield, 3);
        add("Goblin Chieftain", p(1), ZoneType.Battlefield);
        refresh();
        check(ChoiceWipesAi.uses(spell) && willing(), "Harsh Mercy accepts a safe prominent-type forecast");
        castAndResolve();
        check(ai.getCreaturesInPlay().size() == 2, "native type choices preserve our Bears");
        check(p(1).getCreaturesInPlay().stream().filter(c -> c.getName().equals("Serra Angel")).count() == 3,
                "native type choices preserve the opponent's prominent Angels");
        check(p(1).getCreaturesInPlay().stream().noneMatch(c -> c.getName().equals("Goblin Chieftain")),
                "native destroy resolution removes the off-type Goblin");

        setup("Harsh Mercy", false);
        add("Grizzly Bears", ai, ZoneType.Battlefield, 3); add("Serra Angel", ai, ZoneType.Battlefield);
        add("Grizzly Bears", p(1), ZoneType.Battlefield, 3); add("Serra Angel", p(1), ZoneType.Battlefield); refresh();
        check(!willing(), "holds when native choices would destroy friendly creatures");
        Card shielded = ai.getCreaturesInPlay().stream().filter(c -> c.getName().equals("Serra Angel")).findFirst().orElseThrow();
        shielded.incShieldCount(); refresh();
        check(!willing(), "Harsh Mercy's no-regeneration clause still counts a regeneration shield as a friendly loss");
        setup("Harsh Mercy", false); add("Grizzly Bears", ai, ZoneType.Battlefield, 2); refresh();
        check(!willing(), "holds when a seat has no public creature type for its native choice");
    }
    static void paymentAndIsolation() {
        AiOverrideProfile.setActive("choice-wipes");
        setup("Harsh Mercy", false); add("Grizzly Bears", ai, ZoneType.Battlefield, 2);
        add("Serra Angel", p(1), ZoneType.Battlefield, 3); add("Goblin Chieftain", p(1), ZoneType.Battlefield); refresh();
        ai.getCardsIn(ZoneType.Battlefield).removeIf(Card::isLand); add("Plains", ai, ZoneType.Battlefield, 2); refresh();
        check(!willing() && (selected() == null || selected().getHostCard() != source), "native mana affordability still gates Harsh Mercy");

        for (String profile : List.of("", "ghoulcaller-gisa", "moonlight-bargain")) {
            AiOverrideProfile.setActive(profile); setup("Harsh Mercy", false);
            add("Grizzly Bears", ai, ZoneType.Battlefield, 2);
            add("Serra Angel", p(1), ZoneType.Battlefield, 3); add("Goblin Chieftain", p(1), ZoneType.Battlefield); refresh();
            check(!ChoiceWipesAi.uses(spell) && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(source)
                    && (selected() == null || selected().getHostCard() != source), "profile isolation keeps Harsh Mercy blocked under '" + profile + "'");
        }
        AiOverrideProfile.setActive("choice-wipes");
        setup("Harsh Mercy", false); add("Grizzly Bears", ai, ZoneType.Battlefield, 2);
        add("Serra Angel", p(1), ZoneType.Battlefield, 3); add("Goblin Chieftain", p(1), ZoneType.Battlefield); refresh();
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(add("Dispatch", ai, ZoneType.Hand)), "profile does not admit unrelated cards");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
        AiOverrideProfile.setActive("choice-wipes");
        harshMercy(); paymentAndIsolation();
        AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Choice Wipes checks");
    }
}
