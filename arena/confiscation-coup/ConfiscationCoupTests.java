import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.ai.ability.ControlGainAi;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.AbilityUtils;
import forge.game.card.*;
import forge.game.cost.Cost;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import forge.util.collect.FCollection;
import java.util.*;

/** Native baseline and opt-in safety fixtures for Confiscation Coup. */
public class ConfiscationCoupTests {
    static Game game;
    static Player ai;
    static Player opponent;
    static Card source;
    static Card target;
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
    static void setup(String... targetNames) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i=0; i<2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Confiscation Coup fixture " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Confiscation Coup native fixtures")); game.setAge(GameStage.Play);
        ai = p(0); opponent = p(1); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        add("Island", ai, ZoneType.Battlefield, 3); add("Mountain", ai, ZoneType.Battlefield, 2);
        target = null;
        for (String targetName : targetNames) {
            Card next = add(targetName, opponent, ZoneType.Battlefield);
            if (target == null) { target = next; }
        }
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library); }
        source = add("Confiscation Coup", ai, ZoneType.Hand); spell = source.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        refresh();
    }
    static List<SpellAbility> selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen;
    }
    static boolean choseCoup() {
        List<SpellAbility> chosen = selected();
        return chosen != null && chosen.stream().anyMatch(sa -> sa.getHostCard() == source);
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai, spell).willingToPlay(); }
    static boolean nativeWillPay(SpellAbility ability, Cost cost) {
        return new ControlGainAi().willPayUnlessCost(ai, ability, cost, false, new FCollection<>(ai));
    }
    static void baseline() {
        AiOverrideProfile.setActive("");
        setup("Serra Angel"); ai.setCounters(CounterEnumType.ENERGY, 4); refresh();
        SpellAbility gain = spell.getSubAbility();
        check(gain != null && gain.getApi().toString().equals("GainControl"), "baseline: Coup has a native gain-control subability");
        gain.getTargets().add(target);
        Cost cost = AbilityUtils.calculateUnlessCost(gain, gain.getParam("UnlessCost"), false);
        check(cost.getCostEnergy() != null && cost.getCostEnergy().getAbilityAmount(gain) == target.getCMC(), "baseline: native optional payment is the target's mana-value energy");
        check(!nativeWillPay(gain, cost), "baseline: ControlGainAi refuses Coup's optional payment because its sorcery host is not on the battlefield");
        check(!choseCoup(), "baseline: RemoveDeck blocks Confiscation Coup from normal selection");
    }

    public static void main(String[] args) {
        try {
            GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
            if (args.length > 0 && args[0].equalsIgnoreCase("baseline")) { baseline(); }
            else { run(); }
            AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Confiscation Coup checks");
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    static void castAndResolve() {
        check(choseCoup(), "native chooser selects Coup with the opt-in profile");
        check(spell.getSubAbility().getTargetCard() != null && spell.getSubAbility().getTargetCard().getCMC() <= 4,
                "native gain-control ranking chooses an affordable opposing permanent");
        check(ComputerUtil.playStack(spell, ai, game), "native mana payment casts the original Coup");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
    }
    static void run() {
        AiOverrideProfile.setActive("confiscation-coup");
        setup("Craw Wurm", "Hill Giant");
        check(ConfiscationCoupAi.uses(spell) && willing(), "profile admits Coup when a legal target costs at most four energy");
        castAndResolve();
        check(ai.getCardsIn(ZoneType.Battlefield).stream().anyMatch(c -> c.getName().equals("Hill Giant")), "native resolution gains control of the selected Hill Giant");
        check(ai.getCounters(CounterEnumType.ENERGY) == 0, "native energy payment spends the chosen four-mana-value target's four energy");

        AiOverrideProfile.setActive("confiscation-coup"); setup("Craw Wurm");
        check(!willing(), "holds when every legal opposing target costs more than the four energy Coup provides");
        check(!choseCoup(), "normal chooser declines Coup without an affordable target");

        AiOverrideProfile.setActive(""); setup("Hill Giant");
        check(!choseCoup(), "RemoveDeck still blocks Coup without its opt-in profile");
        AiOverrideProfile.setActive("choice-wipes"); setup("Hill Giant");
        check(!ConfiscationCoupAi.uses(spell) && !choseCoup(), "unrelated profile leaves Coup unchanged");

        AiOverrideProfile.setActive("confiscation-coup"); setup("Hill Giant");
        SpellAbility gain = spell.getSubAbility(); gain.getTargets().add(target);
        Cost cost = AbilityUtils.calculateUnlessCost(gain, gain.getParam("UnlessCost"), false);
        ai.setCounters(CounterEnumType.ENERGY, 4); refresh();
        check(nativeWillPay(gain, cost), "profile accepts native payment for the affordable target despite the sorcery host zone");
        target.setController(ai, 0); refresh();
        check(!nativeWillPay(gain, cost), "payment recheck declines if target is no longer opponent-controlled");

        AiOverrideProfile.setActive("confiscation-coup"); setup("Hill Giant");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent); refresh();
        check(!willing(), "native sorcery timing still prevents casting outside our turn");
    }
}
