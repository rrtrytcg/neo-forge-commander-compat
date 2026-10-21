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

/** Reproduces Confiscation Coup's native energy-payment and selection failures. */
public class ConfiscationCoupBaselineTests {
    static Game game;
    static Player ai;
    static Card source;
    static Card target;
    static SpellAbility spell;
    static int passed;
    static void check(boolean ok, String label) {
        if (!ok) { throw new AssertionError(label); }
        passed++; System.out.println("PASS " + label);
    }
    static Card add(String name, Player owner, ZoneType zone) {
        Card c = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); c.setSickness(false); return c;
    }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static void setup() {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i=0; i<2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Confiscation Coup baseline " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Confiscation Coup baseline")); game.setAge(GameStage.Play);
        ai = game.getPlayers().get(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        for (int i=0; i<5; i++) { add(i<3 ? "Island" : "Mountain", ai, ZoneType.Battlefield); }
        target = add("Serra Angel", game.getPlayers().get(1), ZoneType.Battlefield);
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library); }
        source = add("Confiscation Coup", ai, ZoneType.Hand); spell = source.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        refresh();
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
        AiOverrideProfile.setActive(""); setup(); ai.setCounters(CounterEnumType.ENERGY, 4); refresh();
        SpellAbility gain = spell.getSubAbility();
        check(gain != null && gain.getApi().toString().equals("GainControl"), "baseline: Coup has a native gain-control subability");
        gain.getTargets().add(target);
        Cost cost = AbilityUtils.calculateUnlessCost(gain, gain.getParam("UnlessCost"), false);
        check(cost.getCostEnergy() != null && cost.getCostEnergy().getAbilityAmount(gain) == target.getCMC(), "baseline: native optional payment is the target's mana-value energy");
        check(!new ControlGainAi().willPayUnlessCost(ai, gain, cost, false, new FCollection<>(ai)), "baseline: ControlGainAi refuses Coup's optional payment because its sorcery host is not on the battlefield");
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        check(chosen == null || chosen.stream().noneMatch(sa -> sa.getHostCard() == source), "baseline: RemoveDeck blocks Coup from normal selection");
        AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Confiscation Coup baseline checks");
    }
}
