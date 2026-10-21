import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Native baseline, profile isolation, mana-aware cast policy, and the original trigger result. */
public class RideTheAvalancheTests {
    static Game game;
    static Player ai;
    static Card ride;
    static SpellAbility spell;
    static Card target;
    static int passed;

    static void check(boolean ok, String label) {
        if (!ok) { throw new AssertionError(label); }
        passed++; System.out.println("PASS " + label);
    }
    static Player p(int i) { return game.getPlayers().get(i); }
    static Card add(String name, Player owner, ZoneType zone) {
        Card c = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); c.setSickness(false); return c;
    }
    static void add(String name, Player owner, ZoneType zone, int count) { for (int i=0; i<count; i++) { add(name, owner, zone); } }
    static void refresh() {
        game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
    }
    static void setup(boolean followup, boolean creatureTarget) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i=0; i<2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Ride the Avalanche fixture " + i, null); lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Ride the Avalanche fixtures")); game.setAge(GameStage.Play);
        ai = p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        add("Forest", ai, ZoneType.Battlefield, 2); add("Island", ai, ZoneType.Battlefield, 2);
        for (Player player : game.getPlayers()) { add("Forest", player, ZoneType.Library, 20); }
        ride = add("Ride the Avalanche", ai, ZoneType.Hand); spell = ride.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        if (followup) { add("Grizzly Bears", ai, ZoneType.Hand); }
        if (creatureTarget) { target = add("Grizzly Bears", ai, ZoneType.Battlefield); }
        refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai, spell).willingToPlay(); }
    static SpellAbility selected() {
        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }
    static SpellAbility spell(Card card) { return card.getFirstSpellAbility(); }
    static void resolveAll() {
        game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        while (!game.getStack().isEmpty()) {
            game.getStack().resolveStack(); game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        }
        refresh();
    }
    static void nativeBaseline() {
        AiOverrideProfile.setActive(""); setup(true, true);
        check(!willing(), "baseline: generic Effect AI declines Ride without an AILogic case");
        check(selected() == null || selected().getHostCard() != ride, "baseline: RemoveDeck excludes Ride from ordinary spell selection");
    }
    static void safeUseAndNativeResolution() {
        AiOverrideProfile.setActive("ride-the-avalanche"); setup(true, true);
        check("RideTheAvalanche".equals(AiOverrideProfile.get().getAiLogic(ride)), "profile enables only the named Ride rule");
        check(willing(), "casts when a useful creature and jointly affordable hand spell are available");
        check(selected() != null && selected().getHostCard() == ride, "native selection chooses the profile-enabled Ride spell");
        check(ComputerUtil.playStack(spell, ai, game), "native payment casts Ride");
        resolveAll();
        Card followup = ai.getCardsIn(ZoneType.Hand).stream().filter(c -> c.getName().equals("Grizzly Bears")).findFirst().orElseThrow();
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN, ai); refresh();
        check(ComputerUtil.playStack(spell(followup), ai, game), "native payment casts the affordable follow-up spell");
        resolveAll();
        check(target.getCounters(CounterEnumType.P1P1) == 2, "native trigger puts counters equal to follow-up mana value on our creature");
        followup = game.getCardState(followup);
        check(followup.isInZone(ZoneType.Battlefield), "follow-up spell resolves natively");
    }
    static void holdsAndIsolation() {
        AiOverrideProfile.setActive("ride-the-avalanche"); setup(false, true);
        check(!willing(), "holds without another nonland spell in hand");

        setup(true, true);
        ai.getCardsIn(ZoneType.Battlefield).removeIf(c -> c.isLand() && c.getName().equals("Forest")
                || c.isLand() && c.getName().equals("Island"));
        add("Forest", ai, ZoneType.Battlefield); add("Island", ai, ZoneType.Battlefield); refresh();
        check(ComputerUtilCost.canPayCost(spell, ai, false) && !willing(), "holds when Ride is payable but the follow-up is not jointly affordable");

        setup(true, false);
        check(!willing(), "holds when no creature can benefit from the counter trigger");

        setup(true, true); target.addIntrinsicKeyword("Shroud"); refresh();
        check(!willing(), "holds when the only counter-receiving creature is not a legal trigger target");

        setup(true, true); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN, ai); refresh();
        check(!willing(), "holds outside our main phases");

        setup(true, true); game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p(1)); refresh();
        check(!willing(), "holds during an opponent's turn");

        setup(true, true); ai.getCardsIn(ZoneType.Battlefield).removeIf(c -> c.isLand()); refresh();
        check(!willing(), "native affordability holds when Ride's mana cost cannot be paid");

        for (String profile : List.of("", "jolrael")) {
            AiOverrideProfile.setActive(profile); setup(true, true);
            check(!"RideTheAvalanche".equals(AiOverrideProfile.get().getAiLogic(ride)) && !willing()
                    && (selected() == null || selected().getHostCard() != ride),
                    "profile isolation leaves Ride blocked under '" + profile + "'");
        }
        AiOverrideProfile.setActive("ride-the-avalanche"); setup(true, true);
        Card unrelated = add("Gideon Jura", ai, ZoneType.Hand); refresh();
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(unrelated), "profile does not admit unrelated cards");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, null); MyRandom.setRandom(new Random(20261020));
        if (args.length > 0 && "baseline".equals(args[0])) { nativeBaseline(); }
        else { safeUseAndNativeResolution(); holdsAndIsolation(); }
        AiOverrideProfile.setActive(""); System.out.println("DONE " + passed + " Ride the Avalanche checks");
    }
}
