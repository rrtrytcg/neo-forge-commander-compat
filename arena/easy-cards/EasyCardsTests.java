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
public class EasyCardsTests {
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
            LobbyPlayerAi lobby = new LobbyPlayerAi("Easy cards fixture " + i, null);
            lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Easy cards native fixtures"));
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
            for (var m : Class.forName("forge.ai.EasyCardsAi").getMethods()) {
                if (m.getName().equals(method)) { return m.invoke(null, args); }
            }
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        throw new IllegalStateException(method);
    }

    // ---- Syr Elenora: native play only
    static void elenora(boolean baseline) {
        setup(PhaseType.MAIN2, 0);
        add("Island", ai, ZoneType.Battlefield, 5);
        add("Grizzly Bears", ai, ZoneType.Library, 10);
        Card elenora = add("Syr Elenora, the Discerning", ai, ZoneType.Hand);
        add("Craw Wurm", ai, ZoneType.Hand); refresh();
        SpellAbility chosen = selected();
        if (baseline) { check(chosen == null || chosen.getHostCard() != elenora, "baseline: blocked Syr Elenora is never cast"); return; }
        check(chosen != null && chosen.getHostCard() == elenora, "normal native AI selection casts Syr Elenora");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment casts Syr Elenora");
        resolveAll();
        elenora = game.getCardState(elenora);
        check(elenora.isInPlay() && hand(ai) == 2, "original enter trigger draws a card");
        check(elenora.getNetPower() == hand(ai), "power equals cards in hand");
        add("Craw Wurm", ai, ZoneType.Hand); refresh();
        check(elenora.getNetPower() == 3, "power follows hand size");
    }

    // ---- Marsh Flitter
    static Card flitter;
    static SpellAbility pump;
    static void flitterSetup(boolean tokens, PhaseType phase, int turn) {
        setup(PhaseType.MAIN2, 0);
        if (tokens) {
            add("Swamp", ai, ZoneType.Battlefield, 4);
            flitter = add("Marsh Flitter", ai, ZoneType.Hand); refresh();
            ComputerUtil.playStack(spell(flitter), ai, game); resolveAll(); flitter = game.getCardState(flitter);
        } else {
            flitter = add("Marsh Flitter", ai, ZoneType.Battlefield);
        }
        game.getPhaseHandler().devModeSet(phase, p(turn));
        pump = flitter.getSpellAbilities().stream().filter(s -> s.getApi() == ApiType.Animate).findFirst().orElseThrow();
        pump.setActivatingPlayer(ai);
        refresh();
    }
    static int goblinTokens() { return CardLists.count(ai.getCreaturesInPlay(), c -> c.isToken() && c.getType().hasCreatureType("Goblin")); }
    static Combat ourAttack(Card blocker) {
        Combat combat = new Combat(ai); game.getPhaseHandler().setCombat(combat); combat.addAttacker(flitter, p(1));
        if (blocker != null) { combat.addBlocker(flitter, blocker); }
        refresh(); return combat;
    }
    static void marshFlitter(boolean baseline) {
        if (baseline) {
            setup(PhaseType.MAIN2, 0); add("Swamp", ai, ZoneType.Battlefield, 4); Card f = add("Marsh Flitter", ai, ZoneType.Hand); refresh();
            SpellAbility chosen = selected();
            check(chosen == null || chosen.getHostCard() != f, "baseline: blocked Marsh Flitter is never cast");
            return;
        }
        setup(PhaseType.MAIN2, 0); add("Swamp", ai, ZoneType.Battlefield, 4); Card f = add("Marsh Flitter", ai, ZoneType.Hand); refresh();
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == f, "normal native AI selection casts Marsh Flitter");
        flitterSetup(true, PhaseType.COMBAT_DECLARE_BLOCKERS, 0);
        check(goblinTokens() == 2, "original enter trigger creates two Goblin Rogue tokens");
        Card piker = add("Goblin Piker", ai, ZoneType.Battlefield);
        ourAttack(null);
        check(willing(pump), "unblocked attacking Flitter pumps (3 damage instead of 1)");
        chosen = selected();
        check(chosen != null && chosen.getHostCard() == flitter, "normal native AI selection activates the pump after blocks");
        check(ComputerUtil.playStack(pump, ai, game) && goblinTokens() == 1 && piker.isInPlay(), "native cost sacrifices a Goblin token, not the Goblin card");
        check(!willing(pump), "pump already on the stack: no second sacrifice");
        resolveAll();
        check(flitter.getNetPower() == 3 && flitter.getNetToughness() == 3, "original effect makes Flitter 3/3");
        check(!willing(pump), "already 3/3: no second sacrifice this turn");

        flitterSetup(true, PhaseType.COMBAT_DECLARE_BLOCKERS, 0); ourAttack(add("Grizzly Bears", p(1), ZoneType.Battlefield));
        check(willing(pump), "blocked by 2/2: 3/3 kills it and survives");
        flitterSetup(true, PhaseType.COMBAT_DECLARE_BLOCKERS, 0); ourAttack(add("Craw Wurm", p(1), ZoneType.Battlefield));
        check(!willing(pump), "blocked by 6/4: 3/3 neither kills nor survives, holds");
        flitterSetup(true, PhaseType.COMBAT_DECLARE_BLOCKERS, 1);
        Card bears = add("Grizzly Bears", p(1), ZoneType.Battlefield);
        Combat theirs = new Combat(p(1)); game.getPhaseHandler().setCombat(theirs); theirs.addAttacker(bears, ai); theirs.addBlocker(bears, flitter); refresh();
        check(willing(pump), "blocking a 2/2: 3/3 kills it and survives");
        flitterSetup(true, PhaseType.COMBAT_DECLARE_ATTACKERS, 0); ourAttack(null);
        check(!willing(pump), "before blocks holds");
        flitterSetup(true, PhaseType.MAIN2, 0);
        check(!willing(pump), "outside combat holds");
        flitterSetup(false, PhaseType.COMBAT_DECLARE_BLOCKERS, 0); add("Goblin Piker", ai, ZoneType.Battlefield); ourAttack(null);
        check(!willing(pump), "only a nontoken Goblin available: holds");
    }

    // ---- Recurring Insight
    static Card insight;
    static void insightSetup(int library) {
        setup(PhaseType.MAIN2, 0);
        add("Island", ai, ZoneType.Battlefield, 6);
        add("Grizzly Bears", ai, ZoneType.Library, library);
        insight = add("Recurring Insight", ai, ZoneType.Hand);
        refresh();
    }
    static void recurringInsight(boolean baseline) {
        insightSetup(20); add("Forest", p(1), ZoneType.Hand, 4); add("Forest", p(2), ZoneType.Hand, 2); refresh();
        SpellAbility sa = spell(insight);
        if (baseline) { SpellAbility chosen = selected(); check(chosen == null || chosen.getHostCard() != insight, "baseline: blocked Recurring Insight is never cast"); return; }
        check(willing(sa) && sa.getTargets().getFirstTargetedPlayer() == p(1), "targets the opponent with the most cards (4)");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == insight, "normal native AI selection casts Recurring Insight");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment casts it");
        resolveAll();
        check(hand(ai) == 4 && game.getCardState(insight).isInZone(ZoneType.Exile), "original effect draws 4 and rebound exiles it");
        insightSetup(20); add("Forest", p(3), ZoneType.Hand, 1); game.getAction().exile(insight, null, null); refresh();
        Card fromExile = game.getCardState(insight);
        SpellAbility rebound = spell(fromExile);
        check(util("decide", ai, rebound) != null && willing(rebound) && rebound.getTargets().getFirstTargetedPlayer() == p(3), "rebound cast from exile accepts a 1-card draw");
        insightSetup(20); add("Forest", p(1), ZoneType.Hand, 2); refresh();
        check(!willing(spell(insight)), "hand cast with best opponent at 2 cards holds");
        insightSetup(8); add("Forest", p(1), ZoneType.Hand, 4); refresh();
        check(!willing(spell(insight)), "library not above X+5 holds");
        insightSetup(20); add("Forest", p(1), ZoneType.Hand, 4); game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p(1)); refresh();
        check(!willing(spell(insight)), "opponent's turn holds for a hand cast");
    }

    // ---- Pact of the Serpent
    static Card pact;
    static void pactSetup(int bears, int goblins, int life, int library) {
        setup(PhaseType.MAIN2, 0);
        add("Swamp", ai, ZoneType.Battlefield, 3);
        add("Grizzly Bears", ai, ZoneType.Battlefield, bears);
        add("Goblin Piker", ai, ZoneType.Battlefield, goblins);
        add("Grizzly Bears", ai, ZoneType.Library, library);
        ai.setLife(life, null);
        pact = add("Pact of the Serpent", ai, ZoneType.Hand);
        refresh();
    }
    static void pactOfTheSerpent(boolean baseline) {
        pactSetup(3, 1, 20, 20);
        SpellAbility sa = spell(pact);
        if (baseline) { SpellAbility chosen = selected(); check(chosen == null || chosen.getHostCard() != pact, "baseline: blocked Pact of the Serpent is never cast"); return; }
        check(willing(sa) && sa.getSubAbility().getTargets().getFirstTargetedPlayer() == ai, "targets ourselves with three Bears");
        check("Bear".equals(util("pactType", ai)), "most common creature type is Bear");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == pact, "normal native AI selection casts Pact");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment casts it");
        resolveAll();
        check(hand(ai) == 3 && ai.getLife() == 17, "original effect with our chosen type: draw 3, lose 3");
        pactSetup(1, 1, 20, 20); check(!willing(spell(pact)), "X = 1 holds");
        pactSetup(3, 0, 12, 20); check(!willing(spell(pact)), "would drop below 10 life: holds");
        pactSetup(3, 0, 20, 5); check(!willing(spell(pact)), "library not above X+3 holds");
        pactSetup(2, 2, 20, 20); check("Bear".equals(util("pactType", ai)), "ties break alphabetically without RNG (Bear before Goblin)");
        pactSetup(3, 0, 20, 20); add("Grizzly Bears", p(1), ZoneType.Battlefield, 5); refresh();
        SpellAbility s2 = spell(pact); check(willing(s2) && s2.getSubAbility().getTargets().getFirstTargetedPlayer() == ai, "never targets an opponent, even one with more Bears");
        pactSetup(3, 0, 20, 20); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN, ai); refresh();
        check(!willing(spell(pact)), "outside our main phase holds");
    }

    // ---- Suffer the Past
    static Card suffer;
    static void sufferSetup(PhaseType phase, int turn, int swamps) {
        setup(phase, turn);
        add("Swamp", ai, ZoneType.Battlefield, swamps);
        suffer = add("Suffer the Past", ai, ZoneType.Hand);
        refresh();
    }
    static void sufferThePast(boolean baseline) {
        sufferSetup(PhaseType.END_OF_TURN, 3, 5);
        add("Island", p(1), ZoneType.Graveyard, 3); Card wurm = add("Craw Wurm", p(1), ZoneType.Graveyard); Card bears = add("Grizzly Bears", p(1), ZoneType.Graveyard);
        add("Island", p(1), ZoneType.Graveyard); add("Swamp", p(2), ZoneType.Graveyard, 2); refresh();
        SpellAbility sa = spell(suffer);
        if (baseline) { SpellAbility chosen = selected(); check(chosen == null || chosen.getHostCard() != suffer, "baseline: blocked Suffer the Past is never cast"); return; }
        check(willing(sa) && sa.getTargets().getFirstTargetedPlayer() == p(1) && sa.getXManaCostPaid() == 4, "end step before our turn: X = 4 against the biggest graveyard");
        List<Card> targets = new ArrayList<>(sa.getSubAbility().getTargets().getTargetCards());
        check(targets.size() == 4 && targets.get(0) == wurm && targets.get(1) == bears, "creatures exiled first, best first");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == suffer, "normal native AI selection casts Suffer the Past");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment pays X = 4 plus B");
        resolveAll();
        check(p(1).getCardsIn(ZoneType.Graveyard).size() == 2 && p(1).getLife() == 16 && ai.getLife() == 24, "original effect exiles 4, drains 4");
        sufferSetup(PhaseType.MAIN2, 0, 5); add("Island", p(1), ZoneType.Graveyard, 6); refresh();
        check(!willing(spell(suffer)), "non-lethal in our own main phase holds");
        sufferSetup(PhaseType.MAIN2, 0, 5); add("Island", p(2), ZoneType.Graveyard, 3); p(2).setLife(2, null); add("Island", p(1), ZoneType.Graveyard, 6); refresh();
        SpellAbility lethal = spell(suffer);
        check(willing(lethal) && lethal.getTargets().getFirstTargetedPlayer() == p(2), "lethal any time beats the bigger graveyard");
        sufferSetup(PhaseType.END_OF_TURN, 3, 5); add("Island", p(1), ZoneType.Graveyard, 2); refresh();
        check(!willing(spell(suffer)), "X below 3 in the window holds");
        sufferSetup(PhaseType.END_OF_TURN, 3, 5); add("Island", ai, ZoneType.Graveyard, 8); refresh();
        check(!willing(spell(suffer)), "never targets our own graveyard");
        sufferSetup(PhaseType.END_OF_TURN, 1, 5); add("Island", p(1), ZoneType.Graveyard, 6); refresh();
        check(!willing(spell(suffer)), "earlier opponent's end step holds");
    }

    static void boundaries() {
        for (String profile : List.of("", "abzan-armor")) {
            AiOverrideProfile.setActive(profile);
            insightSetup(20); add("Forest", p(1), ZoneType.Hand, 4); refresh();
            check(util("decide", ai, spell(insight)) == null, "Recurring Insight rule inactive for profile '" + profile + "'");
            pactSetup(3, 0, 20, 20);
            check(util("decide", ai, spell(pact)) == null && ComputerUtilCard.isCardRemAIDeck(pact), "Pact rule inactive, AI hint retained, profile '" + profile + "'");
            setup(PhaseType.MAIN2, 0); add("Island", ai, ZoneType.Battlefield, 5); Card e = add("Syr Elenora, the Discerning", ai, ZoneType.Hand); refresh();
            SpellAbility chosen = selected();
            check(chosen == null || chosen.getHostCard() != e, "Syr Elenora stays blocked for profile '" + profile + "'");
        }
        AiOverrideProfile.setActive("easy-cards");
    }

    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(20261019));
        boolean baseline = args.length > 0 && args[0].equals("baseline");
        AiOverrideProfile.setActive("easy-cards");
        elenora(baseline);
        marshFlitter(baseline);
        recurringInsight(baseline);
        pactOfTheSerpent(baseline);
        sufferThePast(baseline);
        if (!baseline) { boundaries(); }
        System.out.println("DONE " + passed + " checks");
    }
}
