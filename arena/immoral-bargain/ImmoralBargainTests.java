import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.card.GamePieceType;
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

/** Native Immoral Bargain sacrifice budgets, target selection, payment and destruction. */
public class ImmoralBargainTests {
    static Game game;
    static Player ai;
    static Card bargain;
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
    static Card creature(String name, Player owner, boolean token) {
        Card c = add(name, owner, ZoneType.Battlefield); if (token) { c.setGamePieceType(GamePieceType.TOKEN); } return c;
    }
    static void refresh() {
        game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
    }
    static void setup() {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Immoral Bargain fixture " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Immoral Bargain native fixtures")); game.setAge(GameStage.Play);
        ai = p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        add("Swamp", ai, ZoneType.Battlefield, 3); add("Forest", ai, ZoneType.Battlefield, 3);
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library, 20); }
        bargain = add("Immoral Bargain", ai, ZoneType.Hand); spell = bargain.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        creature("Walking Corpse", ai, false); creature("Serra Angel", ai, false);
        creature("Grizzly Bears", ai, true);
        add("Sol Ring", p(1), ZoneType.Battlefield); add("Grizzly Bears", p(1), ZoneType.Battlefield);
        add("Serra Angel", p(2), ZoneType.Battlefield); add("Walking Corpse", p(3), ZoneType.Battlefield);
        refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai, spell).willingToPlay(); }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    static int x() { return spell.getXManaCostPaid() == null ? -1 : spell.getXManaCostPaid(); }
    static List<Card> targets() { return new ArrayList<>(spell.getTargets().getTargetCards()); }
    static void resolveAll() {
        for (int i = 0; i < 12; i++) {
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) { break; }
            game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        }
    }
    static void cast(boolean normal) {
        check(normal ? ComputerUtil.handlePlayingSpellAbility(ai, spell, null) : ComputerUtil.playStack(spell, ai, game),
                "native paid cast through " + (normal ? "normal execution" : "playStack"));
        resolveAll();
    }
    static long graveyardCount(Player player, String name) {
        return player.getCardsIn(ZoneType.Graveyard).stream().filter(c -> c.getName().equals(name)).count();
    }

    static void baseline() {
        setup(); AiOverrideProfile.setActive("");
        SpellAbility choice = selected();
        check(choice == null || choice.getHostCard() != bargain, "baseline: RemoveDeck blocks normal Immoral Bargain selection");
    }

    static void xAndTiming() {
        setup();
        check(willing() && x() == 2, "X is capped by useful target count and expendable creatures");
        check(targets().size() == 2 && targets().stream().allMatch(c -> c.getController().isOpponentOf(ai)), "native Destroy AI chooses two opposing permanents");
        check(targets().stream().noneMatch(c -> c.hasKeyword(forge.game.keyword.Keyword.INDESTRUCTIBLE)), "native removal avoids indestructible targets");
        setup(); creature("Grizzly Bears", ai, true); refresh();
        check(willing() && x() == 3, "all-token board offers three expendable bodies");
        setup(); ai.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).forEach(c -> c.setGamePieceType(GamePieceType.CARD));
        refresh();
        setup(); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN, ai);
        check(!willing(), "holds outside own main phase");
        setup(); game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p(1)); check(!willing(), "holds on opponent turn");
        setup(); game.getStack().add(spell.copy(ai)); check(!willing(), "holds with a nonempty stack");
        setup(); creature("Grizzly Bears", ai, true); p(1).getCardsIn(ZoneType.Battlefield).clear();
        p(2).getCardsIn(ZoneType.Battlefield).clear(); p(3).getCardsIn(ZoneType.Battlefield).clear(); refresh();
        check(!willing(), "holds when no opposing nonland permanent can be targeted");
        setup(); for (Player opp : ai.getOpponents()) { for (Card c : opp.getCardsIn(ZoneType.Battlefield)) { c.addIntrinsicKeyword("Indestructible"); } }
        refresh();
        check(!willing(), "holds when native destruction cannot remove the available target");
    }

    static void sacrificeOrder() {
        setup();
        CardCollection candidates = CardLists.filter(ai.getCreaturesInPlay(), CardPredicates.canBeSacrificedBy(spell, false));
        CardCollection payment = ComputerUtil.chooseSacrificeType(ai, "Creature", spell, null, false, 2, null);
        check(payment != null && payment.size() == 2 && payment.get(0).isToken(), "sacrifice payment chooses a token first");
        check(payment.get(1).getName().equals("Walking Corpse") && payment.stream().noneMatch(c -> c.getName().equals("Serra Angel")),
                "then chooses the weakest replaceable creature and preserves the best");
        setup(); ai.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).forEach(c -> c.setGamePieceType(GamePieceType.CARD));
        candidates = CardLists.filter(ai.getCreaturesInPlay(), CardPredicates.canBeSacrificedBy(spell, false));
        CardCollection noTokenPayment = ComputerUtil.chooseSacrificeType(ai, "Creature", spell, null, false, 1, null);
        check(noTokenPayment != null && noTokenPayment.getFirst().getName().equals("Walking Corpse"), "without tokens, sacrifices the weaker body first");
        setup();
        ai.getCardsIn(ZoneType.Battlefield).removeIf(Card::isCreature);
        creature("Serra Angel", ai, false); refresh();
        check(!willing(), "holds when only the best non-token creature would be sacrificed");
    }

    static void consequencesAndFreshState() {
        for (boolean normal : List.of(true, false)) {
            setup(); check(willing() && x() == 2, "native plan before casting " + normal);
            List<Card> chosenTargets = new ArrayList<>(targets());
            long tapped = ai.getLandsInPlay().stream().filter(Card::isTapped).count();
            cast(normal);
            bargain = game.getCardState(bargain);
            check(bargain.isInZone(ZoneType.Graveyard), "spell resolves to graveyard " + normal);
            check(graveyardCount(ai, "Grizzly Bears") == 0 && graveyardCount(ai, "Walking Corpse") == 1
                    && ai.getCreaturesInPlay().stream().noneMatch(c -> c.getName().equals("Grizzly Bears"))
                    && ai.getCreaturesInPlay().stream().anyMatch(c -> c.getName().equals("Serra Angel")), "native payment sacrifices token and weak body, preserving best creature " + normal);
            check(chosenTargets.stream().allMatch(c -> c.isInZone(ZoneType.Graveyard)), "native destroy effect removes its two chosen targets " + normal);
            check(ai.getLandsInPlay().stream().filter(Card::isTapped).count() == tapped + 3, "native colored payment spends exactly one black, one green and one generic mana " + normal);
        }
        setup(); check(willing(), "plan before expendable creature disappears");
        Card best = ai.getCreaturesInPlay().stream().filter(c -> c.getName().equals("Serra Angel")).findFirst().orElseThrow();
        for (Card c : ai.getCreaturesInPlay()) { if (c.isToken() || c.getName().equals("Walking Corpse")) { game.getAction().moveTo(ZoneType.Graveyard, c, null, null); } }
        refresh();
        check(!ComputerUtil.handlePlayingSpellAbility(ai, spell, null) && best.isInPlay()
                && game.getStack().isEmpty() && bargain.isInZone(ZoneType.Hand), "fresh payment check cancels when only best creature remains");
        setup(); check(willing(), "plan before target pool changes");
        p(1).getCardsIn(ZoneType.Battlefield).clear(); p(2).getCardsIn(ZoneType.Battlefield).clear(); p(3).getCardsIn(ZoneType.Battlefield).clear(); refresh();
        check(!ComputerUtil.handlePlayingSpellAbility(ai, spell, null), "fresh payment check cancels when opponents have no target");
    }

    static void isolation() {
        for (String profile : List.of("", "doomsday-confluence", "moonlight-bargain")) {
            AiOverrideProfile.setActive(profile); setup();
            SpellAbility choice = selected(); check(choice == null || choice.getHostCard() != bargain, "blocked card remains unselected under '" + profile + "'");
            check(!willing(), "Immoral Bargain remains blocked under profile '" + profile + "'");
        }
        AiOverrideProfile.setActive("immoral-bargain"); setup(); check(willing(), "approved opt-in profile restores native target decision");
    }

    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
        boolean baseline = args.length > 0 && args[0].equals("baseline");
        AiOverrideProfile.setActive(baseline ? "" : "immoral-bargain");
        if (baseline) { baseline(); } else { xAndTiming(); sacrificeOrder(); consequencesAndFreshState(); isolation(); }
        AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Immoral Bargain checks");
    }
}
