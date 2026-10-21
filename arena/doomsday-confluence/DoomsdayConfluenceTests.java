import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Native XXB payment, repeated mode chains, sacrifice/discard consequences and public-state rechecks. */
public class DoomsdayConfluenceTests {
    static Game game;
    static Player ai;
    static Card confluence;
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
    static void add(String name, Player owner, ZoneType zone, int n) {
        for (int i = 0; i < n; i++) { add(name, owner, zone); }
    }
    static void refresh() {
        game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
    }
    static void setup(int lands) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Confluence fixture " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Doomsday Confluence fixtures")); game.setAge(GameStage.Play);
        ai = p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        add("Swamp", ai, ZoneType.Battlefield, lands);
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library, 20); }
        confluence = add("Doomsday Confluence", ai, ZoneType.Hand); spell = confluence.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai, spell).willingToPlay(); }
    static long modes(ApiType api) { return spell.getChosenList() == null ? 0 : spell.getChosenList().stream().filter(s -> s.getApi() == api).count(); }
    static void expect(int x, int sacrifice, int discard, String label) {
        check(willing() && spell.getXManaCostPaid() == x && modes(ApiType.Sacrifice) == sacrifice
                && modes(ApiType.Discard) == discard && modes(ApiType.Token) == x - sacrifice - discard, label);
        int n = 0; for (SpellAbility part = spell.getSubAbility(); part != null; part = part.getSubAbility()) {
            n++; if (n > 100) { throw new AssertionError("cyclic chain"); }
        }
        check(n == x && spell.getChosenList().size() == x, label + ": exactly X native chained modes");
    }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    static void resolveAll() {
        for (int i = 0; i < 24; i++) {
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) { break; }
            game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        }
    }
    static long daleks() { return ai.getCreaturesInPlay().stream().filter(c -> c.isToken() && c.getType().hasSubtype("Dalek")).count(); }
    static void cast(boolean normal) {
        check(normal ? ComputerUtil.handlePlayingSpellAbility(ai, spell, null) : ComputerUtil.playStack(spell, ai, game), "native paid cast through " + (normal ? "normal execution" : "playStack"));
        resolveAll(); confluence = game.getCardState(confluence);
    }
    static void baseline() {
        setup(9); SpellAbility chosen = selected();
        check(chosen == null || chosen.getHostCard() != confluence, "baseline: RemoveDeck blocks normal casting");
        spell.setXManaCostPaid(4);
        check(ComputerUtilCost.canPayCost(spell, ai, false), "baseline: native XXB payment can afford X four with nine Swamps");
        check(!willing(), "baseline: generic Charm cannot construct four repeated selections");
    }
    static void xAndTiming() {
        for (int lands = 3; lands <= 11; lands++) {
            setup(lands); expect((lands - 1) / 2, 0, 0, "largest affordable XXB value with " + lands + " Swamps");
        }
        setup(9); SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == confluence && chosen.getXManaCostPaid() == 4, "normal native AI selects X four");
        setup(2); check(!willing() && spell.getXManaCostPaid() == 0 && spell.getChosenList() == null && spell.getSubAbility() == null, "X zero holds with all stale choices cleared");
        setup(0); add("Mountain", ai, ZoneType.Battlefield, 9); refresh(); check(!willing(), "nine red mana cannot supply black");
        setup(0); add("Sol Ring", ai, ZoneType.Battlefield, 4); add("Swamp", ai, ZoneType.Battlefield); refresh(); expect(4, 0, 0, "native artifact mana pays both X symbols and black");
        setup(7); add("Sphere of Resistance", p(1), ZoneType.Battlefield); refresh(); expect(2, 0, 0, "native spell tax reduces affordable X");
        for (PhaseType phase : List.of(PhaseType.MAIN1, PhaseType.COMBAT_BEGIN, PhaseType.COMBAT_DECLARE_BLOCKERS, PhaseType.END_OF_TURN)) {
            setup(9); game.getPhaseHandler().devModeSet(phase, ai); check(!willing(), "holds in own phase " + phase);
        }
        setup(9); game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p(1)); check(!willing(), "opponent second main holds");
        setup(9); game.getStack().add(spell.copy(ai)); check(!willing(), "nonempty stack holds");
        setup(9); spell.getMapParams().put("CanRepeatModes", "False"); check(!willing(), "modified card shape holds");
        setup(9); spell.setXManaCostPaid(99); expect(4, 0, 0, "stale X ninety-nine is recalculated");
    }
    static void choiceRules() {
        setup(9); add("Grizzly Bears", p(1), ZoneType.Battlefield); refresh(); expect(4, 1, 0, "safe single sacrifice, all remaining slots Daleks");
        setup(9); add("Ornithopter", p(1), ZoneType.Battlefield); refresh(); expect(4, 0, 0, "opposing artifact creature does not qualify");
        setup(9); add("Grizzly Bears", p(1), ZoneType.Battlefield); add("Grizzly Bears", ai, ZoneType.Battlefield); refresh(); expect(4, 0, 0, "own nonartifact creature prevents sacrifice");
        setup(9); add("Grizzly Bears", p(1), ZoneType.Battlefield); add("Ornithopter", ai, ZoneType.Battlefield); refresh(); expect(4, 1, 0, "friendly artifact creature is safe from the edict");
        setup(9); ai.setTeam(0); p(1).setTeam(0); add("Grizzly Bears", p(1), ZoneType.Battlefield); add("Grizzly Bears", p(2), ZoneType.Battlefield); refresh(); expect(4, 0, 0, "allied nonartifact creature prevents sacrifice");
        setup(9); add("Tajuru Preserver", p(1), ZoneType.Battlefield); add("Grizzly Bears", p(1), ZoneType.Battlefield); refresh(); expect(4, 0, 0, "native sacrifice protection makes opponents ineligible");
        setup(9); add("Grizzly Bears", p(1), ZoneType.Battlefield).setPhasedOut(p(1)); refresh(); expect(4, 0, 0, "phased-out opposing creature is ineligible");
        setup(9); add("Forest", p(1), ZoneType.Hand); refresh(); expect(4, 0, 0, "one nonempty opposing hand does not qualify for discard");
        setup(9); add("Forest", p(1), ZoneType.Hand); add("Forest", p(2), ZoneType.Hand); refresh(); expect(4, 0, 1, "two nonempty opposing hands qualify for one discard");
        setup(9); add("Craw Wurm", p(1), ZoneType.Hand); add("Doom Blade", p(2), ZoneType.Hand); refresh(); expect(4, 0, 1, "hidden card contents do not change the public-count decision");
        setup(9); ai.setTeam(0); p(1).setTeam(0); add("Forest", p(1), ZoneType.Hand); add("Forest", p(2), ZoneType.Hand); refresh(); expect(4, 0, 0, "allied hand does not count as a second opponent");
        setup(9); for (int i = 1; i < 4; i++) { add("Grizzly Bears", p(i), ZoneType.Battlefield, 3); add("Forest", p(i), ZoneType.Hand, 5); } refresh();
        expect(4, 1, 1, "both useful modes at most once, then two Daleks");
        List<ApiType> order = new ArrayList<>(); for (SpellAbility part = spell.getSubAbility(); part != null; part = part.getSubAbility()) { order.add(part.getApi()); }
        check(order.equals(List.of(ApiType.Sacrifice, ApiType.Token, ApiType.Token, ApiType.Discard)), "native printed order preserved");
        for (int i = 0; i < 4; i++) { expect(4, 1, 1, "repeated evaluation is stable " + i); }
        setup(3); add("Grizzly Bears", p(1), ZoneType.Battlefield); add("Forest", p(1), ZoneType.Hand); add("Forest", p(2), ZoneType.Hand); refresh(); expect(1, 1, 0, "X one prioritizes safe sacrifice over discard");
        setup(3); add("Forest", p(1), ZoneType.Hand); add("Forest", p(2), ZoneType.Hand); refresh(); expect(1, 0, 1, "X one uses multiplayer discard when sacrifice is ineligible");
        setup(5); add("Grizzly Bears", p(1), ZoneType.Battlefield); add("Forest", p(1), ZoneType.Hand); add("Forest", p(2), ZoneType.Hand); refresh(); expect(2, 1, 1, "X two can use both useful modes with no token slot");
    }
    static void consequences() {
        for (boolean normal : List.of(false, true)) {
            setup(11); expect(5, 0, 0, "five native repeated token modes " + normal); cast(normal);
            check(daleks() == 5 && ai.getCreaturesInPlay().stream().allMatch(c -> c.isArtifact() && c.getNetPower() == 3 && c.getNetToughness() == 3 && c.hasKeyword(Keyword.MENACE)), "original token script makes five 3/3 artifact Daleks with menace " + normal);
            check(ai.getLandsInPlay().stream().filter(Card::isTapped).count() == 11 && confluence.isInZone(ZoneType.Graveyard), "native double-X payment spends eleven mana and spell finishes " + normal);
        }
        setup(9); Card artifact = add("Ornithopter", ai, ZoneType.Battlefield);
        for (int i = 1; i < 4; i++) { add("Grizzly Bears", p(i), ZoneType.Battlefield, 2); add("Forest", p(i), ZoneType.Hand, 2); }
        refresh(); expect(4, 1, 1, "mixed native resolution"); cast(true);
        check(artifact.isInPlay() && daleks() == 2, "friendly artifact survives and two original Daleks enter");
        for (int i = 1; i < 4; i++) {
            check(p(i).getCreaturesInPlay().size() == 1 && p(i).getCardsIn(ZoneType.Graveyard).stream().filter(c -> c.getName().equals("Grizzly Bears")).count() == 1, "opponent chooses exactly one native sacrifice " + i);
            check(p(i).getCardsIn(ZoneType.Hand).size() == 1, "opponent chooses exactly one native discard " + i);
        }
        setup(7); ai.setTeam(0); p(1).setTeam(0); Card ally = add("Grizzly Bears", p(1), ZoneType.Battlefield);
        add("Grizzly Bears", p(2), ZoneType.Battlefield); add("Forest", p(1), ZoneType.Hand, 2); add("Forest", p(2), ZoneType.Hand, 2); add("Forest", p(3), ZoneType.Hand, 2); refresh();
        expect(3, 0, 1, "allied creature blocks sacrifice while both opponents permit discard"); cast(false);
        check(ally.isInPlay() && p(1).getCardsIn(ZoneType.Hand).size() == 2 && p(2).getCardsIn(ZoneType.Hand).size() == 1 && p(3).getCardsIn(ZoneType.Hand).size() == 1 && daleks() == 2, "native team semantics protect allied creature and hand");
        setup(7); add("Sphere of Resistance", p(1), ZoneType.Battlefield); refresh(); cast(false);
        check(daleks() == 2 && ai.getLandsInPlay().stream().filter(Card::isTapped).count() == 6, "native taxed X two pays six mana and creates two Daleks");
    }
    static void freshState() {
        for (boolean normal : List.of(false, true)) {
            setup(9); expect(4, 0, 0, "plan before mana changes " + normal);
            for (int i = 0; i < 4; i++) { ai.getLandsInPlay().get(i).setTapped(true); }
            cast(normal); check(daleks() == 2, "fresh native payment lowers X to two " + normal);
            setup(7); add("Grizzly Bears", p(1), ZoneType.Battlefield); refresh(); expect(3, 1, 0, "plan before friendly creature appears " + normal);
            Card friendly = add("Grizzly Bears", ai, ZoneType.Battlefield); refresh(); cast(normal);
            check(friendly.isInPlay() && p(1).getCreaturesInPlay().size() == 1 && daleks() == 3, "fresh board replaces sacrifice with a Dalek " + normal);
            setup(7); add("Forest", p(1), ZoneType.Hand); add("Forest", p(2), ZoneType.Hand); refresh(); expect(3, 0, 1, "plan before opposing hand empties " + normal);
            p(2).getZone(ZoneType.Hand).remove(p(2).getCardsIn(ZoneType.Hand).getFirst()); refresh(); cast(normal);
            check(p(1).getCardsIn(ZoneType.Hand).size() == 1 && daleks() == 3, "fresh public counts replace discard with a Dalek " + normal);
            setup(3); expect(1, 0, 0, "plan before mana disappears " + normal); ai.getLandsInPlay().getFirst().setTapped(true);
            long tapped = ai.getLandsInPlay().stream().filter(Card::isTapped).count();
            boolean played = normal ? ComputerUtil.handlePlayingSpellAbility(ai, spell, null) : ComputerUtil.playStack(spell, ai, game);
            check(!played && game.getStack().isEmpty() && confluence.isInZone(ZoneType.Hand)
                    && ai.getLandsInPlay().stream().filter(Card::isTapped).count() == tapped
                    && spell.getChosenList() == null && spell.getSubAbility() == null, "unaffordable X cancels without costs and clears modes " + normal);
        }
        setup(9); expect(4, 0, 0, "new game has no prior X or modes");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        check(!ComputerUtil.handlePlayingSpellAbility(ai, spell, null) && ai.getLandsInPlay().stream().noneMatch(Card::isTapped), "fresh timing recheck cancels without payment");
    }
    static void isolation() {
        for (String profile : List.of("", "moonlight-bargain", "jolrael")) {
            AiOverrideProfile.setActive(profile); setup(9); SpellAbility choice = selected();
            check(choice == null || choice.getHostCard() != confluence, "blocked card remains unselected for profile '" + profile + "'");
            spell.setXManaCostPaid(4); check(!willing(), "native repeated-mode defect unchanged for profile '" + profile + "'");
        }
        AiOverrideProfile.setActive("doomsday-confluence"); setup(9); expect(4, 0, 0, "opt-in restored after unrelated profiles");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
        boolean baseline = args.length > 0 && args[0].equals("baseline"); AiOverrideProfile.setActive(baseline ? "" : "doomsday-confluence");
        if (baseline) { baseline(); } else { xAndTiming(); choiceRules(); consequences(); freshState(); isolation(); }
        AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Doomsday Confluence checks");
    }
}
