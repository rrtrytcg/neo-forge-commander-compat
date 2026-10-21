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

/** Native baseline defect and opt-in public-board choice and cast-policy checks. */
public class SelectiveObliterationTests {
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
    static void setup() {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i=0; i<2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Selective Obliteration fixture " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Selective Obliteration fixtures")); game.setAge(GameStage.Play);
        ai = p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        add("Wastes", ai, ZoneType.Battlefield, 5);
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library, 20); }
        source = add("Selective Obliteration", ai, ZoneType.Hand); spell = source.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai, spell).willingToPlay(); }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    static SpellAbility repeatChoice() { return spell.getAdditionalAbility("RepeatSubAbility"); }
    static String choiceFor(Player player) {
        source.addRemembered(player);
        try { return ComputerUtilCard.chooseColor(repeatChoice(), 1, 1, List.of("White", "Blue", "Black", "Red", "Green")).get(0); }
        finally { source.removeRemembered(player); }
    }
    static void nativeBaseline() {
        AiOverrideProfile.setActive(""); setup();
        add("Grizzly Bears", ai, ZoneType.Battlefield, 2);
        add("Goblin Piker", p(1), ZoneType.Battlefield, 4); add("Grizzly Bears", p(1), ZoneType.Battlefield);
        refresh();
        check(willing(), "baseline: RepeatEach accepts Selective Obliteration without weighing resulting losses");
        String baselineColor = choiceFor(p(1));
        check("Green".equalsIgnoreCase(baselineColor), "baseline: color chooser protects the casting AI's green prominence on the opponent's board");
        check(selected() == null || selected().getHostCard() != source, "baseline: RemoveDeck keeps Selective Obliteration blocked during normal selection");
    }
    static void safeUseAndNativeResolution() {
        AiOverrideProfile.setActive("selective-obliteration"); setup();
        add("Grizzly Bears", ai, ZoneType.Battlefield, 2);
        add("Goblin Piker", p(1), ZoneType.Battlefield, 4); add("Grizzly Bears", p(1), ZoneType.Battlefield);
        refresh();
        check("SelectiveObliteration".equals(AiOverrideProfile.get().getAiLogic(source)), "profile opts in to the dedicated remembered-player color rule");
        check("Green".equalsIgnoreCase(choiceFor(ai)) && "Red".equalsIgnoreCase(choiceFor(p(1))), "each seat's choice protects its highest-value mono-color group");
        check(willing(), "casts when the forecast removes more opposing than friendly value");
        check(selected() != null && selected().getHostCard() == source, "native AI selects the profile-enabled spell");
        check(ComputerUtil.playStack(spell, ai, game), "native mana payment puts the original spell on the stack");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(game.getStack().isEmpty(), "native RepeatEach resolution completes");
        check(ai.getCreaturesInPlay().size() == 2 && ai.getCreaturesInPlay().stream().allMatch(c -> c.getName().equals("Grizzly Bears")),
                "our green mono-colored creatures survive our choice");
        check(p(1).getCreaturesInPlay().stream().filter(c -> c.getName().equals("Goblin Piker")).count() == 4,
                "opponent's red majority survives its own choice");
        check(p(1).getCreaturesInPlay().stream().noneMatch(c -> c.getName().equals("Grizzly Bears")),
                "native exile resolution removes the opponent's off-color creature");
    }
    static void holdsAndIsolation() {
        AiOverrideProfile.setActive("selective-obliteration"); setup();
        add("Selesnya Guildmage", ai, ZoneType.Battlefield); add("Goblin Piker", p(1), ZoneType.Battlefield, 3); refresh();
        check(!willing(), "holds when our multicolored permanent loss outweighs the opponent's forecast loss");

        setup(); add("Grizzly Bears", ai, ZoneType.Battlefield, 2);
        add("Goblin Piker", p(1), ZoneType.Battlefield, 4); add("Grizzly Bears", p(1), ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, ai); refresh();
        check(!willing(), "native sorcery timing prevents casting outside a main phase");

        setup(); add("Grizzly Bears", ai, ZoneType.Battlefield, 2);
        add("Goblin Piker", p(1), ZoneType.Battlefield, 4); add("Grizzly Bears", p(1), ZoneType.Battlefield);
        ai.getCardsIn(ZoneType.Battlefield).removeIf(c -> c.getName().equals("Wastes")); refresh();
        check(!willing() && (selected() == null || selected().getHostCard() != source), "native mana affordability still gates the spell");

        for (String profile : List.of("", "choice-wipes", "ghoulcaller-gisa")) {
            AiOverrideProfile.setActive(profile); setup();
            add("Grizzly Bears", ai, ZoneType.Battlefield, 2);
            add("Goblin Piker", p(1), ZoneType.Battlefield, 4); add("Grizzly Bears", p(1), ZoneType.Battlefield); refresh();
            check(!"SelectiveObliteration".equals(AiOverrideProfile.get().getAiLogic(source)) && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(source)
                    && (selected() == null || selected().getHostCard() != source), "profile isolation keeps Selective Obliteration blocked under '" + profile + "'");
        }
        AiOverrideProfile.setActive("selective-obliteration"); setup();
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(add("Dispatch", ai, ZoneType.Hand)), "profile does not admit unrelated cards");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
        if (args.length > 0 && "baseline".equals(args[0])) { nativeBaseline(); }
        else { safeUseAndNativeResolution(); holdsAndIsolation(); }
        AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Selective Obliteration checks");
    }
}
