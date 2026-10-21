import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.ai.ability.ChoosePlayerAi;
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

/** Native Sylvan Offering decision, target selection, payment and token resolution fixtures. */
public class SylvanOfferingTests {
    static Game game;
    static Player ai;
    static Card offering;
    static SpellAbility spell;
    static int passed;
    static void check(boolean ok, String label) {
        if (!ok) { throw new AssertionError(label); }
        passed++;
        System.out.println("PASS " + label);
    }
    static Card add(String name, Player owner, ZoneType zone) {
        Card c = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), owner);
        c.setGameTimestamp(game.getNextTimestamp());
        owner.getZone(zone).add(c);
        return c;
    }
    static Player p(int i) { return game.getPlayers().get(i); }
    static void refresh() {
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
    }
    static void setup(String profile) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Sylvan fixture " + i, null);
            lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Sylvan Offering native fixtures"));
        game.setAge(GameStage.Play);
        ai = p(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        for (int i = 0; i < 5; i++) { add("Forest", ai, ZoneType.Battlefield); }
        offering = add("Sylvan Offering", ai, ZoneType.Hand);
        spell = offering.getFirstSpellAbility();
        spell.setActivatingPlayer(ai);
        AiOverrideProfile.setActive(profile);
        refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai, spell).willingToPlay(); }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    static Player nativeChoice(Iterable<Player> choices) {
        return new ChoosePlayerAi().chooseSinglePlayer(ai, spell, choices, null);
    }
    static int creatures(Player p) { return p.getCreaturesInPlay().size(); }

    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(20261020));
        AiOverrideProfile.setActive("sylvan-offering");
        if (args.length > 0 && args[0].equals("baseline")) {
            setup("");
            add("Grizzly Bears", p(1), ZoneType.Battlefield);
            add("Grizzly Bears", p(1), ZoneType.Battlefield);
            add("Grizzly Bears", p(1), ZoneType.Battlefield);
            refresh();
            check(willing(), "baseline defect: native chooser approves symmetric gift without a board advantage");
            check(nativeChoice(ai.getOpponents()) == p(1), "baseline defect: native choice picks first opponent with more creatures");
            System.out.println("DONE " + passed + " baseline checks");
            return;
        }

        setup("sylvan-offering");
        Card anthem = add("Glorious Anthem", ai, ZoneType.Battlefield);
        for (int i = 0; i < 3; i++) { add("Grizzly Bears", p(1), ZoneType.Battlefield); }
        refresh();
        check(willing(), "second-main asymmetric anthem permits cast");
        check(spell.getXManaCostPaid() != null && spell.getXManaCostPaid() == 2, "X is capped at two");
        check(nativeChoice(ai.getOpponents()) == p(2), "native player chooser picks opponent with fewest creatures");
        SpellAbility picked = selected();
        check(picked != null && picked.getHostCard() == offering, "normal native AI selection chooses Sylvan Offering");
        check(ComputerUtil.playStack(spell, ai, game), "native spell payment puts Sylvan Offering on stack");
        game.getStack().resolveStack(); refresh();
        check(creatures(ai) == 3 && creatures(p(1)) == 3 && creatures(p(2)) == 3,
                "native resolution creates capped Treefolk and Elf tokens for AI and least-creature opponent");
        check(anthem != null && spell.getXManaCostPaid() == 2, "cast uses selected X and preserves the anthem");

        setup("sylvan-offering");
        add("Parallel Lives", ai, ZoneType.Battlefield);
        for (int i = 0; i < 3; i++) { add("Grizzly Bears", p(1), ZoneType.Battlefield); }
        refresh();
        check(willing(), "asymmetric own-token multiplier permits cast");
        check(ComputerUtil.playStack(spell, ai, game), "multiplier-backed native payment puts spell on stack");
        game.getStack().resolveStack(); refresh();
        check(creatures(ai) == 6 && creatures(p(1)) == 3 && creatures(p(2)) == 3,
                "native token replacement doubles only AI-created Treefolk and Elf tokens");

        setup("sylvan-offering");
        check(!willing(), "holds when no asymmetric anthem or multiplier advantage exists");
        setup("sylvan-offering");
        add("Glorious Anthem", ai, ZoneType.Battlefield); add("Glorious Anthem", p(1), ZoneType.Battlefield);
        refresh();
        check(!willing(), "holds when the selected opponent has an equal anthem advantage");
        setup("sylvan-offering"); add("Parallel Lives", ai, ZoneType.Battlefield); add("Anointed Procession", p(1), ZoneType.Battlefield);
        refresh();
        check(!willing(), "holds when the selected opponent has an equal token multiplier");
        setup("sylvan-offering"); add("Glorious Anthem", ai, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai); refresh();
        check(!willing(), "holds outside our second main phase");
        setup("sylvan-offering"); add("Glorious Anthem", ai, ZoneType.Battlefield);
        SpellAbility blocker = add("Grizzly Bears", p(1), ZoneType.Hand).getFirstSpellAbility();
        blocker.setActivatingPlayer(p(1)); game.getStack().add(blocker);
        check(!willing(), "holds with a non-empty stack");
        setup("sylvan-offering"); add("Glorious Anthem", ai, ZoneType.Battlefield);
        for (Card land : ai.getCardsIn(ZoneType.Battlefield)) { if (land.isLand()) { land.setTapped(true); } }
        refresh();
        check(!willing(), "holds when mana cannot pay even X one and the colored cost");

        for (String profile : List.of("", "molten-slagheap")) {
            setup(profile); add("Glorious Anthem", ai, ZoneType.Battlefield);
            check(willing(), "unrelated profile leaves native permissive chooser unchanged for '" + profile + "'");
            check(nativeChoice(ai.getOpponents()) == p(1), "unrelated profile keeps native first-opponent choice for '" + profile + "'");
            SpellAbility choice = selected();
            check(choice == null || choice.getHostCard() != offering, "blocked Sylvan Offering stays out of normal selection for '" + profile + "'");
        }
        AiOverrideProfile.setActive("sylvan-offering");
        System.out.println("DONE " + passed + " checks");
    }
}
