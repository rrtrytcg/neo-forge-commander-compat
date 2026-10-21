import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
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

/** Native Jolrael decisions, normal selection, payment, triggers and lifecycle fixtures. */
public class JolraelTests {
    static Game game;
    static Player ai, enemy;
    static Card jolrael, first, second;
    static SpellAbility activation;
    static int passed;
    static void check(boolean ok, String label) {
        if (!ok) { throw new AssertionError(label); }
        passed++; System.out.println("PASS " + label);
    }
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
    static void setup(int hand, int lands) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Jolrael fixture " + i, null);
            lobby.setAiProfile("Default"); regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Jolrael native fixtures"));
        game.setAge(GameStage.Play); ai = game.getPlayers().get(0); enemy = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN, ai);
        add("Forest", ai, ZoneType.Battlefield, lands); add("Forest", ai, ZoneType.Hand, hand);
        add("Forest", ai, ZoneType.Library, 20); add("Forest", enemy, ZoneType.Library, 20);
        jolrael = add("Jolrael, Mwonvuli Recluse", ai, ZoneType.Battlefield);
        jolrael.setTapped(true); // not an attacker; its ability has no tap cost
        first = add("Grizzly Bears", ai, ZoneType.Battlefield); first.setSickness(false);
        second = add("Grizzly Bears", ai, ZoneType.Battlefield); second.setSickness(false);
        activation = jolrael.getSpellAbilities().stream().filter(s -> s.isActivatedAbility() && s.getApi() == ApiType.AnimateAll).findFirst().orElseThrow();
        activation.setActivatingPlayer(ai); refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(activation).canPlayWithSubs(ai, activation).willingToPlay(); }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    static void resolveAll() {
        for (int i = 0; i < 12; i++) {
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) { break; }
            game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        }
    }
    static long tappedLands() { return ai.getLandsInPlay().stream().filter(Card::isTapped).count(); }
    static void baseline() {
        setup(4, 6);
        check(!willing(), "baseline: native AnimateAll rejects useful hand-size pump");
        SpellAbility chosen = selected();
        check(chosen == null || chosen.getHostCard() != jolrael, "baseline: RemoveDeck blocks normal activation selection");
    }
    static void offense() {
        setup(4, 6);
        check(willing(), "exact threshold: two ready Bears gain four total power");
        check(tappedLands() == 0 && first.getNetPower() == 2, "evaluation leaves mana and live stats untouched");
        for (int i = 0; i < 5; i++) { check(willing(), "repeat evaluation is stable " + i); }
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == jolrael && chosen.getApi() == ApiType.AnimateAll,
                "normal native selection chooses Jolrael activation");
        setup(3, 6); check(!willing(), "two Bears gaining only two total power hold");
        setup(0, 6); check(!willing(), "empty hand holds");
        setup(2, 6); check(!willing(), "no power improvement holds");
        setup(4, 6); second.setSickness(true); check(!willing(), "summoning-sick creature does not count");
        setup(4, 6); second.setTapped(true); check(!willing(), "tapped creature does not count");
        setup(4, 6); second.setPhasedOut(ai); check(!willing(), "phased-out creature does not count");
        setup(4, 6); ai.getZone(ZoneType.Battlefield).remove(second); add("Wall of Wood", ai, ZoneType.Battlefield).setSickness(false); refresh();
        check(!willing(), "defender does not count");
        setup(6, 6); second.setTapped(true); check(!willing(), "one attacker with four power gain is insufficient");
        setup(4, 5); check(!willing(), "five lands cannot pay six mana");
        setup(4, 0); add("Mountain", ai, ZoneType.Battlefield, 6); refresh(); check(!willing(), "six red mana cannot pay GG");
        setup(4, 6); ai.getLandsInPlay().getFirst().setTapped(true); check(!willing(), "tapped lands cannot be reused");
        setup(4, 6);
        for (PhaseType phase : List.of(PhaseType.MAIN1, PhaseType.COMBAT_DECLARE_ATTACKERS, PhaseType.COMBAT_DECLARE_BLOCKERS, PhaseType.MAIN2)) {
            game.getPhaseHandler().devModeSet(phase, ai); check(!willing(), "holds outside beginning of combat: " + phase);
        }
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN, enemy); check(!willing(), "holds on opponent beginning of combat");
    }
    static void safety() {
        setup(4, 6); Card large = add("Craw Wurm", ai, ZoneType.Battlefield); large.setTapped(true); refresh();
        check(!willing(), "holds rather than shrink a tapped nonattacker's power");
        setup(4, 6); add("Wall of Air", ai, ZoneType.Battlefield); refresh(); check(!willing(), "holds rather than shrink a defender's toughness");
        setup(4, 6); first.addCounterInternal(CounterEnumType.P1P1, 3, ai, false, null, null); refresh();
        check(willing(), "native preview retains counters after base setting");
        Card after = CombatBaseStatsAi.preview(ai, first, activation);
        check(after.getNetPower() == 7 && after.getNetToughness() == 7 && first.getNetPower() == 5, "counter preview is layered and does not mutate live card");
        setup(4, 6); add("Glorious Anthem", ai, ZoneType.Battlefield); refresh(); check(willing(), "constant anthem is supported");
        after = CombatBaseStatsAi.preview(ai, first, activation);
        check(after.getNetPower() == 5 && first.getNetPower() == 3, "anthem is retained exactly once");
        setup(4, 6); add("Coat of Arms", enemy, ZoneType.Battlefield); refresh(); check(!willing(), "uncertain variable continuous effects hold");
        setup(4, 6); activation.getMapParams().put("Power", "9"); check(!willing(), "modified ability shape is refused");
        setup(4, 6); game.getStack().add(activation.copy(ai)); check(!willing(), "pending activation prevents another activation");
        setup(4, 6); jolrael.setPhasedOut(ai); check(!willing(), "phased-out source holds");
        setup(4, 6); add("Cursed Totem", enemy, ZoneType.Battlefield); refresh();
        check(((PlayerControllerAi) ai.getController()).getAi().canPlaySa(activation) != AiPlayDecision.WillPlay, "native activation prohibition is retained");
        setup(4, 6); game.getRules().addAppliedVariant(GameType.Commander);
        for (Player player : game.getPlayers()) { player.createCommanderEffect(); }
        refresh(); check(willing(), "native Commander casting permission cannot change the stat preview");
        Card effect = ai.getCardsIn(ZoneType.Command).stream().filter(c -> c.getName().equals("Commander Effect")).findFirst().orElseThrow();
        effect.addStaticAbility("Mode$ Continuous | EffectZone$ Command | Affected$ Creature.YouCtrl | SetPower$ 1");
        refresh(); check(!willing(), "other continuous effects on Commander Effect are not exempted");
    }
    static void execution() {
        setup(4, 12); check(willing(), "activation qualifies before native payment");
        check(ComputerUtil.playStack(activation, ai, game), "native playStack pays and stacks activation");
        check(tappedLands() == 6, "native payment taps exactly six Forests");
        check(!willing(), "pending activation holds even with six mana left");
        resolveAll();
        check(first.getNetPower() == 4 && first.getNetToughness() == 4 && second.getNetPower() == 4 && jolrael.getNetPower() == 4,
                "original effect sets every friendly creature to hand-size 4/4");
        add("Forest", ai, ZoneType.Hand, 2); refresh();
        check(!willing(), "resolved activation is not repeated even after hand grows");
        game.getEndOfTurn().executeUntil(); refresh();
        check(first.getNetPower() == 2 && first.getNetToughness() == 2 && jolrael.getNetPower() == 1 && jolrael.getNetToughness() == 2,
                "native end-of-turn restores original base stats");
        setup(4, 6);
        check(ComputerUtil.handlePlayingSpellAbility(ai, activation, null), "normal execution entry point pays original activation");
        resolveAll(); check(tappedLands() == 6 && first.getNetPower() == 4, "normal execution entry point resolves original effect");
        for (boolean normal : List.of(false, true)) {
            setup(4, 6); check(willing(), "plan qualifies before hand change " + normal);
            ai.getZone(ZoneType.Hand).remove(ai.getCardsIn(ZoneType.Hand).getFirst()); refresh();
            boolean played = normal ? ComputerUtil.handlePlayingSpellAbility(ai, activation, null) : ComputerUtil.playStack(activation, ai, game);
            check(!played && tappedLands() == 0 && game.getStack().isEmpty(), "fresh hand recheck cancels without payment " + normal);
            setup(4, 6); check(willing(), "plan qualifies before board change " + normal);
            add("Craw Wurm", ai, ZoneType.Battlefield); refresh();
            played = normal ? ComputerUtil.handlePlayingSpellAbility(ai, activation, null) : ComputerUtil.playStack(activation, ai, game);
            check(!played && tappedLands() == 0 && game.getStack().isEmpty(), "fresh board recheck cancels without payment " + normal);
        }
        setup(4, 6); check(willing(), "plan qualifies before source leaves play");
        ai.getZone(ZoneType.Battlefield).remove(jolrael); ai.getZone(ZoneType.Graveyard).add(jolrael); refresh();
        check(!ComputerUtil.handlePlayingSpellAbility(ai, activation, null) && tappedLands() == 0, "missing source cancels before payment");
        setup(4, 6); check(willing(), "new game has no leaked activation history");
    }
    static void castingAndTrigger() {
        setup(0, 2); ai.getZone(ZoneType.Battlefield).remove(jolrael); ai.getZone(ZoneType.Hand).add(jolrael);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai); refresh();
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getHostCard() == jolrael && chosen.isSpell(), "normal native selection casts Jolrael");
        check(ComputerUtil.playStack(chosen, ai, game), "native creature payment casts Jolrael"); resolveAll();
        jolrael = game.getCardState(jolrael);
        check(jolrael.isInPlay() && jolrael.getNetPower() == 1 && jolrael.getNetToughness() == 2, "original Jolrael enters as 1/2");
        add("Forest", ai, ZoneType.Battlefield, 4);
        Card harmonize = add("Harmonize", ai, ZoneType.Hand); refresh();
        SpellAbility draw = harmonize.getFirstSpellAbility(); draw.setActivatingPlayer(ai);
        check(ComputerUtil.playStack(draw, ai, game), "native Harmonize payment supplies three draws"); resolveAll();
        check(ai.getCardsIn(ZoneType.Hand).size() == 3, "native draw puts three cards in hand");
        check(ai.getCreaturesInPlay().stream().filter(c -> c.isToken() && c.getType().hasSubtype("Cat") && c.getNetPower() == 2 && c.getNetToughness() == 2).count() == 1,
                "second-card native trigger creates exactly one original 2/2 Cat");
    }
    static void isolation() {
        for (String profile : List.of("", "easy-cards-3", "biomass-mutation")) {
            AiOverrideProfile.setActive(profile); setup(4, 6);
            check(!willing(), "native activation remains refused in profile '" + profile + "'");
            SpellAbility chosen = selected();
            check(chosen == null || chosen.getHostCard() != jolrael, "Jolrael stays blocked in profile '" + profile + "'");
        }
        AiOverrideProfile.setActive(""); activation.getMapParams().put("AILogic", "Always");
        check(willing(), "unprofiled native AnimateAll Always behavior stays unchanged");
        AiOverrideProfile.setActive("jolrael"); setup(4, 6); check(willing(), "opt-in restored after unrelated profiles");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
        AiOverrideProfile.setActive(args.length > 0 && args[0].equals("baseline") ? "" : "jolrael");
        if (args.length > 0 && args[0].equals("baseline")) { baseline(); }
        else { offense(); safety(); execution(); castingAndTrigger(); isolation(); }
        AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Jolrael checks");
    }
}
