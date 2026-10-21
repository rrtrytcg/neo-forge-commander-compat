import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.ability.effects.PeekAndRevealEffect;
import forge.game.card.*;
import forge.game.cost.Cost;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import forge.util.collect.FCollection;
import java.util.*;

/** Real native look, per-card payment, zone changes and no-draw semantics; no policy matches. */
public class MoonlightBargainTests {
    static Game game;
    static Player ai, enemy;
    static Card bargain;
    static SpellAbility spell;
    static int passed;
    static final String[] MIX = {"Sol Ring", "Harmonize", "Craw Wurm", "Artisan of Kozilek", "Forest"};
    static void check(boolean ok, String label) {
        if (!ok) { throw new AssertionError(label); }
        passed++; System.out.println("PASS " + label);
    }
    static Card add(String name, Player owner, ZoneType zone) {
        Card c = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); return c;
    }
    static void add(String name, Player owner, ZoneType zone, int count) {
        for (int i = 0; i < count; i++) { add(name, owner, zone); }
    }
    static void refresh() {
        game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
    }
    static void setup(int life, int lands, int extraHand, String... shown) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Bargain fixture " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Moonlight Bargain native fixtures"));
        game.setAge(GameStage.Play); ai = game.getPlayers().get(0); enemy = game.getPlayers().get(3);
        ai.setLife(life, null); game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, enemy);
        add("Swamp", ai, ZoneType.Battlefield, lands); add("Forest", ai, ZoneType.Hand, extraHand);
        for (String name : shown) { add(name, ai, ZoneType.Library); }
        add("Swamp", ai, ZoneType.Library, 20);
        for (Player player : game.getPlayers()) { if (player != ai) { add("Forest", player, ZoneType.Library, 20); } }
        bargain = add("Moonlight Bargain", ai, ZoneType.Hand); spell = bargain.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
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
    static long count(ZoneType zone, String name) { return ai.getCardsIn(zone).stream().filter(c -> c.getName().equals(name)).count(); }
    static Set<String> handNames() {
        Set<String> names = new TreeSet<>(); for (Card card : ai.getCardsIn(ZoneType.Hand)) { names.add(card.getName()); } return names;
    }
    static void cast() {
        check(ComputerUtil.playStack(spell, ai, game), "native payment stacks Bargain"); resolveAll();
        bargain = game.getCardState(bargain);
    }
    static void baseline() {
        setup(40, 5, 0, "Forest", "Sol Ring", "Llanowar Elves", "Grizzly Bears", "Harmonize");
        SpellAbility choice = selected(); check(choice == null || choice.getHostCard() != bargain, "baseline: RemoveDeck blocks normal selection");
        cast();
        check(handNames().equals(Set.of("Grizzly Bears")), "baseline: native payments refuse land, noncreatures and low-power creature");
        setup(40, 5, 0, "Grizzly Bears", "Grizzly Bears", "Grizzly Bears", "Grizzly Bears", "Grizzly Bears"); cast();
        check(ai.getLife() == 30 && count(ZoneType.Hand, "Grizzly Bears") == 5, "baseline: native policy spends ten life keeping all five creatures");
    }
    static void casting() {
        setup(14, 5, 2, MIX); check(willing(), "exact cast threshold: life 14, hand three, five mana, last opponent end step");
        SpellAbility choice = selected();
        check(choice != null && choice.getHostCard() == bargain, "normal AI selection chooses Bargain");
        setup(13, 5, 0, MIX); check(!willing(), "life thirteen holds");
        setup(40, 5, 3, MIX); check(!willing(), "four-card hand including Bargain holds");
        setup(40, 4, 0, MIX); check(!willing(), "four mana holds");
        setup(40, 0, 0, MIX); add("Mountain", ai, ZoneType.Battlefield, 5); refresh(); check(!willing(), "five red mana cannot pay BB");
        setup(40, 5, 0, MIX); ai.getLandsInPlay().getFirst().setTapped(true); check(!willing(), "tapped mana cannot be reused");
        setup(40, 5, 0, MIX);
        while (ai.getCardsIn(ZoneType.Library).size() > 6) { ai.getZone(ZoneType.Library).remove(ai.getCardsIn(ZoneType.Library).getLast()); }
        check(!willing(), "six library cards holds"); add("Swamp", ai, ZoneType.Library); refresh(); check(willing(), "seven library cards qualifies");
        for (PhaseType phase : List.of(PhaseType.MAIN1, PhaseType.MAIN2, PhaseType.COMBAT_BEGIN, PhaseType.COMBAT_DECLARE_BLOCKERS)) {
            setup(40, 5, 0, MIX); game.getPhaseHandler().devModeSet(phase, enemy); check(!willing(), "holds in opponent phase " + phase);
        }
        setup(40, 5, 0, MIX); game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, ai); check(!willing(), "own end step holds");
        setup(40, 5, 0, MIX); game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, game.getPlayers().get(1)); check(!willing(), "earlier opponent end step holds");
        setup(40, 5, 0, MIX); game.getPhaseHandler().addExtraTurn(enemy); check(!willing(), "native extra turn before us postpones cast");
        setup(40, 5, 0, MIX); game.getStack().add(spell.copy(ai)); check(!willing(), "nonempty stack holds");
        setup(40, 5, 0, MIX); spell.getMapParams().put("PeekAmount", "6"); check(!willing(), "modified look shape holds");
        setup(40, 5, 0, "Forest", "Forest", "Forest", "Forest", "Forest"); check(willing(), "cast decision does not peek at five lands");
        setup(40, 5, 0, "Sol Ring", "Sol Ring", "Sol Ring", "Sol Ring", "Sol Ring"); check(willing(), "cast decision identical for five nonlands");
    }
    static void outcomes() {
        setup(40, 5, 0, MIX); cast();
        check(handNames().equals(Set.of("Forest", "Craw Wurm", "Harmonize")), "keep needed land then highest near-term nonlands");
        check(ai.getLife() == 34 && ai.getCardsIn(ZoneType.Hand).size() == 3, "three keeps spend exactly six life from forty");
        check(count(ZoneType.Graveyard, "Sol Ring") == 1 && count(ZoneType.Graveyard, "Artisan of Kozilek") == 1,
                "unselected cheap spell and distant expensive spell go to graveyard");
        check(ai.getCardsIn(ZoneType.Library).size() == 20 && bargain.isInZone(ZoneType.Graveyard), "native resolution removes exactly the top five and finishes Bargain");
        check(!bargain.hasRemembered() && bargain.getImprintedCards().isEmpty(), "native cleanup clears remembered and imprinted cards");
        check(ai.getLandsInPlay().stream().allMatch(Card::isTapped), "native cast paid all five Swamps");
        setup(14, 5, 0, MIX); cast();
        check(handNames().equals(Set.of("Forest", "Craw Wurm")) && ai.getLife() == 10, "life fourteen keeps two including needed land and retains ten");
        setup(15, 5, 0, MIX); cast(); check(ai.getLife() == 11 && ai.getCardsIn(ZoneType.Hand).size() == 2, "odd life fifteen keeps two and retains eleven");
        setup(16, 5, 0, MIX); cast(); check(ai.getLife() == 10 && ai.getCardsIn(ZoneType.Hand).size() == 3, "life sixteen keeps three and retains ten");
        setup(40, 6, 0, MIX); cast();
        check(handNames().equals(Set.of("Craw Wurm", "Harmonize", "Sol Ring")), "six lands: no extra land bought");
        setup(40, 5, 1, MIX); cast();
        check(count(ZoneType.Hand, "Forest") == 1 && handNames().equals(Set.of("Forest", "Craw Wurm", "Harmonize", "Sol Ring")), "existing hand land prevents buying another");
        setup(40, 5, 0, "Forest", "Island", "Swamp", "Mountain", "Plains"); cast();
        check(ai.getLife() == 38 && handNames().equals(Set.of("Forest")), "five lands: buy only one needed land, deterministic name tie break");
        setup(40, 6, 0, "Forest", "Island", "Swamp", "Mountain", "Plains"); cast();
        check(ai.getLife() == 40 && ai.getCardsIn(ZoneType.Hand).isEmpty(), "unfavorable five lands with six in play: pay nothing");
        setup(40, 5, 0, "Ulamog, the Infinite Gyre", "Kozilek, Butcher of Truth", "Artisan of Kozilek", "Darksteel Colossus", "Blightsteel Colossus"); cast();
        check(handNames().equals(Set.of("Artisan of Kozilek", "Kozilek, Butcher of Truth", "Darksteel Colossus")), "expensive fallback: cheapest first, then name");
        setup(40, 5, 0, "Sign in Blood", "Naturalize", "Grizzly Bears", "Doom Blade", "Llanowar Elves"); cast();
        check(handNames().equals(Set.of("Doom Blade", "Grizzly Bears", "Naturalize")), "equal mana value chooses by card name across types");
        setup(40, 5, 0, "Llanowar Elves", "Sol Ring", "Forest", "Island", "Swamp"); cast();
        check(handNames().equals(Set.of("Forest", "Llanowar Elves", "Sol Ring")), "low-power creature and noncreature are both eligible");
        setup(40, 5, 0, "Grizzly Bears", "Grizzly Bears", "Grizzly Bears", "Grizzly Bears", "Grizzly Bears"); cast();
        check(count(ZoneType.Hand, "Grizzly Bears") == 3 && ai.getLife() == 34, "identical card names still keep only three distinct cards for six life");
        setup(40, 5, 0, MIX); add("Underworld Dreams", enemy, ZoneType.Battlefield); refresh(); cast();
        check(ai.getNumDrawnThisTurn() == 0 && ai.getLife() == 34, "hand moves are not draws and do not trigger Underworld Dreams");
        setup(40, 5, 0, MIX); add("Platinum Emperion", ai, ZoneType.Battlefield); refresh(); cast();
        check(ai.getLife() == 40 && ai.getCardsIn(ZoneType.Hand).isEmpty(), "native inability to pay life declines every keep");
    }
    static SpellAbility prepare(int life, String... cards) {
        setup(life, 5, 0, cards);
        check(ComputerUtil.playStack(spell, ai, game), "prepare native paid spell for payment-stage checks");
        bargain = spell.getHostCard(); new PeekAndRevealEffect().resolve(spell);
        check(bargain.getRememberedCount() == 5, "native look supplies all five legally shown cards before choices");
        return spell.getSubAbility().getAdditionalAbility("RepeatSubAbility");
    }
    static boolean ask(SpellAbility payment, String name, boolean pay) {
        Card card = ai.getCardsIn(ZoneType.Library).stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
        bargain.addImprintedCard(card); FCollection<Player> payers = new FCollection<>(); payers.add(ai);
        boolean result = pay ? ai.getController().payCostToPreventEffect(new Cost("PayLife<2>", true), payment, false, payers)
                : SpellApiToAi.Converter.get(payment).willPayUnlessCost(ai, payment, new Cost("PayLife<2>", true), false, payers);
        bargain.removeImprintedCard(card); return result;
    }
    static void rechecks() {
        for (boolean normal : List.of(false, true)) {
            for (String change : List.of("hand", "life", "library", "phase", "mana")) {
                setup(40, 5, 0, MIX); check(willing(), "planned cast qualifies before " + change + " " + normal);
                switch (change) {
                    case "hand": add("Forest", ai, ZoneType.Hand, 3); break;
                    case "life": ai.setLife(13, null); break;
                    case "library": while (ai.getCardsIn(ZoneType.Library).size() > 6) { ai.getZone(ZoneType.Library).remove(ai.getCardsIn(ZoneType.Library).getLast()); } break;
                    case "phase": game.getPhaseHandler().devModeSet(PhaseType.MAIN2, enemy); break;
                    case "mana": ai.getLandsInPlay().getFirst().setTapped(true); break;
                }
                long before = ai.getLandsInPlay().stream().filter(Card::isTapped).count();
                boolean played = normal ? ComputerUtil.handlePlayingSpellAbility(ai, spell, null) : ComputerUtil.playStack(spell, ai, game);
                check(!played && bargain.isInZone(ZoneType.Hand) && game.getStack().isEmpty()
                        && ai.getLandsInPlay().stream().filter(Card::isTapped).count() == before, "stale " + change + " cancels without cost " + normal);
            }
        }
        SpellAbility payment = prepare(14, MIX);
        check(ask(payment, "Forest", false) && ask(payment, "Forest", false), "repeated decision does not consume budget");
        ai.setLife(40, null);
        check(!ask(payment, "Harmonize", false), "later life gain does not expand the original two-card keep set");
        ai.setLife(11, null); check(!ask(payment, "Craw Wurm", false), "fresh payment life eleven protects ten-life floor");
        ai.setLife(12, null); check(ask(payment, "Craw Wurm", true) && ai.getLife() == 10, "native per-card payment pays two down to exact floor");
        payment = prepare(40, MIX); add("Platinum Emperion", ai, ZoneType.Battlefield); refresh();
        check(!ask(payment, "Forest", false), "new payment prohibition is rechecked at resolution");
        setup(40, 5, 0, MIX); check(ComputerUtil.handlePlayingSpellAbility(ai, spell, null), "normal execution entry point casts Bargain"); resolveAll();
        check(ai.getLife() == 34 && handNames().equals(Set.of("Forest", "Craw Wurm", "Harmonize")), "normal execution uses same native resolution and keep policy");
        setup(40, 5, 0, MIX); cast(); check(ai.getLife() == 34, "new game has an independent keep budget");
        for (Card card : new ArrayList<Card>(ai.getCardsIn(ZoneType.Hand))) {
            game.getAction().moveToGraveyard(card, null);
        }
        bargain = game.getAction().moveToHand(bargain, null); spell = bargain.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        for (Card land : ai.getLandsInPlay()) { land.setTapped(false); }
        for (Card card : new ArrayList<Card>(ai.getCardsIn(ZoneType.Library))) { ai.getZone(ZoneType.Library).remove(card); }
        for (String name : MIX) { add(name, ai, ZoneType.Library); }
        add("Swamp", ai, ZoneType.Library, 20); ai.setLife(14, null); refresh(); cast();
        check(ai.getLife() == 10 && handNames().equals(Set.of("Forest", "Craw Wurm")), "recasting the same card starts a fresh two-card budget");
    }
    static void isolation() {
        for (String profile : List.of("", "jolrael", "easy-cards-3")) {
            AiOverrideProfile.setActive(profile); setup(40, 5, 0, MIX);
            SpellAbility choice = selected(); check(choice == null || choice.getHostCard() != bargain, "Bargain remains blocked for profile '" + profile + "'");
            cast(); check(handNames().equals(Set.of("Craw Wurm", "Artisan of Kozilek")) && ai.getLife() == 36, "native per-card payment unchanged for profile '" + profile + "'");
        }
        AiOverrideProfile.setActive("moonlight-bargain"); setup(40, 5, 0, MIX); check(willing(), "opt-in restored after unrelated profiles");
    }
    static void copiedGameBudget() {
        SpellAbility payment = prepare(14, MIX);
        check(ask(payment, "Forest", false), "original game seeds a two-card keep set");
        Game liveGame = game; Player liveAi = ai, liveEnemy = enemy; Card liveBargain = bargain; SpellAbility liveSpell = spell;
        var copier = new forge.ai.simulation.GameCopier(liveGame);
        game = copier.makeCopy(); ai = copier.find(liveAi); enemy = copier.find(liveEnemy); bargain = copier.find(liveBargain);
        spell = liveSpell.copy(bargain, ai, true); spell.setActivatingPlayer(ai); ai.setLife(40, null); refresh();
        check(spell.getId() == liveSpell.getId() && game != liveGame && bargain.getGame() == game,
                "native LKI ability retains its ID across separate copied games");
        bargain.clearRemembered(); new PeekAndRevealEffect().resolve(spell);
        check(bargain.getRememberedCount() == 5, "copied game performs its own native legal look before payment choices");
        SpellAbility copiedPayment = spell.getSubAbility().getAdditionalAbility("RepeatSubAbility");
        check(ask(copiedPayment, "Harmonize", false), "copied game's larger life budget does not inherit the live keep set");
        game = liveGame; ai = liveAi; enemy = liveEnemy; bargain = liveBargain; spell = liveSpell; ai.setLife(40, null);
        check(!ask(payment, "Harmonize", false), "copied evaluation cannot expand the live resolution's original two-card set");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
        boolean baseline = args.length > 0 && args[0].equals("baseline");
        AiOverrideProfile.setActive(baseline ? "" : "moonlight-bargain");
        if (baseline) { baseline(); } else { casting(); outcomes(); rechecks(); copiedGameBudget(); isolation(); }
        AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Moonlight Bargain checks");
    }
}
