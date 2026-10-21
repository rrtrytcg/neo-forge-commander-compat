import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Native fixtures for the utility-lands profile: decisions, real activation/payment/resolution; no matches. */
public class UtilityLandsTests {
    static Game game;
    static Player ai;
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
    static void setup(PhaseType phase, int turn) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Utility lands fixture " + i, null);
            lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Utility lands native fixtures"));
        game.setAge(GameStage.Play);
        ai = p(0);
        game.getPhaseHandler().devModeSet(phase, p(turn));
        refresh();
    }
    static void lands(String name, int n) { for (int i = 0; i < n; i++) { add(name, ai, ZoneType.Battlefield); } }
    static SpellAbility ability(Card c, ApiType api) {
        SpellAbility sa = c.getSpellAbilities().stream().filter(s -> s.getApi() == api && !s.isManaAbility()).findFirst().orElseThrow();
        sa.setActivatingPlayer(ai);
        return sa;
    }
    static boolean willing(SpellAbility sa) { return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa).willingToPlay(); }
    static boolean selected(Card host) {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen != null && !chosen.isEmpty() && chosen.get(0).getHostCard() == host;
    }
    /** Reflection keeps the baseline fixture compilable against the unchanged candidate. */
    static Object util(String method, Object... args) {
        try {
            for (var m : Class.forName("forge.ai.UtilityLandsAi").getMethods()) {
                if (m.getName().equals(method)) { return m.invoke(null, args); }
            }
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        throw new IllegalStateException(method);
    }
    static void resolve() { game.getStack().resolveStack(); refresh(); }

    // ---- Labyrinth of Skophos
    static Card lab;
    static Combat attack(Card... attackers) {
        Combat combat = new Combat(p(1));
        game.getPhaseHandler().setCombat(combat);
        for (Card a : attackers) { combat.addAttacker(a, ai); }
        refresh();
        return combat;
    }
    static SpellAbility labSetup(int otherLands) {
        setup(PhaseType.COMBAT_DECLARE_BLOCKERS, 1);
        lab = add("Labyrinth of Skophos", ai, ZoneType.Battlefield);
        lands("Wastes", otherLands);
        return ability(lab, ApiType.RemoveFromCombat);
    }
    static void labyrinth(boolean baseline) {
        SpellAbility sa = labSetup(4);
        Card wurm = add("Craw Wurm", p(1), ZoneType.Battlefield);
        Combat combat = attack(wurm);
        if (baseline) { check(!willing(sa), "baseline: native Labyrinth never removes a 6-power attacker"); return; }
        check(willing(sa) && sa.getTargetCard() == wurm, "Labyrinth removes the unblocked 6-power attacker hitting us");
        check(selected(lab), "normal AI selection activates Labyrinth after blocks");
        check(ComputerUtil.playStack(sa, ai, game) && lab.isTapped(), "native activation pays {4},{T} and enters stack");
        resolve();
        check(!combat.isAttacking(wurm), "original effect removes the attacker from combat");

        sa = labSetup(4); attack(add("Grizzly Bears", p(1), ZoneType.Battlefield));
        check(!willing(sa), "2-power attacker at 20 life holds");
        sa = labSetup(4); ai.setLife(2, null); Card bears = add("Grizzly Bears", p(1), ZoneType.Battlefield); attack(bears);
        check(willing(sa) && sa.getTargetCard() == bears, "lethal unblocked damage removes even a small attacker");
        sa = labSetup(4); wurm = add("Craw Wurm", p(1), ZoneType.Battlefield); combat = attack(wurm);
        combat.addBlocker(wurm, add("Wall of Stone", ai, ZoneType.Battlefield)); refresh();
        check(!willing(sa), "blocked attacker holds");
        sa = labSetup(3); attack(add("Craw Wurm", p(1), ZoneType.Battlefield));
        check(!willing(sa), "three other untapped lands hold");
        sa = labSetup(4); wurm = add("Craw Wurm", p(1), ZoneType.Battlefield);
        combat = new Combat(p(1)); game.getPhaseHandler().setCombat(combat); combat.addAttacker(wurm, p(2)); refresh();
        check(!willing(sa), "attacker aimed at another opponent holds");
        sa = labSetup(4); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, p(1)); attack(add("Craw Wurm", p(1), ZoneType.Battlefield));
        check(!willing(sa), "before blocks holds");
        sa = labSetup(4); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, ai);
        Combat ours = new Combat(ai); game.getPhaseHandler().setCombat(ours); ours.addAttacker(add("Craw Wurm", ai, ZoneType.Battlefield), p(1)); refresh();
        check(!willing(sa), "our own combat holds");
    }

    // ---- Crucible of the Spirit Dragon: deferred; documents why (native payment cannot spend storage mana).
    static void crucibleDeferred() {
        setup(PhaseType.MAIN2, 0);
        Card crucible = add("Crucible of the Spirit Dragon", ai, ZoneType.Battlefield);
        crucible.setCounters(CounterEnumType.STORAGE, 4); lands("Mountain", 2); refresh();
        SpellAbility dragon = add("Shivan Dragon", ai, ZoneType.Hand).getFirstSpellAbility(); dragon.setActivatingPlayer(ai);
        check(!ComputerUtilMana.canPayManaCost(dragon, ai, 0, false), "deferral reason: native payment ignores 4 stored Dragon mana");
        check(AiOverrideProfile.get().getAiLogic(crucible).isEmpty() && ComputerUtilCard.isCardRemAIDeck(crucible), "Crucible stays blocked and unmapped");
    }

    static Card helios;
    static SpellAbility heliosMain(int otherLands, int energy) {
        setup(PhaseType.MAIN2, 0);
        helios = add("HELIOS One", ai, ZoneType.Battlefield); lands("Wastes", otherLands);
        ai.setCounters(CounterEnumType.ENERGY, energy); refresh();
        return ability(helios, ApiType.Destroy);
    }
    static void helios(boolean baseline) {
        setup(PhaseType.END_OF_TURN, 3);
        helios = add("HELIOS One", ai, ZoneType.Battlefield); lands("Wastes", 1);
        SpellAbility energy = ability(helios, ApiType.PutCounter);
        if (baseline) {
            SpellAbility kill = heliosMain(5, 4); add("Hill Giant", p(1), ZoneType.Battlefield); refresh();
            check(!willing(kill), "baseline: native HELIOS does not destroy with banked energy");
            return;
        }
        check(willing(energy), "HELIOS takes energy at the end step of the player right before us");
        check(ComputerUtil.playStack(energy, ai, game), "native energy activation pays {1},{T}");
        resolve();
        check(ai.getCounters(CounterEnumType.ENERGY) == 1, "original effect gives one energy");
        setup(PhaseType.END_OF_TURN, 2); helios = add("HELIOS One", ai, ZoneType.Battlefield); lands("Wastes", 1);
        check(!willing(ability(helios, ApiType.PutCounter)), "earlier opponent's end step holds");

        SpellAbility kill = heliosMain(5, 4);
        Card giant = add("Hill Giant", p(1), ZoneType.Battlefield); add("Craw Wurm", p(2), ZoneType.Battlefield); add("Grizzly Bears", p(3), ZoneType.Battlefield); refresh();
        check(willing(kill) && kill.getTargetCard() == giant, "HELIOS targets the opposing MV4 permanent it can pay for, not MV6 or MV2");
        check(ComputerUtil.playStack(kill, ai, game) && !helios.isInPlay(), "native activation pays {3}, energy and sacrifices HELIOS");
        check(ai.getCounters(CounterEnumType.ENERGY) == 0, "energy paid equals the target's mana value");
        resolve();
        check(!giant.isInPlay(), "original effect destroys the target");
        kill = heliosMain(5, 9); Card wurm = add("Craw Wurm", p(1), ZoneType.Battlefield); add("Hill Giant", p(2), ZoneType.Battlefield); refresh();
        check(willing(kill) && kill.getTargetCard() == wurm, "more energy: best opposing permanent by native evaluation");
        kill = heliosMain(5, 4); add("Grizzly Bears", p(1), ZoneType.Battlefield); refresh();
        check(!willing(kill), "only MV2 targets hold");
        kill = heliosMain(4, 4); add("Hill Giant", p(1), ZoneType.Battlefield); refresh();
        check(!willing(kill), "fewer than five other lands holds");
        kill = heliosMain(5, 3); add("Hill Giant", p(1), ZoneType.Battlefield); refresh();
        check(!willing(kill), "not enough energy (3 for MV4) holds");
        kill = heliosMain(5, 4); add("Hill Giant", ai, ZoneType.Battlefield); refresh();
        check(!willing(kill), "own permanents are never targets");
        kill = heliosMain(5, 4); add("Hill Giant", p(1), ZoneType.Battlefield); game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p(1)); refresh();
        check(!willing(kill), "opponent's turn holds");
    }

    static Card stage;
    static SpellAbility stageSetup(PhaseType phase, int otherLands) {
        setup(phase, 0);
        stage = add("Thespian's Stage", ai, ZoneType.Battlefield);
        String[] mine = {"Forest", "Forest", "Island"};
        for (int i = 0; i < otherLands; i++) { add(mine[i % 3], ai, ZoneType.Battlefield); }
        refresh();
        return ability(stage, ApiType.Clone);
    }
    static void stage(boolean baseline) {
        SpellAbility sa = stageSetup(PhaseType.MAIN2, 3);
        Card plains = add("Plains", p(1), ZoneType.Battlefield); add("Swamp", p(2), ZoneType.Battlefield); refresh();
        if (baseline) { return; }
        check(willing(sa) && sa.getTargetCard() == plains, "Stage copies a basic of our scarcest colour (white first in WUBRG ties)");
        check(ComputerUtil.playStack(sa, ai, game) && stage.isTapped(), "native activation pays {2},{T}");
        resolve();
        check(stage.getType().hasSubtype("Plains") && stage.canProduceColorMana(Set.of("white")), "original effect makes Stage a Plains copy");
        check(!willing(sa), "a copied Stage does not copy again");
        sa = stageSetup(PhaseType.MAIN2, 3); Card ownPlains = add("Plains", ai, ZoneType.Battlefield); add("Plains", p(1), ZoneType.Battlefield);
        add("Swamp", p(1), ZoneType.Battlefield); refresh();
        check(willing(sa) && sa.getTargetCard() != ownPlains && sa.getTargetCard().getType().hasSubtype("Swamp"), "colour counts include our lands: scarcest becomes black");
        sa = stageSetup(PhaseType.MAIN1, 3); add("Plains", p(1), ZoneType.Battlefield); refresh();
        check(!willing(sa), "first main holds");
        sa = stageSetup(PhaseType.MAIN2, 1); add("Plains", p(1), ZoneType.Battlefield); refresh();
        check(!willing(sa), "one other untapped land holds");
        sa = stageSetup(PhaseType.MAIN2, 3); add("Tundra", p(1), ZoneType.Battlefield); refresh();
        check(!willing(sa), "no basic of the scarce colour: nonbasic lands are never copied");
    }

    static Card dakmor;
    static void dredgeSetup(int library, int landsInPlay, boolean landInHand) {
        setup(PhaseType.DRAW, 0);
        dakmor = add("Dakmor Salvage", ai, ZoneType.Graveyard);
        for (int i = 0; i < library; i++) { add("Grizzly Bears", ai, ZoneType.Library); }
        lands("Swamp", landsInPlay);
        if (landInHand) { add("Swamp", ai, ZoneType.Hand); }
        refresh();
    }
    static void dakmor(boolean baseline) {
        dredgeSetup(25, 3, false);
        ai.drawCard();
        boolean dredged = dakmor.isInZone(ZoneType.Hand) || game.getCardState(dakmor).isInZone(ZoneType.Hand);
        if (baseline) {
            System.out.println("INFO baseline native dredge choice with no land in hand: " + (dredged ? "dredge" : "draw"));
            return;
        }
        check(dredged && ai.getCardsIn(ZoneType.Library).size() == 23 && ai.getCardsIn(ZoneType.Hand).size() == 1,
                "no land in hand: dredge returns Salvage and mills two instead of drawing");
        dredgeSetup(25, 3, true); ai.drawCard();
        check(game.getCardState(dakmor).isInZone(ZoneType.Graveyard) && ai.getCardsIn(ZoneType.Library).size() == 24, "land in hand: normal draw");
        dredgeSetup(19, 3, false); ai.drawCard();
        check(game.getCardState(dakmor).isInZone(ZoneType.Graveyard) && ai.getCardsIn(ZoneType.Library).size() == 18, "library under 20: normal draw");
        dredgeSetup(25, 7, false); ai.drawCard();
        check(game.getCardState(dakmor).isInZone(ZoneType.Graveyard), "seven lands in play: normal draw");
    }

    static void boundaries() {
        for (String profile : List.of("", "abzan-armor")) {
            AiOverrideProfile.setActive(profile);
            SpellAbility sa = labSetup(4); attack(add("Craw Wurm", p(1), ZoneType.Battlefield));
            check(util("decide", ai, sa) == null && !willing(sa), "native Labyrinth unchanged for profile '" + profile + "'");
            setup(PhaseType.END_OF_TURN, 3); Card c = add("HELIOS One", ai, ZoneType.Battlefield); lands("Wastes", 1);
            check(util("decide", ai, ability(c, ApiType.PutCounter)) == null, "HELIOS energy rule inactive for profile '" + profile + "'");
            SpellAbility kill = heliosMain(5, 4); add("Hill Giant", p(1), ZoneType.Battlefield); refresh();
            check(util("decide", ai, kill) == null, "HELIOS rule inactive for profile '" + profile + "'");
            sa = stageSetup(PhaseType.MAIN2, 3);
            check(util("decide", ai, sa) == null, "Stage rule inactive for profile '" + profile + "'");
            dredgeSetup(25, 3, false);
            check(util("dredge", dakmor.getReplacementEffects().iterator().next(), dakmor) == null, "Dakmor rule inactive for profile '" + profile + "'");
            check(ComputerUtilCard.isCardRemAIDeck(dakmor), "original AI:RemoveDeck hint retained");
        }
        AiOverrideProfile.setActive("utility-lands");
    }

    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(20261019));
        boolean baseline = args.length > 0 && args[0].equals("baseline");
        AiOverrideProfile.setActive("utility-lands");
        labyrinth(baseline);
        if (!baseline) { crucibleDeferred(); }
        helios(baseline);
        stage(baseline);
        dakmor(baseline);
        if (!baseline) { boundaries(); }
        System.out.println("DONE " + passed + " checks");
    }
}
