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
import java.util.*;

/** Native Ghoulcaller Gisa timing, cost payment, fodder and token-resolution fixtures. */
public class GhoulcallerGisaTests {
    static Game game;
    static Player ai;
    static Card gisa;
    static SpellAbility ability;
    static int passed;

    static void check(boolean ok, String label) {
        if (!ok) { throw new AssertionError(label); }
        passed++;
        System.out.println("PASS " + label);
    }
    static Player p(int i) { return game.getPlayers().get(i); }
    static Card add(String name, Player owner, ZoneType zone) {
        Card c = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), owner);
        c.setGameTimestamp(game.getNextTimestamp());
        owner.getZone(zone).add(c);
        return c;
    }
    static void add(String name, Player owner, ZoneType zone, int count) {
        for (int i = 0; i < count; i++) { add(name, owner, zone); }
    }
    static void refresh() {
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
    }
    static void setup(boolean fodder, int swamps) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Ghoulcaller Gisa fixture " + i, null);
            lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Ghoulcaller Gisa native fixtures"));
        game.setAge(GameStage.Play);
        ai = p(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        add("Swamp", ai, ZoneType.Battlefield, swamps);
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library, 12); }
        gisa = add("Ghoulcaller Gisa", ai, ZoneType.Battlefield);
        gisa.setSickness(false);
        ability = gisa.getSpellAbilities().stream().filter(s -> s.getApi() == ApiType.Token).findFirst().orElseThrow();
        ability.setActivatingPlayer(ai);
        if (fodder) { add("Grizzly Bears", ai, ZoneType.Battlefield); }
        refresh();
    }
    static void setupCast() {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Ghoulcaller Gisa cast fixture " + i, null);
            lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Ghoulcaller Gisa native casting"));
        game.setAge(GameStage.Play);
        ai = p(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        add("Swamp", ai, ZoneType.Battlefield, 5);
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library, 12); }
        gisa = add("Ghoulcaller Gisa", ai, ZoneType.Hand);
        refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(ability).canPlayWithSubs(ai, ability).willingToPlay(); }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    static long zombies() {
        return ai.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.isToken() && c.getType().hasSubtype("Zombie")).count();
    }
    static void baseline() {
        AiOverrideProfile.setActive(""); setup(true, 1);
        SpellAbility choice = selected();
        check(choice == null || choice.getHostCard() != gisa, "baseline: RemoveDeck blocks normal Ghoulcaller Gisa activation selection");
    }
    static void deterministicActivation() {
        AiOverrideProfile.setActive("ghoulcaller-gisa"); setup(true, 1);
        check(GhoulcallerGisaAi.uses(ability), "profile recognizes Gisa's native Zombie ability");
        check(Boolean.TRUE.equals(GhoulcallerGisaAi.checkPhase(ai, ability)), "Gisa policy timing is our empty-stack second main");
        AiAbilityDecision decision = SpellApiToAi.Converter.get(ability).canPlayWithSubs(ai, ability);
        check(decision.willingToPlay(ability), "profile policy accepts a legal second-main activation without a random chance roll: " + decision.decision());
        SpellAbility choice = selected();
        check(choice != null && choice.getHostCard() == gisa && choice.getApi() == ApiType.Token,
                "normal AI selection chooses Ghoulcaller Gisa's Zombie ability");
        check(ComputerUtil.playStack(ability, ai, game), "native activation pays black, taps Gisa and sacrifices fodder");
        Card bears = game.getCardsIn(ZoneType.Graveyard).stream().filter(c -> c.getName().equals("Grizzly Bears")).findFirst().orElse(null);
        check(bears != null, "the selected Grizzly Bears is sacrificed as the native cost");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(zombies() == 2, "native resolution creates Zombie tokens equal to sacrificed creature power");
        check(gisa.isInPlay() && gisa.isTapped(), "native activation leaves Gisa in play and tapped");
    }
    static void holds() {
        AiOverrideProfile.setActive("ghoulcaller-gisa"); setup(false, 1);
        check(!willing(), "holds when no other creature can be sacrificed");
        setup(true, 0); check(!willing(), "holds when native payment cannot pay black mana");
        setup(true, 1); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        check(!willing(), "holds outside our second main phase");
        setup(true, 1); game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p(1));
        check(!willing(), "holds during an opponent's turn");
        setup(true, 1); game.getStack().add(ability.copy(ai));
        check(!willing(), "holds while another ability is on the stack");
        setup(true, 1); gisa.setTapped(true); refresh();
        check(!willing(), "native tap-cost legality prevents reuse while Gisa is tapped");
    }
    static void nativeCast() {
        AiOverrideProfile.setActive("ghoulcaller-gisa"); setupCast();
        SpellAbility spell = selected();
        SpellAbility gisaSpell = gisa.getFirstSpellAbility(); gisaSpell.setActivatingPlayer(ai);
        check(spell != null && spell.getHostCard() == gisa && spell.isSpell(), "native creature AI selects Gisa to cast");
        check(ComputerUtil.playStack(spell, ai, game), "native payment puts Gisa on the stack");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        gisa = game.getCardState(gisa);
        check(gisa.isInPlay(), "native creature spell resolves Gisa onto the battlefield");
    }
    static void profileIsolation() {
        for (String profile : List.of("", "carpet-of-flowers")) {
            AiOverrideProfile.setActive(profile); setup(true, 1);
            SpellAbility choice = selected();
            check(choice == null || choice.getHostCard() != gisa,
                    "profile '" + profile + "' leaves Ghoulcaller Gisa blocked at normal selection");
        }
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        forge.util.MyRandom.setRandom(new java.util.Random(20261020));
        if (args.length > 0 && "baseline".equals(args[0])) { baseline(); }
        else { deterministicActivation(); holds(); nativeCast(); profileIsolation(); }
        System.out.println("GhoulcallerGisaTests passed " + passed + " checks");
    }
}
