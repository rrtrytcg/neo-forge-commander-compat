import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.ai.ability.ChangeZoneAi;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.AbilityUtils;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Native Winter trigger, pile selection, and original exile-return effect fixtures. */
public class WinterCynicalOpportunistTests {
    static Game game;
    static Player ai;
    static Card winter, wurm, bear, murder, secondMurder, tutor, forest;
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
        c.setSickness(false);
        return c;
    }
    static Player p(int i) { return game.getPlayers().get(i); }
    static void refresh() {
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
    }
    static void setup(boolean profile) {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Winter fixture " + i, null);
            lobby.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(regs, rules, new Match(rules, regs, "Winter native fixtures"));
        game.setAge(GameStage.Play);
        ai = p(0);
        game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, ai);
        winter = add("Winter, Cynical Opportunist", ai, ZoneType.Battlefield);
        wurm = add("Craw Wurm", ai, ZoneType.Graveyard);
        bear = add("Grizzly Bears", ai, ZoneType.Graveyard);
        murder = add("Murder", ai, ZoneType.Graveyard);
        secondMurder = add("Murder", ai, ZoneType.Graveyard);
        tutor = add("Demonic Tutor", ai, ZoneType.Graveyard);
        forest = add("Forest", ai, ZoneType.Graveyard);
        refresh();
        AiOverrideProfile.setActive(profile ? "winter-cynical-opportunist" : "");
    }
    static SpellAbility exile() {
        var trigger = winter.getTriggers().stream()
                .filter(t -> "TrigExileDelirium".equals(t.getParam("Execute")))
                .findFirst().orElseThrow();
        SpellAbility sa = trigger.ensureAbility().copy(ai);
        sa.setTrigger(trigger);
        sa.setActivatingPlayer(ai);
        return sa;
    }
    static Card choose(SpellAbility sa, CardCollection options) {
        return ChangeZoneAi.chooseCardToHiddenOriginChangeZone(ZoneType.Exile,
                List.of(ZoneType.Graveyard), sa, options, ai, ai);
    }
    static CardCollection graveyardOptions() { return new CardCollection(ai.getCardsIn(ZoneType.Graveyard)); }

    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(20261020));

        if (args.length > 0 && args[0].equals("baseline")) {
            setup(false);
            SpellAbility sa = exile();
            Card nativeFirst = choose(sa, graveyardOptions());
            check(nativeFirst != wurm, "baseline native exile chooser prefers its generic worst graveyard card");
            System.out.println("DONE " + passed + " baseline checks");
            return;
        }

        setup(true);
        SpellAbility sa = exile();
        check(ai.hasDelirium(), "fixture has four distinct card types in the graveyard");
        check(SpellApiToAi.Converter.get(sa).doTrigger(ai, sa, false),
                "delirium trigger is accepted when a returnable permanent and enough type support exist");
        CardCollection options = graveyardOptions();
        Card selected = choose(sa, options);
        check(selected == wurm, "best permanent is selected first for return");
        options.remove(selected);
        int selectedTypes = selected.getType().getCoreTypes().size();
        Card support = choose(sa, options);
        check(support != null && !support.isPermanent(), "first support choice is a nonpermanent");
        selectedTypes += support.getType().getCoreTypes().size();
        options.remove(support);
        while (selectedTypes < 4) {
            support = choose(sa, options);
            check(support != null && !support.isPermanent(), "remaining type support avoids a second permanent");
            selectedTypes += support.getType().getCoreTypes().size();
            options.remove(support);
        }
        check(choose(sa, options) == null, "selection stops once the aggregate four-type threshold is met");

        sa = exile();
        game.getStack().add(sa);
        game.getStack().resolveStack();
        refresh();
        check(game.getCardState(wurm).isInPlay() && game.getCardState(bear).isInZone(ZoneType.Graveyard),
                "native chained effect returns the selected best permanent only");
        check(game.getCardState(murder).isInZone(ZoneType.Exile)
                        && game.getCardState(secondMurder).isInZone(ZoneType.Exile)
                        && game.getCardState(tutor).isInZone(ZoneType.Exile),
                "native effect exiles only the support cards needed for four total types");

        setup(true);
        sa = exile();
        game.getCardsIn(ZoneType.Graveyard).stream().filter(c -> c.getOwner() == ai && c != wurm && c != bear)
                .forEach(c -> game.getAction().exile(c, null, forge.game.ability.AbilityKey.newMap()));
        refresh();
        check(!SpellApiToAi.Converter.get(sa).doTrigger(ai, sa, false),
                "trigger holds when remaining nonpermanents cannot complete the four-type pile");

        setup(true);
        Card duplicateWinter = add("Winter, Cynical Opportunist", ai, ZoneType.Graveyard);
        refresh();
        sa = exile();
        check(choose(sa, graveyardOptions()) != duplicateWinter,
                "a legendary duplicate is not chosen when Winter is already on the battlefield");

        setup(false);
        sa = exile();
        check(AiOverrideProfile.get().getAiLogic(winter).isEmpty(), "default profile leaves Winter without a card policy");
        check(choose(sa, graveyardOptions()) != wurm, "default profile preserves the native graveyard choice");
        AiOverrideProfile.setActive("");
        System.out.println("DONE " + passed + " Winter checks");
    }
}
