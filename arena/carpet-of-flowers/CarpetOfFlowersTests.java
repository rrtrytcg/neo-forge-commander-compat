import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.AbilityKey;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Native Carpet casting, public Island-count targeting and mana production. */
public class CarpetOfFlowersTests {
    static Game game;
    static Player ai;
    static Card carpet;
    static SpellAbility spell;
    static int passed;

    static void check(boolean ok, String label) {
        if (!ok) { throw new AssertionError(label); }
        passed++; System.out.println("PASS " + label);
    }
    static Player p(int i) { return game.getPlayers().get(i); }
    static Card add(String name, Player owner, ZoneType zone) {
        Card c = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); return c;
    }
    static void add(String name, Player owner, ZoneType zone, int count) { for (int i = 0; i < count; i++) { add(name, owner, zone); } }
    static Card island(Player owner) { return add("Island", owner, ZoneType.Battlefield); }
    static void refresh() {
        game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
    }
    static void setup(int... islands) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Carpet of Flowers fixture " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Carpet of Flowers native fixtures")); game.setAge(GameStage.Play);
        ai = p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        add("Forest", ai, ZoneType.Battlefield, 5);
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library, 20); }
        for (int i = 0; i < Math.min(islands.length, 3); i++) { add("Island", p(i + 1), ZoneType.Battlefield, islands[i]); }
        carpet = add("Carpet of Flowers", ai, ZoneType.Hand); spell = carpet.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai, spell).willingToPlay(); }
    static SpellAbility selected() {
        List<SpellAbility> choices = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return choices == null || choices.isEmpty() ? null : choices.get(0);
    }
    static void resolveAll() {
        for (int i = 0; i < 12; i++) {
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) { break; }
            game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        }
    }
    static SpellAbility top() { return game.getStack().peekAbility(); }
    static List<Player> targetPlayers(SpellAbility sa) {
        List<Player> result = new ArrayList<>(); for (Player player : sa.getTargets().getTargetPlayers()) { result.add(player); } return result;
    }
    static void baseline() {
        setup(1, 2, 4); AiOverrideProfile.setActive("");
        SpellAbility choice = selected();
        check(choice == null || choice.getHostCard() != carpet, "baseline: RemoveDeck blocks normal Carpet of Flowers selection");
    }
    static void casting() {
        AiOverrideProfile.setActive("carpet-of-flowers");
        setup(1, 0, 0);
        check(willing(), "native noncreature AI accepts the profile-enabled cast with a positive Island payoff");
        check(selected() != null && selected().getHostCard() == carpet, "normal AI selection chooses Carpet of Flowers");
        setup(0, 0, 0); check(!willing(), "holds when no opponent controls an Island");
        setup(1, 0, 0); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai); check(!willing(), "retains native Main2 permanent timing");
        setup(1, 0, 0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p(1)); check(!willing(), "holds on an opponent turn");
        setup(1, 0, 0); game.getStack().add(spell.copy(ai)); check(!willing(), "holds with a nonempty stack");
    }
    static void triggerTargetAndMana() {
        AiOverrideProfile.setActive("carpet-of-flowers"); setup(1, 2, 4);
        check(ComputerUtil.playStack(spell, ai, game), "native cost payment puts Carpet on the stack"); resolveAll();
        carpet = game.getCardState(carpet); check(carpet.isInPlay(), "native resolution puts Carpet onto the battlefield");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(ai), false);
        game.getStack().addAllTriggeredAbilitiesToStack();
        SpellAbility trigger = top();
        check(trigger != null && trigger.getHostCard() == carpet, "native first-main trigger is placed on the stack");
        List<Player> targets = trigger == null ? List.of() : targetPlayers(trigger);
        check(targets.size() == 1 && targets.get(0) == p(3), "trigger targets the opponent with the most Islands");
        resolveAll();
        check(ai.getManaPool().totalMana() == 4, "native mana subability produces four mana for four Islands");
        setup(2, 2, 0); carpet = add("Carpet of Flowers", ai, ZoneType.Battlefield); refresh();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(ai), false);
        game.getStack().addAllTriggeredAbilitiesToStack(); trigger = top();
        targets = trigger == null ? List.of() : targetPlayers(trigger);
        check(targets.size() == 1 && targets.get(0) == p(1), "equal Island counts break ties by opponent order");
    }
    static void isolation() {
        for (String profile : List.of("", "immoral-bargain", "doomsday-confluence")) {
            AiOverrideProfile.setActive(profile); setup(1, 0, 0);
            SpellAbility choice = selected(); check(choice == null || choice.getHostCard() != carpet, "blocked card remains unselected under '" + profile + "'");
            check(willing(), "direct native cast evaluation remains unchanged under profile '" + profile + "'");
        }
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
        boolean baseline = args.length > 0 && args[0].equals("baseline");
        AiOverrideProfile.setActive(baseline ? "" : "carpet-of-flowers");
        if (baseline) { baseline(); } else { casting(); triggerTargetAndMana(); isolation(); }
        AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Carpet of Flowers checks");
    }
}
