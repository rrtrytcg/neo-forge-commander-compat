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
public class EasyCards3Tests {
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
            LobbyPlayerAi lobby = new LobbyPlayerAi("Easy cards 3 fixture " + i, null);
            lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Easy cards 3 native fixtures"));
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
            for (var m : Class.forName("forge.ai.EasyCards3Ai").getMethods()) {
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

    static Card token(String name, Player owner) {
        Card c = add(name, owner, ZoneType.Battlefield);
        c.setGamePieceType(forge.card.GamePieceType.TOKEN);
        return c;
    }

    // ---- The Prismatic Piper
    static void prismaticPiper(boolean baseline) {
        setup(PhaseType.MAIN2, 0);
        add("Island", ai, ZoneType.Battlefield, 5);
        Card piper = add("The Prismatic Piper", ai, ZoneType.Hand); refresh();
        if (baseline) { notSelected(piper, "baseline: blocked The Prismatic Piper is never cast"); return; }
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == piper, "normal native AI selection casts The Prismatic Piper");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment casts it");
        resolveAll();
        piper = game.getCardState(piper);
        check(piper.isInPlay() && piper.getNetPower() == 3 && piper.getNetToughness() == 3, "original card enters as a 3/3");
    }

    // ---- Cave of Temptation
    static Card caveSetup(int lands, boolean landInHand, boolean creature, int turn) {
        setup(PhaseType.MAIN2, turn);
        add("Forest", ai, ZoneType.Battlefield, lands - 1);
        Card cave = add("Cave of Temptation", ai, ZoneType.Battlefield);
        if (creature) { add("Craw Wurm", ai, ZoneType.Battlefield); }
        if (landInHand) { add("Forest", ai, ZoneType.Hand); }
        refresh();
        return cave;
    }
    static void caveOfTemptation(boolean baseline) {
        Card cave = caveSetup(8, false, true, 0);
        SpellAbility sa = ability(cave, ApiType.PutCounter);
        if (baseline) { notSelected(cave, "baseline: blocked Cave of Temptation never activates"); return; }
        Card wurm = ai.getCreaturesInPlay().getFirst();
        check(willing(sa) && sa.getTargets().getFirstTargetedCard() == wurm, "8 lands, no land in hand: targets our best creature");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == cave, "normal native AI selection activates the Cave");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment: four mana, tap and sacrifice the Cave");
        resolveAll();
        check(wurm.getCounters(CounterEnumType.P1P1) == 2 && game.getCardState(cave).isInZone(ZoneType.Graveyard), "original effect: two counters, Cave sacrificed");
        check(!willing(ability(caveSetup(7, false, true, 0), ApiType.PutCounter)), "seven lands: holds");
        check(!willing(ability(caveSetup(8, true, true, 0), ApiType.PutCounter)), "a land in hand: holds");
        check(!willing(ability(caveSetup(8, false, false, 0), ApiType.PutCounter)), "no creature: holds");
        check(!willing(ability(caveSetup(8, false, true, 1), ApiType.PutCounter)), "opponent's turn (sorcery speed): holds");
    }

    // ---- Throes of Chaos
    static Card throesSetup(PhaseType phase, int turn, int lands, boolean landInHand, ZoneType zone) {
        setup(phase, turn);
        add("Mountain", ai, ZoneType.Battlefield, lands);
        add("Grizzly Bears", ai, ZoneType.Library, 10);
        Card throes = add("Throes of Chaos", ai, zone);
        if (landInHand) { add("Forest", ai, ZoneType.Hand); }
        refresh();
        return throes;
    }
    static void throesOfChaos(boolean baseline) {
        Card throes = throesSetup(PhaseType.MAIN1, 0, 4, false, ZoneType.Hand);
        if (baseline) { notSelected(throes, "baseline: blocked Throes of Chaos is never cast"); return; }
        check(willing(spell(throes)), "first main: casts");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == throes, "normal native AI selection casts Throes of Chaos");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment casts it");
        resolveAll();
        check(ai.getCreaturesInPlay().stream().anyMatch(c -> c.getName().equals("Grizzly Bears")), "original cascade puts the free spell onto the battlefield");
        check(!willing(spell(throesSetup(PhaseType.MAIN2, 0, 4, false, ZoneType.Hand))), "second main holds");
        check(!willing(spell(throesSetup(PhaseType.MAIN1, 1, 4, false, ZoneType.Hand))), "opponent's turn holds");
        check(willing(spell(throesSetup(PhaseType.MAIN2, 0, 6, true, ZoneType.Graveyard))), "retrace: six lands and a land in hand");
        Card retr = throesSetup(PhaseType.MAIN2, 0, 6, true, ZoneType.Graveyard);
        ai.setLandsPlayedThisTurn(1);
        SpellAbility pick = selected();
        check(pick != null && pick.getHostCard() == retr, "normal native AI selection retraces Throes from the graveyard once the land drop is used");
        check(ComputerUtil.playStack(pick, ai, game), "native payment: four mana and a discarded land");
        resolveAll();
        check(hand(ai) == 0 && inGraveyard(ai, "Forest") && inGraveyard(ai, "Throes of Chaos") && ai.getCreaturesInPlay().size() == 1,
                "original retrace: the Forest is discarded, cascade resolves, Throes returns to the graveyard");
        check(!willing(spell(throesSetup(PhaseType.MAIN2, 0, 5, true, ZoneType.Graveyard))), "retrace holds below six lands");
        check(!willing(spell(throesSetup(PhaseType.MAIN2, 0, 6, false, ZoneType.Graveyard))), "retrace holds with no land to discard");
    }

    // ---- Masterful Replication
    static Card replicationSetup(PhaseType phase, int turn) {
        setup(phase, turn);
        add("Island", ai, ZoneType.Battlefield, 6);
        add("Sol Ring", ai, ZoneType.Battlefield);
        Card rep = add("Masterful Replication", ai, ZoneType.Hand); refresh();
        return rep;
    }
    static void masterfulReplication(boolean baseline) {
        Card rep = replicationSetup(PhaseType.END_OF_TURN, 1);
        if (baseline) { notSelected(rep, "baseline: blocked Masterful Replication is never cast"); return; }
        SpellAbility sa = spell(rep);
        check(willing(sa) && sa.getChosenList().size() == 1 && sa.getChosenList().get(0).getApi() == ApiType.Token, "opponent's end step: picks only the Golem mode");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == rep, "normal native AI selection casts it");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment");
        resolveAll();
        check(ai.getCreaturesInPlay().size() == 2 && ai.getCreaturesInPlay().stream().allMatch(c -> c.isToken() && c.getNetPower() == 3 && c.isArtifact()),
                "original effect: two 3/3 artifact Golem tokens");
        check(!willing(spell(replicationSetup(PhaseType.END_OF_TURN, 0))), "our own end step holds");
        check(!willing(spell(replicationSetup(PhaseType.MAIN2, 1))), "outside the end step holds");
    }

    // ---- Call for Aid
    static Card aidSetup(PhaseType phase, int victimWurms, int p2Life, int p2Blockers) {
        setup(phase, 0);
        add("Mountain", ai, ZoneType.Battlefield, 5);
        Card bears = add("Grizzly Bears", ai, ZoneType.Battlefield); bears.setSickness(false);
        add("Craw Wurm", p(1), ZoneType.Battlefield, victimWurms);
        add("Grizzly Bears", p(2), ZoneType.Battlefield, p2Blockers);
        p(2).setLife(p2Life, null);
        Card aid = add("Call for Aid", ai, ZoneType.Hand); refresh();
        return aid;
    }
    static void callForAid(boolean baseline) {
        Card aid = aidSetup(PhaseType.MAIN1, 2, 10, 0);
        if (baseline) { notSelected(aid, "baseline: blocked Call for Aid is never cast"); return; }
        Player victim = p(1);
        SpellAbility sa = spell(aid);
        check(willing(sa) && sa.getTargets().getFirstTargetedPlayer() == victim, "14 attacking power is lethal on the 10-life opponent: borrows from the other opponent");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == aid, "normal native AI selection casts it");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment");
        resolveAll();
        check(victim.getCreaturesInPlay().isEmpty() && ai.getCreaturesInPlay().stream().filter(c -> c.getName().equals("Craw Wurm")).count() == 2
                && ai.getCreaturesInPlay().stream().allMatch(c -> c.hasKeyword("Haste") || c.getName().equals("Grizzly Bears")), "original effect: both Craw Wurms borrowed with haste");
        check(!willing(spell(aidSetup(PhaseType.MAIN1, 2, 20, 0))), "not lethal on anyone: holds");
        check(!willing(spell(aidSetup(PhaseType.MAIN1, 2, 10, 2))), "two blockers absorb the biggest attackers: holds");
        check(!willing(spell(aidSetup(PhaseType.MAIN2, 2, 10, 0))), "second main holds");
        check(!willing(spell(aidSetup(PhaseType.MAIN1, 0, 10, 0))), "nothing to borrow and 2 power against 10 life: holds");
        Card own = aidSetup(PhaseType.MAIN1, 2, 10, 0);
        for (int i = 0; i < 4; i++) { Card extra = add("Craw Wurm", ai, ZoneType.Battlefield); extra.setSickness(false); }
        refresh();
        check(!willing(spell(own)), "our own creatures are already lethal: borrowing is not needed, holds");
    }

    // ---- Marauding Raptor
    static Card raptorSetup(boolean raptorInPlay) {
        setup(PhaseType.MAIN2, 0);
        add("Mountain", ai, ZoneType.Battlefield, 4);
        Card raptor = add("Marauding Raptor", ai, raptorInPlay ? ZoneType.Battlefield : ZoneType.Hand);
        refresh();
        return raptor;
    }
    static void maraudingRaptor(boolean baseline) {
        Card raptor = raptorSetup(false);
        if (baseline) { notSelected(raptor, "baseline: blocked Marauding Raptor is never cast"); return; }
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == raptor, "normal native AI selection casts Marauding Raptor");
        raptor = raptorSetup(true);
        Card bears = add("Grizzly Bears", ai, ZoneType.Hand);
        Card giant = add("Hill Giant", ai, ZoneType.Hand); refresh();
        check(!willing(spell(bears)), "with the Raptor in play, a 2/2 is held");
        check(willing(spell(giant)), "a 3/3 is still allowed");
        check(ComputerUtil.playStack(spell(giant), ai, game), "native payment casts the Hill Giant");
        resolveAll();
        Card inPlay = game.getCardState(giant);
        check(inPlay.isInPlay() && inPlay.getDamage() == 2 && game.getCardState(raptor).getNetPower() == 2, "original trigger: the Giant takes 2 and survives (Dinosaur pump needs a Dinosaur)");
        raptor = raptorSetup(false);
        bears = add("Grizzly Bears", ai, ZoneType.Hand); refresh();
        check(willing(spell(bears)), "with no Raptor in play a 2/2 is not held");
    }

    // ---- God-Eternal Bontu
    static Card bontuSetup(int lands, int tokens) {
        setup(PhaseType.MAIN2, 0);
        add("Swamp", ai, ZoneType.Battlefield, lands);
        for (int i = 0; i < tokens; i++) { token("Goblin Piker", ai); }
        add("Hill Giant", ai, ZoneType.Battlefield);
        add("Grizzly Bears", ai, ZoneType.Library, 10);
        Card bontu = add("God-Eternal Bontu", ai, ZoneType.Hand); refresh();
        return bontu;
    }
    static void godEternalBontu(boolean baseline) {
        Card bontu = bontuSetup(9, 2);
        if (baseline) { notSelected(bontu, "baseline: blocked God-Eternal Bontu is never cast"); return; }
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == bontu, "normal native AI selection casts Bontu");
        check(ComputerUtil.playStack(chosen, ai, game), "native payment");
        resolveAll();
        check(game.getCardState(bontu).isInPlay() && hand(ai) == 3 && ai.getCreaturesInPlay().stream().noneMatch(Card::isToken) && ai.getLandsInPlay().size() == 8
                && ai.getCreaturesInPlay().stream().anyMatch(c -> c.getName().equals("Hill Giant")),
                "original trigger: two tokens and one spare land sacrificed for three cards; the Giant kept");
        Card inPlay = game.getCardState(bontu);
        inPlay.setDamage(20);
        game.getAction().checkStateEffects(true);
        resolveAll();
        check(ai.getCardsIn(ZoneType.Library).size() > 2 && ai.getCardsIn(ZoneType.Library).get(2).getName().equals("God-Eternal Bontu"), "death trigger: Bontu goes third from the top");
        bontu = bontuSetup(7, 0);
        check(ComputerUtil.playStack(spell(bontu), ai, game), "native payment with nothing to give up");
        resolveAll();
        check(hand(ai) == 0 && ai.getLandsInPlay().size() == 7 && game.getCardState(bontu).isInPlay(), "nothing worth sacrificing: no sacrifice, no draw");
    }

    // ---- Esix, Fractal Bloom
    static Card esixSetup(String other) {
        setup(PhaseType.MAIN2, 0);
        add("Forest", ai, ZoneType.Battlefield, 3);
        add("Island", ai, ZoneType.Battlefield, 3);
        add("Plains", ai, ZoneType.Battlefield, 2);
        Card esix = add("Esix, Fractal Bloom", ai, ZoneType.Battlefield);
        if (other != null) { add(other, ai, ZoneType.Battlefield); }
        Card alarm = add("Raise the Alarm", ai, ZoneType.Hand);
        refresh();
        return alarm;
    }
    static long creatures(String name) { return ai.getCreaturesInPlay().stream().filter(c -> c.getName().equals(name)).count(); }
    static void esixFractalBloom(boolean baseline) {
        setup(PhaseType.MAIN2, 0);
        add("Forest", ai, ZoneType.Battlefield, 3); add("Island", ai, ZoneType.Battlefield, 3);
        Card cast = add("Esix, Fractal Bloom", ai, ZoneType.Hand); refresh();
        if (baseline) { notSelected(cast, "baseline: blocked Esix is never cast"); return; }
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == cast, "normal native AI selection casts Esix");
        Card alarm = esixSetup("Craw Wurm");
        check(ComputerUtil.playStack(spell(alarm), ai, game), "native payment of a token-making spell");
        resolveAll();
        check(creatures("Craw Wurm") == 3 && creatures("Soldier Token") == 0 && ai.getCreaturesInPlay().stream().filter(Card::isToken).count() == 2,
                "original replacement: two token copies of our Craw Wurm instead of two Soldiers");
        alarm = esixSetup("Grizzly Bears");
        ai.getCreaturesInPlay().stream().filter(c -> c.getName().equals("Grizzly Bears")).findFirst().orElseThrow().setTapped(true);
        for (int i = 0; i < 3; i++) { token("Goblin Piker", ai); }
        refresh();
        check(ComputerUtil.playStack(spell(alarm), ai, game), "native payment with a tapped Bears and three small tokens around");
        resolveAll();
        check(creatures("Grizzly Bears") == 3 && creatures("Soldier Token") == 0, "the best creature is copied even when it is tapped and surrounded by tokens");
        alarm = esixSetup(null);
        check(ComputerUtil.playStack(spell(alarm), ai, game), "native payment with nothing worth copying");
        resolveAll();
        check(ai.getCreaturesInPlay().stream().filter(Card::isToken).count() == 2 && creatures("Esix, Fractal Bloom") == 1 && ai.getCreaturesInPlay().stream().noneMatch(c -> c.getName().equals("Esix, Fractal Bloom") && c.isToken()),
                "no creature worth copying: declines, the Soldier tokens are made");
        alarm = esixSetup("Isamaru, Hound of Konda");
        check(ComputerUtil.playStack(spell(alarm), ai, game), "native payment with only a legendary creature");
        resolveAll();
        check(creatures("Isamaru, Hound of Konda") == 1, "a legendary creature is never copied");
    }

    static void boundaries() {
        for (String profile : List.of("", "abzan-armor")) {
            AiOverrideProfile.setActive(profile);
            Card cave = caveSetup(8, false, true, 0);
            check(util("decide", ai, ability(cave, ApiType.PutCounter)) == null, "Cave rule inactive for profile '" + profile + "'");
            Card aid = aidSetup(PhaseType.MAIN1, 2, 10, 0);
            check(util("decide", ai, spell(aid)) == null, "Call for Aid rule inactive for profile '" + profile + "'");
            Card bears = raptorSetup(true);
            bears = add("Grizzly Bears", ai, ZoneType.Hand); refresh();
            check(util("decide", ai, spell(bears)) == null, "Raptor veto inactive for profile '" + profile + "'");
            Card throes = throesSetup(PhaseType.MAIN1, 0, 4, false, ZoneType.Hand);
            SpellAbility chosen = selected();
            check(chosen == null || (chosen.getHostCard() != throes), "blocked cards stay blocked for profile '" + profile + "'");
        }
        AiOverrideProfile.setActive("easy-cards-3");
    }

    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(20261021));
        boolean baseline = args.length > 0 && args[0].equals("baseline");
        AiOverrideProfile.setActive("easy-cards-3");
        prismaticPiper(baseline);
        caveOfTemptation(baseline);
        throesOfChaos(baseline);
        masterfulReplication(baseline);
        callForAid(baseline);
        maraudingRaptor(baseline);
        godEternalBontu(baseline);
        esixFractalBloom(baseline);
        if (!baseline) { boundaries(); }
        System.out.println("DONE " + passed + " checks");
    }
}
