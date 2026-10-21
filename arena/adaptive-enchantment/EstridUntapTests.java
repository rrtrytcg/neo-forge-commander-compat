import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Native Estrid, the Masked decision, selection, loyalty payment and original-effect fixtures; no matches. */
public class EstridUntapTests {
    static Game game;
    static Player ai;
    static Card estrid, land, aura;
    static SpellAbility plus, mask;
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
    /** Estrid at three loyalty in our second main; a tapped Forest carries our Wild Growth. */
    static void setup() {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Estrid fixture " + i, null);
            lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Estrid native fixtures"));
        game.setAge(GameStage.Play);
        ai = p(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        estrid = add("Estrid, the Masked", ai, ZoneType.Battlefield);
        estrid.setCounters(CounterEnumType.LOYALTY, 3);
        plus = estrid.getSpellAbilities().stream().filter(s -> s.getApi() == ApiType.UntapAll).findFirst().orElseThrow();
        mask = estrid.getSpellAbilities().stream().filter(s -> s.getApi() == ApiType.Token).findFirst().orElseThrow();
        plus.setActivatingPlayer(ai);
        mask.setActivatingPlayer(ai);
        land = add("Forest", ai, ZoneType.Battlefield);
        aura = add("Wild Growth", ai, ZoneType.Battlefield);
        aura.attachToEntity(land, null);
        land.setTapped(true);
        refresh();
    }
    /** Same position, but nothing enchanted is tapped and we control a creature worth masking. */
    static Card maskSetup() {
        setup();
        land.setTapped(false);
        Card bears = add("Grizzly Bears", ai, ZoneType.Battlefield);
        add("Grizzly Bears", p(1), ZoneType.Battlefield);
        refresh();
        return bears;
    }
    static boolean willing(SpellAbility sa) { return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa).willingToPlay(); }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    static Card wornMask(Card c) {
        return c.getEnchantedBy().stream().filter(a -> a.getName().equals("Mask") && a.hasKeyword(Keyword.UMBRA_ARMOR)).findFirst().orElse(null);
    }
    static void nativeMask(String label) {
        Card enemy = add("Grizzly Bears", p(1), ZoneType.Battlefield);
        refresh();
        check(willing(mask) && mask.getTargetCard() == enemy, label);
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(20261019));
        AiOverrideProfile.setActive("adaptive-enchantment");
        if (args.length > 0 && args[0].equals("baseline")) {
            setup();
            check(!willing(plus), "baseline failure: +2 rejected with tapped enchanted own Forest");
            setup(); land.setTapped(false); refresh();
            nativeMask("baseline failure: native Mask decision protects an opposing creature");
            System.out.println("DONE " + passed + " baseline checks");
            return;
        }

        // +2 untap
        setup();
        check(willing(plus), "+2 accepts tapped enchanted own land in second main");
        check(!willing(mask), "Mask holds while an enchanted permanent needs untapping");
        check(land.isTapped() && estrid.getCounters(CounterEnumType.LOYALTY) == 3 && game.getStack().isEmpty() && mask.getTargets().isEmpty(),
                "decisions do not untap, pay loyalty, change stack or leave a Mask target");
        SpellAbility chosen = selected();
        check(chosen != null && chosen.getApi() == ApiType.UntapAll, "normal native AI selection chooses Estrid +2 without forced priority");
        check(ComputerUtil.playStack(plus, ai, game), "native +2 activation pays costs and enters stack");
        check(estrid.getCounters(CounterEnumType.LOYALTY) == 5 && land.isTapped(), "original +2 loyalty cost paid before resolution");
        game.getStack().resolveStack(); refresh();
        check(!land.isTapped() && aura.getAttachedTo() == land, "original effect untaps enchanted land without detaching Aura");
        land.setTapped(true); refresh();
        check(!willing(plus) && !willing(mask), "native once-per-turn loyalty restriction remains authoritative");

