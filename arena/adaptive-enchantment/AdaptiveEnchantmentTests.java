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

import forge.game.ability.AbilityUtils;
import forge.game.ability.AbilityFactory;
import forge.card.mana.ManaCost;
import forge.game.mana.ManaCostBeingPaid;

class AdaptiveEnchantmentTests {
    static Game game;
    static Player ai;
    static int passed;

    static Card add(String name, Player p, ZoneType zone) {
        Card card = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), p);
        card.setGameTimestamp(p.getGame().getNextTimestamp());
        p.getZone(zone).add(card);
        return card;
    }

    static Card creature(String name, Player p) {
        return add(name, p, ZoneType.Battlefield);
    }

    static void newGame() {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LobbyPlayerAi l = new LobbyPlayerAi("Ai(" + (i + 1) + ")-test", null);
            l.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(l));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        Match match = new Match(rules, regs, "slaughter-the-strong fixtures");
        game = new Game(regs, rules, match);
        game.setAge(GameStage.Play);
        ai = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
    }


    static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
        passed++;
        System.out.println("PASS " + label);
    }

    static SpellAbility spell(String name) {
        Card card = add(name, ai, ZoneType.Hand);
        SpellAbility sa = card.getSpellAbilities().get(0);
        sa.setActivatingPlayer(ai);
        if (sa.getSubAbility() != null) { sa.getSubAbility().setActivatingPlayer(ai); }
        return sa;
    }

    static boolean willing(SpellAbility sa) {
        return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa).willingToPlay();
    }

    static void recovery() {
        newGame();
        SpellAbility sa = spell("Creeping Renaissance");
        check(!willing(sa), "empty graveyard: no cast");
        for (int i = 0; i < 3; i++) { add("Wild Growth", ai, ZoneType.Graveyard); }
        check(!willing(sa), "three of one type: no cast");
        add("Plains", ai, ZoneType.Graveyard);
        check(!willing(sa), "four total across types: no cast");
        add("Wild Growth", ai, ZoneType.Graveyard);
        check(willing(sa), "four enchantments: cast through native AI entry point");
        List<String> types = List.of("Creature", "Land", "Artifact", "Enchantment", "Battle", "Planeswalker");
        check("Enchantment".equals(ai.getController().chooseSomeType("Card", sa, types)),
                "native controller selects largest graveyard type");
        for (int i = 0; i < 5; i++) { add("Grizzly Bears", ai, ZoneType.Graveyard); }
        check("Creature".equals(ai.getController().chooseSomeType("Card", sa, types)),
                "five creatures beat four enchantments");
        check("Enchantment".equals(ai.getController().chooseSomeType("Card", sa, List.of("Land", "Enchantment"))),
                "choice respects engine supplied valid types");
        AbilityUtils.resolve(sa);
        check(ai.getCardsIn(ZoneType.Hand).stream().filter(c -> c.isCreature()).count() == 5,
                "native resolution returns all five chosen creatures to hand");
        check(ai.getCardsIn(ZoneType.Graveyard).size() == 5, "resolution leaves other types in graveyard");
        sa.getHostCard().setChosenType("Land"); // stale earlier choice must not affect the next decision
        check(willing(sa), "decision recounts remaining graveyard after resolution");
        AbilityUtils.resolve(sa);
        check("Enchantment".equals(sa.getHostCard().getChosenType()), "resolution recomputes choice");
        AiOverrideProfile.setActive("");
        check(!willing(sa), "no profile retains upstream missing-logic rejection");
        AiOverrideProfile.setActive("adaptive-enchantment");

        newGame();
        sa = spell("Creeping Renaissance");
        for (int i = 0; i < 4; i++) { add("Boon Satyr", ai, ZoneType.Graveyard); }
        check(willing(sa), "multitype enchantment creatures qualify");
        check("Creature".equals(ai.getController().chooseSomeType("Card", sa, types)),
                "equal multitype counts use deterministic type order");
        newGame();
        sa = spell("Creeping Renaissance");
        for (int i = 0; i < 4; i++) { add("Counterspell", ai, ZoneType.Graveyard); }
        for (int i = 0; i < 4; i++) { add("Wild Growth", game.getPlayers().get(1), ZoneType.Graveyard); }
        check(!willing(sa), "instants and opponent graveyard do not count");

        newGame();
        for (int i = 0; i < 4; i++) { add("Forest", ai, ZoneType.Graveyard); }
        Card card = add("Creeping Renaissance", ai, ZoneType.Graveyard);
        SpellAbility flashback = card.getAllPossibleAbilities(ai, false).stream()
                .filter(a -> a.isFlashback()).findFirst().orElseThrow();
        flashback.setActivatingPlayer(ai);
        check(willing(flashback), "same threshold allows native flashback ability");
    }

    static void targets() {
        for (String name : List.of("Dawn's Reflection", "Fertile Ground", "Wild Growth")) {
            newGame();
            Card nonbasic = add("Command Tower", ai, ZoneType.Battlefield);
            Card basic = add("Forest", ai, ZoneType.Battlefield);
            add("Plains", game.getPlayers().get(1), ZoneType.Battlefield);
            SpellAbility sa = spell(name);
            check(willing(sa), name + ": willing to enchant own land");
            check(sa.getTargetCard() == (name.equals("Wild Growth") ? nonbasic : basic),
                    name + ": requested land preference");
            newGame();
            nonbasic = add("Command Tower", ai, ZoneType.Battlefield);
            sa = spell(name);
            check(willing(sa) && sa.getTargetCard() == nonbasic, name + ": nonbasic fallback");
            newGame();
            add("Forest", game.getPlayers().get(1), ZoneType.Battlefield);
            sa = spell(name);
            check(!willing(sa), name + ": never ramp opponent when own lands absent");
        }
    }

    static void mana(String auraName, String cost) {
        mana(auraName, cost, "Plains");
    }

    static void mana(String auraName, String cost, String landName) {
        newGame();
        Card land = add(landName, ai, ZoneType.Battlefield);
        SpellAbility payment = AbilityFactory.getAbility("SP$ Draw | Cost$ " + cost + " | Defined$ You | NumCards$ 1",
                add("Divination", ai, ZoneType.Hand));
        payment.setActivatingPlayer(ai);
        check(!ComputerUtilMana.canPayManaCost(new ManaCostBeingPaid(new ManaCost(cost)), payment, ai, false),
                auraName + ": plain land cannot pay " + cost);
        Card aura = add(auraName, ai, ZoneType.Battlefield);
        aura.attachToEntity(land, null);
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        AiOverrideProfile.setActive("");
        if (landName.equals("Plains")) {
            check(!ComputerUtilMana.canPayManaCost(new ManaCostBeingPaid(new ManaCost(cost)), payment, ai, false),
                    auraName + ": no profile keeps upstream source-color behavior");
        }
        AiOverrideProfile.setActive("adaptive-enchantment");
        check(!ComputerUtilMana.canPayManaCost(new ManaCostBeingPaid(new ManaCost("W U B R G")), payment, ai, false),
                auraName + ": does not invent enough mana for five colors");
        check(!ComputerUtilMana.canPayManaCost(new ManaCostBeingPaid(new ManaCost("C")), payment, ai, false),
                auraName + ": colored bonus cannot pay true colorless");
        check(ComputerUtilMana.canPayManaCost(new ManaCostBeingPaid(new ManaCost(cost)), payment, ai, false),
                auraName + ": native planner recognizes extra mana for " + cost);
        check(ComputerUtilMana.payManaCost(new ManaCostBeingPaid(new ManaCost(cost)), payment, ai, false),
                auraName + ": native payment spends real triggered mana for " + cost);
        check(land.isTapped(), auraName + ": payment taps enchanted land");
        check(ManaAuraAi.chooseColor(ai, aura.getTriggers().get(0).ensureAbility(), forge.card.ColorSet.WUBRG) == 0,
                auraName + ": payment color context cleared");
        check(!ComputerUtilMana.canPayManaCost(new ManaCostBeingPaid(new ManaCost(cost)), payment, ai, false),
                auraName + ": tapped land cannot be reused");
    }

    static void stackedMana() {
        for (boolean reverse : List.of(false, true)) {
            newGame();
            Card land = add("Plains", ai, ZoneType.Battlefield);
            for (String name : reverse ? List.of("Wild Growth", "Fertile Ground") : List.of("Fertile Ground", "Wild Growth")) {
                add(name, ai, ZoneType.Battlefield).attachToEntity(land, null);
            }
            game.getAction().checkStaticAbilities();
            game.getTriggerHandler().resetActiveTriggers();
            game.copyLastState();
            SpellAbility sa = AbilityFactory.getAbility("SP$ Draw | Cost$ W G R | Defined$ You | NumCards$ 1",
                    add("Divination", ai, ZoneType.Hand));
            sa.setActivatingPlayer(ai);
            check(ComputerUtilMana.canPayManaCost(new ManaCostBeingPaid(new ManaCost("W G R")), sa, ai, false),
                    "stacked Auras predict distinct fixed and flexible mana, reverse=" + reverse);
            check(ComputerUtilMana.payManaCost(new ManaCostBeingPaid(new ManaCost("W G R")), sa, ai, false),
                    "stacked Auras pay W G R regardless of trigger order, reverse=" + reverse);
        }
    }

    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(7318));
        AiOverrideProfile.setActive("adaptive-enchantment");
        check(AiOverrideProfile.get().getDecks().size() == 2, "profile supplies Commander mirror decks");
        recovery();
        targets();
        mana("Wild Growth", "W G");
        mana("Fertile Ground", "W U");
        mana("Dawn's Reflection", "W U B");
        mana("Fertile Ground", "G U", "Mana Confluence");
        mana("Dawn's Reflection", "G U B", "Mana Confluence");
        stackedMana();
        AiOverrideProfile.setActive("");
        System.out.println("DONE " + passed + " checks");
    }
}
