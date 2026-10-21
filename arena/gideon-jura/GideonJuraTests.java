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

/** Native selection, loyalty payment, target legality and resolution for Gideon's Destroy-only profile. */
public class GideonJuraTests {
    static Game game;
    static Player ai;
    static Player opponent;
    static Card gideon;
    static SpellAbility minusTwo, plusTwo, zero;
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
    static SpellAbility ability(ApiType api) {
        return gideon.getSpellAbilities().stream().filter(sa -> sa.getApi() == api).findFirst().orElseThrow();
    }
    static void setup(String... targets) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i=0; i<2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Gideon fixture " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Gideon Jura native fixtures")); game.setAge(GameStage.Play);
        ai = p(0); opponent = p(1); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        for (int i=0; i<5; i++) { add("Plains", ai, ZoneType.Battlefield); }
        gideon = add("Gideon Jura", ai, ZoneType.Battlefield); gideon.setCounters(CounterEnumType.LOYALTY, 6);
        for (String target : targets) { add(target, opponent, ZoneType.Battlefield).setTapped(true); }
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library); }
        minusTwo = ability(ApiType.Destroy); plusTwo = ability(ApiType.DelayedTrigger); zero = ability(ApiType.Animate);
        minusTwo.setActivatingPlayer(ai); plusTwo.setActivatingPlayer(ai); zero.setActivatingPlayer(ai); refresh();
    }
    static boolean willing(SpellAbility sa) { return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa).willingToPlay(); }
    static List<SpellAbility> selected() { return ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay(); }
    static boolean chose(SpellAbility ability) { List<SpellAbility> list = selected(); return list != null && list.contains(ability); }
    static void activateAndResolve() {
        Card target = minusTwo.getTargetCard();
        check(target != null && target.getController() == opponent && target.isTapped(), "native target choice is a legal tapped opposing creature");
        int loyalty = gideon.getCounters(CounterEnumType.LOYALTY);
        check(ComputerUtil.playStack(minusTwo, ai, game), "native -2 activation pays loyalty and enters stack");
        check(gideon.getCounters(CounterEnumType.LOYALTY) == loyalty - 2, "native activation pays exactly two loyalty");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(!target.isInPlay(), "native resolution destroys the selected tapped creature");
        check(gideon.isInPlay(), "Gideon remains on the battlefield after the -2 resolves");
    }
    public static void main(String[] args) {
        try {
            GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
            AiOverrideProfile.setActive("gideon-jura");

            setup("Grizzly Bears", "Craw Wurm");
            check(GideonJuraAi.uses(minusTwo) && willing(minusTwo), "profile admits Gideon -2 when a tapped opposing creature is available");
            check(!willing(plusTwo) && !willing(zero), "profile holds unsupported +2 and 0 modes");
            check(chose(minusTwo), "normal AI chooser selects Gideon's -2 from the battlefield");
            activateAndResolve();

            setup();
            check(!willing(minusTwo) && !chose(minusTwo), "holds -2 when no opposing creature is tapped");
            setup("Grizzly Bears"); gideon.setCounters(CounterEnumType.LOYALTY, 1); refresh();
            check(!willing(minusTwo), "holds -2 when fewer than two loyalty counters remain");
            setup("Grizzly Bears"); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent); refresh();
            check(!willing(minusTwo), "holds -2 outside our turn");
            setup("Grizzly Bears"); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN, ai); refresh();
            check(!willing(minusTwo), "holds -2 outside a sorcery-speed main phase");
            setup("Grizzly Bears");
            Card stackTarget = add("Grizzly Bears", ai, ZoneType.Battlefield);
            SpellAbility stack = add("Murder", opponent, ZoneType.Hand).getFirstSpellAbility(); stack.setActivatingPlayer(opponent); stack.getTargets().add(stackTarget); game.getStack().add(stack);
            check(!willing(minusTwo), "holds -2 with a non-empty stack");

            AiOverrideProfile.setActive(""); setup("Grizzly Bears");
            check(!GideonJuraAi.uses(minusTwo) && !chose(minusTwo), "default profile remains blocked by RemoveDeck");
            AiOverrideProfile.setActive("choice-wipes"); setup("Grizzly Bears");
            check(!GideonJuraAi.uses(minusTwo) && !chose(minusTwo), "unrelated profile leaves Gideon unchanged");
            AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Gideon Jura checks");
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
}