        setup(); add("Grizzly Bears", ai, ZoneType.Battlefield); refresh();
        check(willing(plus) && !willing(mask), "untapping beats Mask even with an unmasked creature");
        setup(); Card tappedBears = add("Grizzly Bears", ai, ZoneType.Battlefield);
        Card pacifism = add("Pacifism", ai, ZoneType.Battlefield); pacifism.attachToEntity(tappedBears, null);
        land.setTapped(false); tappedBears.setTapped(true); refresh();
        check(willing(plus) && !willing(mask), "tapped enchanted creature also counts as something to untap");
        setup(); Card oppAura = add("Wild Growth", p(1), ZoneType.Battlefield); Card oppLand = add("Forest", p(1), ZoneType.Battlefield);
        oppAura.attachToEntity(oppLand, null); oppLand.setTapped(true); land.setTapped(false);
        Card ownBears = add("Grizzly Bears", ai, ZoneType.Battlefield); refresh();
        check(!willing(plus) && willing(mask) && mask.getTargetCard() == ownBears, "opposing tapped enchanted land does not count");
        setup(); aura.unattachFromEntity(land); refresh();
        check(willing(plus) && !willing(mask), "+2 still gains loyalty when nothing needs untapping and nothing can be masked");
        setup(); land.setTapped(false); refresh();
        check(willing(plus), "untapped enchanted land: +2 for loyalty");
        setup(); game.getAction().moveToGraveyard(aura, null); add("Grizzly Bears", ai, ZoneType.Battlefield); refresh();
        check(!willing(plus) && willing(mask), "removed Aura is rechecked: choice moves to Mask");

        // -1 Mask
        Card bears = maskSetup();
        check(!willing(plus), "+2 holds when a Mask is preferred");
        check(willing(mask) && mask.getTargetCard() == bears, "Mask targets our own creature, never the opposing one");
        chosen = selected();
        check(chosen != null && chosen.getApi() == ApiType.Token && chosen.getTargetCard() == bears, "normal native AI selection chooses Mask on own creature");
        check(ComputerUtil.playStack(mask, ai, game), "native Mask activation pays costs and enters stack");
        check(estrid.getCounters(CounterEnumType.LOYALTY) == 2, "original -1 loyalty cost paid before resolution");
        game.getStack().resolveStack(); refresh();
        Card worn = wornMask(bears);
        check(worn != null && worn.isToken() && ai.equals(worn.getController()), "original effect attaches our Mask token with umbra armor");
        SpellAbility murder = add("Murder", p(1), ZoneType.Hand).getFirstSpellAbility();
        murder.setActivatingPlayer(p(1)); murder.getTargets().add(bears); AbilityUtils.resolve(murder); refresh();
        check(bears.isInPlay() && wornMask(bears) == null, "native umbra armor saves the masked creature and the Mask is destroyed");

        bears = maskSetup(); Card wurm = add("Craw Wurm", ai, ZoneType.Battlefield); refresh();
        check(willing(mask) && mask.getTargetCard() == wurm, "Mask chooses our best creature by native evaluation");
        bears = maskSetup();
        Card umbra = add("Hyena Umbra", ai, ZoneType.Battlefield); umbra.attachToEntity(bears, null); refresh();
        check(!willing(mask) && willing(plus), "creature already wearing umbra armor is not masked again");
        bears = maskSetup(); estrid.setCounters(CounterEnumType.LOYALTY, 2); refresh();
        check(!willing(mask) && willing(plus), "two loyalty: +2 instead of Mask");
        setup(); land.setTapped(false); add("Grizzly Bears", p(1), ZoneType.Battlefield); refresh();
        check(!willing(mask) && willing(plus), "only opposing creatures: never mask them, +2 instead");
        bears = maskSetup(); Card hexBears = add("Grizzly Bears", ai, ZoneType.Battlefield); refresh();
        check(willing(mask) && (mask.getTargetCard() == bears || mask.getTargetCard() == hexBears), "equal own creatures still produce a legal own target");

        // timing and stack
        for (PhaseType phase : List.of(PhaseType.MAIN1, PhaseType.COMBAT_BEGIN, PhaseType.END_OF_TURN)) {
            setup(); game.getPhaseHandler().devModeSet(phase, ai); check(!willing(plus), "+2 holds in " + phase);
            maskSetup(); game.getPhaseHandler().devModeSet(phase, ai); check(!willing(mask), "Mask holds in " + phase);
        }
        setup(); game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p(1)); check(!willing(plus), "+2 holds on opposing turn");
        maskSetup(); game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p(1)); check(!willing(mask), "Mask holds on opposing turn");
        setup(); SpellAbility onStack = add("Grizzly Bears", p(1), ZoneType.Hand).getFirstSpellAbility(); onStack.setActivatingPlayer(p(1));
        game.getStack().add(onStack); check(!willing(plus), "+2 holds with a non-empty stack");

        // profile boundaries
        for (String profile : List.of("", "abzan-armor")) {
            setup(); AiOverrideProfile.setActive(profile); refresh();
            check(!willing(plus), "unchanged native +2 rejection for profile '" + profile + "'");
            setup(); land.setTapped(false); AiOverrideProfile.setActive(profile); refresh();
            nativeMask("unchanged native Mask decision for profile '" + profile + "'");
        }
        AiOverrideProfile.setActive("adaptive-enchantment");
        System.out.println("DONE " + passed + " checks");
    }
}
