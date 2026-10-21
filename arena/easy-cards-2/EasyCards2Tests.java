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

/** Native fixtures for the easy-cards profile: decisions, selection, real payment/resolution; no matches. */
public class EasyCards2Tests {
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
    static void add(String name, Player owner, ZoneType zone, int n) { for (int i = 0; i < n; i++) { add(name, owner, zone); } }
    static Player p(int i) { return game.getPlayers().get(i); }
    static void refresh() {
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
    }
    static void setup(PhaseType phase, int turn) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Easy cards 2 fixture " + i, null);
            lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Easy cards 2 native fixtures"));
        game.setAge(GameStage.Play);
        ai = p(0);
        game.getPhaseHandler().devModeSet(phase, p(turn));
        refresh();
    }
    static SpellAbility spell(Card c) { SpellAbility sa = c.getFirstSpellAbility(); sa.setActivatingPlayer(ai); return sa; }
    static boolean willing(SpellAbility sa) { return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa).willingToPlay(); }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    static void resolveAll() {
        for (int i = 0; i < 8; i++) {
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) { break; }
            game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        }
    }
    static int hand(Player pl) { return pl.getCardsIn(ZoneType.Hand).size(); }
    /** Reflection keeps the baseline fixture compilable against the unchanged candidate. */
    static Object util(String method, Object... args) {
        try {
            for (var m : Class.forName("forge.ai.EasyCards2Ai").getMethods()) {
                if (m.getName().equals(method)) { return m.invoke(null, args); }
            }
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        throw new IllegalStateException(method);
    }

    static void notSelected(Card host, String label) {
        SpellAbility chosen = selected();
        check(chosen == null || chosen.getHostCard() != host, label);
    }
    static SpellAbility ability(Card c, ApiType api) {
        SpellAbility sa = c.getSpellAbilities().stream().filter(s -> s.isActivatedAbility() && s.getApi() == api && !s.isCycling()).findFirst().orElseThrow();
        sa.setActivatingPlayer(ai);
        return sa;
    }
    static void damage(Player pl, int n) { pl.receiveDamage(org.apache.commons.lang3.tuple.Pair.of(n, false)); }
    static void counters(Card c, CounterEnumType type, int n) { c.addCounterInternal(type, n, ai, false, null, null); }
    static boolean inGraveyard(Player pl, String name) { return pl.getCardsIn(ZoneType.Graveyard).stream().anyMatch(c -> c.getName().equals(name)); }

    // ---- Autumn Willow
    static void autumnWillow(boolean baseline) {
        setup(PhaseType.MAIN2, 0);
        add("Forest", ai, ZoneType.Battlefield, 7);
        Card willow = add("Autumn Willow", ai, ZoneType.Hand); refresh();
        if (baseline) { notSelected(willow, "baseline: blocked Autumn Willow is never cast"); return; }
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == willow, "normal native AI selection casts Autumn Willow");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment casts it");
        resolveAll();
        willow = game.getCardState(willow);
        check(willow.isInPlay() && willow.hasKeyword("Shroud"), "original card enters with shroud");
        SpellAbility drop = ability(willow, ApiType.Effect);
        check(!willing(drop), "the shroud-dropping ability is vetoed");
        notSelected(willow, "normal AI selection never activates it with mana open");
    }

    // ---- Edge of Autumn
    static Card edgeSetup(int lands, String libraryCard, int turn) {
        setup(PhaseType.MAIN2, turn);
        add("Forest", ai, ZoneType.Battlefield, lands);
        add(libraryCard, ai, ZoneType.Library, 10);
        Card edge = add("Edge of Autumn", ai, ZoneType.Hand); refresh();
        return edge;
    }
    static void edgeOfAutumn(boolean baseline) {
        Card edge = edgeSetup(3, "Forest", 0);
        if (baseline) { notSelected(edge, "baseline: blocked Edge of Autumn is never cast"); return; }
        check(willing(spell(edge)), "3 lands and a basic in the library: casts");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == edge && chosen.isSpell(), "normal native AI selection casts it, not the cycling");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment casts it");
        resolveAll();
        check(ai.getLandsInPlay().size() == 4 && ai.getCardsIn(ZoneType.Library).size() == 9, "original effect fetches a basic onto the battlefield");
        check(!willing(spell(edgeSetup(5, "Forest", 0))), "five lands: the condition would fail, holds");
        check(!willing(spell(edgeSetup(3, "Grizzly Bears", 0))), "no basic land in the library: holds");
        edge = edgeSetup(3, "Forest", 0);
        SpellAbility cycling = edge.getSpellAbilities().stream().filter(SpellAbility::isCycling).findFirst().orElseThrow();
        cycling.setActivatingPlayer(ai);
        check(!willing(cycling), "cycling (sacrificing a land) is vetoed");
        check(!willing(spell(edgeSetup(3, "Forest", 1))), "opponent's turn holds");
    }

    // ---- Knollspine Dragon
    static Card dragonSetup(int handLands, int library) {
        setup(PhaseType.MAIN2, 0);
        add("Mountain", ai, ZoneType.Battlefield, 7);
        add("Grizzly Bears", ai, ZoneType.Library, library);
        Card dragon = add("Knollspine Dragon", ai, ZoneType.Hand);
        add("Forest", ai, ZoneType.Hand, handLands); refresh();
        return dragon;
    }
    static void knollspine(boolean baseline) {
        Card dragon = dragonSetup(0, 15);
        if (baseline) { notSelected(dragon, "baseline: blocked Knollspine Dragon is never cast"); return; }
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == dragon, "second main: normal native AI selection casts the Dragon");
        dragon = dragonSetup(2, 15);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai); refresh();
        check(!willing(spell(dragon)), "first main holds so combat damage counts");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai); refresh();
        damage(p(1), 3);
        damage(p(2), 5);
        check(ComputerUtil.playStack(spell(dragon), ai, game), "native payment casts it");
        resolveAll();
        check(game.getCardState(dragon).isInPlay() && hand(ai) == 5 && ai.getCardsIn(ZoneType.Library).size() == 10,
                "5 damage beat the 2-card hand: discard and draw 5 from the most damaged opponent");

        dragon = dragonSetup(3, 15);
        damage(p(1), 2);
        check(ComputerUtil.playStack(spell(dragon), ai, game), "native payment casts it again");
        resolveAll();
        check(game.getCardState(dragon).isInPlay() && hand(ai) == 3 && ai.getCardsIn(ZoneType.Library).size() == 15, "2 damage against a 3-card hand: keeps the hand");

        dragon = dragonSetup(0, 3);
        damage(p(1), 6);
        check(ComputerUtil.playStack(spell(dragon), ai, game), "native payment casts it with a short library");
        resolveAll();
        check(ai.getCardsIn(ZoneType.Library).size() == 3, "library not above the draw: declines");
    }

    // ---- Bosh, Iron Golem
    static Card bosh;
    static void boshSetup(String... artifacts) {
        setup(PhaseType.MAIN2, 0);
        add("Mountain", ai, ZoneType.Battlefield, 4);
        bosh = add("Bosh, Iron Golem", ai, ZoneType.Battlefield);
        for (String a : artifacts) { add(a, ai, ZoneType.Battlefield); }
        refresh();
    }
    static SpellAbility boshAbility() { return ability(bosh, ApiType.DealDamage); }
    static void boshIronGolem(boolean baseline) {
        boshSetup("Juggernaut", "Wurmcoil Engine", "Sol Ring");
        Card giant = add("Hill Giant", p(1), ZoneType.Battlefield); refresh();
        SpellAbility sa = boshAbility();
        if (baseline) { notSelected(bosh, "baseline: blocked Bosh never activates"); return; }
        check(willing(sa) && sa.getTargets().getFirstTargetedCard() == giant, "targets the killable opposing creature");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == bosh, "normal native AI selection activates Bosh");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment: 3R and a sacrifice");
        resolveAll();
        check(inGraveyard(ai, "Juggernaut") && !inGraveyard(ai, "Wurmcoil Engine") && !inGraveyard(ai, "Sol Ring") && bosh.isInPlay(),
                "sacrificed the cheapest sufficient artifact (Juggernaut), not Wurmcoil, Sol Ring or Bosh");
        check(game.getCardState(giant).isInZone(ZoneType.Graveyard), "original effect: 4 damage kills the Hill Giant");

        boshSetup("Gilded Lotus", "Sol Ring"); Player victim = p(1); victim.setLife(5, null); add("Hill Giant", p(1), ZoneType.Battlefield); refresh();
        sa = boshAbility();
        check(willing(sa) && sa.getTargets().getFirstTargetedPlayer() == victim, "lethal on a player beats killing a creature");
        check(ComputerUtil.playStack(sa, ai, game), "native payment sacrifices for lethal");
        resolveAll();
        check(victim.getLife() <= 0 && inGraveyard(ai, "Gilded Lotus") && !inGraveyard(ai, "Sol Ring"), "Gilded Lotus (MV5) sacrificed for exactly lethal");

        boshSetup("Sol Ring", "Mind Stone"); add("Hill Giant", p(1), ZoneType.Battlefield); refresh();
        check(!willing(boshAbility()), "only artifacts below MV 3: holds");
        boshSetup("Juggernaut"); add("Craw Wurm", p(1), ZoneType.Battlefield); add("Hill Giant", p(1), ZoneType.Battlefield); refresh();
        sa = boshAbility();
        check(willing(sa) && sa.getTargets().getFirstTargetedCard().getName().equals("Craw Wurm"), "kills the best creature the MV 4 artifact can kill");
        boshSetup("Juggernaut"); add("Craw Wurm", p(1), ZoneType.Battlefield); counters(p(1).getCreaturesInPlay().getFirst(), CounterEnumType.P1P1, 2); refresh();
        check(!willing(boshAbility()), "a 6-toughness creature is out of reach: holds");
        boshSetup(); add("Hill Giant", p(1), ZoneType.Battlefield); refresh();
        check(!willing(boshAbility()), "Bosh alone is never sacrificed to itself: holds");
        boshSetup("Juggernaut"); add("Hill Giant", p(1), ZoneType.Battlefield); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, p(1)); refresh();
        check(willing(boshAbility()), "instant speed: also acts on an opponent's turn");
    }

    // ---- Ashling the Pilgrim
    static Card ashling;
    static void ashlingSetup(PhaseType phase, int turn) {
        setup(phase, turn);
        add("Mountain", ai, ZoneType.Battlefield, 8);
        ashling = add("Ashling the Pilgrim", ai, ZoneType.Battlefield);
        refresh();
    }
    static SpellAbility ashlingAbility() { return ability(ashling, ApiType.PutCounter); }
    static void ashlingThePilgrim(boolean baseline) {
        ashlingSetup(PhaseType.MAIN2, 0);
        SpellAbility sa = ashlingAbility();
        if (baseline) { notSelected(ashling, "baseline: blocked Ashling never activates"); return; }
        check(willing(sa), "second main with spare mana: grows");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == ashling, "normal native AI selection activates Ashling");
        check(ComputerUtil.playStack(sa, ai, game), "native payment 1R");
        resolveAll();
        check(ashling.getCounters(CounterEnumType.P1P1) == 1 && sa.getResolvedThisTurn() == 1, "original effect: one counter, one resolution recorded");
        check(willing(sa) && ComputerUtil.playStack(sa, ai, game), "second activation allowed");
        resolveAll();
        check(ashling.getCounters(CounterEnumType.P1P1) == 2, "two counters after two activations");
        check(!willing(sa), "third activation holds when it would not kill an opponent (3 damage vs 20 life)");
        p(1).setLife(4, null); refresh();
        check(!willing(sa), "opponent at 4 survives 3 damage: holds");
        Player foe = p(1);
        foe.setLife(3, null); refresh();
        check(willing(sa), "opponent at 3 dies to the 3-damage sweep: fires");
        ai.setLife(3, null); refresh();
        check(!willing(sa), "we would die to our own sweep: holds");
        ai.setLife(20, null); refresh();
        check(ComputerUtil.playStack(sa, ai, game), "native payment of the third activation");
        resolveAll();
        check(foe.getLife() == 0 && ai.getLife() == 17 && game.getCardState(ashling).isInZone(ZoneType.Graveyard), "original sweep: 3 damage to everyone, Ashling dies");

        ashlingSetup(PhaseType.MAIN1, 0);
        check(!willing(ashlingAbility()), "first main holds");
        ashlingSetup(PhaseType.END_OF_TURN, 1);
        check(willing(ashlingAbility()), "opponent's end step with spare mana: grows");
        ashlingSetup(PhaseType.MAIN2, 0);
        SpellAbility s = ashlingAbility();
        for (int i = 0; i < 2; i++) { ComputerUtil.playStack(s, ai, game); resolveAll(); }
        Player foe1 = p(1), foe2 = p(2);
        foe1.setLife(1, null); foe2.setLife(1, null); refresh();
        check(willing(s) && ComputerUtil.playStack(s, ai, game), "lethal sweep also fires in a multiplayer game");
        resolveAll();
        check(foe1.getLife() <= 0 && foe2.getLife() <= 0, "sweep hits every opponent");
    }

    // ---- Benevolent Offering
    static Card offeringSetup(PhaseType phase, int ours, int theirs1, int theirs2) {
        setup(phase, 0);
        add("Plains", ai, ZoneType.Battlefield, 4);
        add("Grizzly Bears", ai, ZoneType.Battlefield, ours);
        add("Grizzly Bears", p(1), ZoneType.Battlefield, theirs1);
        add("Grizzly Bears", p(2), ZoneType.Battlefield, theirs2);
        add("Grizzly Bears", p(3), ZoneType.Battlefield, 2);
        Card offering = add("Benevolent Offering", ai, ZoneType.Hand); refresh();
        return offering;
    }
    static void benevolentOffering(boolean baseline) {
        Card offering = offeringSetup(PhaseType.MAIN2, 3, 1, 0);
        if (baseline) { notSelected(offering, "baseline: blocked Benevolent Offering is never cast"); return; }
        check(willing(spell(offering)), "3 creatures against opponents with 1, 0 and 2: casts");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == offering, "normal native AI selection casts it");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment");
        resolveAll();
        check(ai.getCreaturesInPlay().size() == 6 && p(2).getCreaturesInPlay().size() == 3 && p(1).getCreaturesInPlay().size() == 1,
                "first choice: the opponent with the fewest creatures gets the tokens");
        check(ai.getLife() == 32 && p(1).getLife() == 22 && p(2).getLife() == 20 && p(3).getLife() == 20,
                "second choice re-picks the now-fewest opponent: we gain 12, that one gains 2");
        check(!willing(spell(offeringSetup(PhaseType.MAIN2, 2, 1, 1))), "only one creature ahead: holds");
        check(!willing(spell(offeringSetup(PhaseType.MAIN1, 4, 0, 0))), "first main holds");
    }

    // ---- Peel from Reality
    static Card peelSetup(PhaseType phase, int turn, String own, String theirs) {
        setup(phase, turn);
        add("Island", ai, ZoneType.Battlefield, 2);
        if (own != null) { add(own, ai, ZoneType.Battlefield); }
        if (theirs != null) { add(theirs, p(1), ZoneType.Battlefield); }
        Card peel = add("Peel from Reality", ai, ZoneType.Hand); refresh();
        return peel;
    }
    static void peelFromReality(boolean baseline) {
        Card peel = peelSetup(PhaseType.MAIN2, 0, "Elvish Visionary", "Craw Wurm");
        if (baseline) { notSelected(peel, "baseline: blocked Peel from Reality is never cast"); return; }
        Card visionary = ai.getCreaturesInPlay().getFirst();
        Card wurm = p(1).getCreaturesInPlay().getFirst();
        SpellAbility sa = spell(peel);
        check(willing(sa) && sa.getTargets().getFirstTargetedCard() == visionary && sa.getSubAbility().getTargets().getFirstTargetedCard() == wurm,
                "returns our enter-trigger creature and their best creature");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == peel, "normal native AI selection casts it");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment");
        resolveAll();
        check(game.getCardState(visionary).isInZone(ZoneType.Hand) && game.getCardState(wurm).isInZone(ZoneType.Hand), "original effect returns both to hand");

        check(!willing(spell(peelSetup(PhaseType.MAIN2, 0, "Grizzly Bears", "Craw Wurm"))), "a vanilla creature of ours is not worth resetting: holds");
        peel = peelSetup(PhaseType.MAIN2, 0, "Craw Wurm", "Craw Wurm");
        Card cursed = ai.getCreaturesInPlay().getFirst();
        counters(cursed, CounterEnumType.M1M1, 3); refresh();
        sa = spell(peel);
        check(willing(sa) && sa.getTargets().getFirstTargetedCard() == cursed, "a creature with -1/-1 counters is reset");
        check(!willing(spell(peelSetup(PhaseType.MAIN2, 0, "Elvish Visionary", "Elvish Visionary"))), "their creature no better than ours: holds");
        check(!willing(spell(peelSetup(PhaseType.MAIN2, 0, "Elvish Visionary", null))), "no opposing creature: holds");
        check(!willing(spell(peelSetup(PhaseType.MAIN1, 1, "Elvish Visionary", "Craw Wurm"))), "not our turn: holds");
    }

    static void boundaries() {
        for (String profile : List.of("", "abzan-armor")) {
            AiOverrideProfile.setActive(profile);
            Card edge = edgeSetup(3, "Forest", 0);
            check(util("decide", ai, spell(edge)) == null, "Edge rule inactive for profile '" + profile + "'");
            SpellAbility chosen = selected();
            check(chosen == null || chosen.getHostCard() != edge, "blocked cards stay blocked for profile '" + profile + "'");
            boshSetup("Juggernaut"); add("Hill Giant", p(1), ZoneType.Battlefield); refresh();
            check(util("decide", ai, boshAbility()) == null, "Bosh rule inactive for profile '" + profile + "'");
            ashlingSetup(PhaseType.MAIN2, 0);
            check(util("decide", ai, ashlingAbility()) == null, "Ashling rule inactive for profile '" + profile + "'");
        }
        AiOverrideProfile.setActive("easy-cards-2");
    }

    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(20261020));
        boolean baseline = args.length > 0 && args[0].equals("baseline");
        AiOverrideProfile.setActive("easy-cards-2");
        autumnWillow(baseline);
        edgeOfAutumn(baseline);
        knollspine(baseline);
        boshIronGolem(baseline);
        ashlingThePilgrim(baseline);
        benevolentOffering(baseline);
        peelFromReality(baseline);
        if (!baseline) { boundaries(); }
        System.out.println("DONE " + passed + " checks");
    }
}
