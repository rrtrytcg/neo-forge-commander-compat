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

/**
 * Bounded fixture for the Beast Within AI rules, opted in by the NeoForge override profile
 * (aiLogic=Beast Within=BeastWithin).
 *
 * Target priorities, best first:
 *   enchanted/equipped creature that is 4/4+; plain 4/4+ creature; smaller enchanted/equipped
 *   creature; enchantment costing 4+; any other permanent costing 4+; any enchanted land.
 * With no match the AI must not cast (the destroyed permanent's controller gets a 3/3).
 *
 * Consumes no game RNG.
 */
class BeastWithinTests {
    static Game game;
    static Player ai;
    static int passed;

    static Card add(String name, Player p, ZoneType zone) {
        Card card = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), p);
        card.setGameTimestamp(p.getGame().getNextTimestamp());
        p.getZone(zone).add(card);
        return card;
    }

    static SpellAbility beastWithin() {
        Card card = add("Beast Within", ai, ZoneType.Hand);
        SpellAbility sa = card.getSpellAbilities().get(0);
        sa.setActivatingPlayer(ai);
        if (sa.getSubAbility() != null) {
            sa.getSubAbility().setActivatingPlayer(ai);
        }
        return sa;
    }

    static Player opp(int i) {
        return game.getPlayers().get(i);
    }

    static void newGame() {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LobbyPlayerAi l = new LobbyPlayerAi("Ai(" + (i + 1) + ")-test", null);
            l.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(l));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        Match match = new Match(rules, regs, "beast-within fixtures");
        game = new Game(regs, rules, match);
        game.setAge(GameStage.Play);
        ai = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        for (int i = 0; i < 3; i++) {
            add("Forest", ai, ZoneType.Battlefield);
        }
    }

    static boolean willing(SpellAbility sa) {
        return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa).willingToPlay();
    }

    static Card chosenTarget(SpellAbility sa) {
        for (Card c : sa.getTargets().getTargetCards()) {
            return c;
        }
        return null;
    }

    /** Run the decision, then return the target it picked. */
    static Card decidedTarget(SpellAbility sa) {
        willing(sa);
        return chosenTarget(sa);
    }

    static Card attach(String attachment, Card host, Player owner) {
        Card card = add(attachment, owner, ZoneType.Battlefield);
        card.attachToEntity(host, null);
        return card;
    }

    static void check(boolean cond, String name) {
        if (!cond) {
            throw new AssertionError(name);
        }
        passed++;
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(5201));
        AiOverrideProfile.setActive("");

        // Leatherback Baloth is 4/5 for {G}{G}{G}, mana value 3: "4/4 and larger", below "4 mana+".
        // The default AI filters on AITgts$ Card.cmcGE4, so it cannot see this target at all.
        newGame();
        Card baloth = add("Leatherback Baloth", opp(1), ZoneType.Battlefield);
        SpellAbility off = beastWithin();
        check(!willing(off), "off: a 4/5 with mana value 3 is not a default target");

        newGame();
        Card baloth2 = add("Leatherback Baloth", opp(1), ZoneType.Battlefield);
        SpellAbility on = beastWithin();
        AiOverrideProfile.setActive("abzan-armor");
        check(willing(on), "on: a 4/4-or-larger creature is targeted whatever its mana value");
        check(chosenTarget(on) == baloth2, "on: the 4/5 is the chosen target");

        // An equipped creature outranks a plain big creature.
        newGame();
        Card big = add("Leatherback Baloth", opp(1), ZoneType.Battlefield);
        Card small = add("Grizzly Bears", opp(1), ZoneType.Battlefield);
        attach("Bonesplitter", small, opp(1));
        check(small.isEquipped(), "setup: the equipment is attached");
        SpellAbility sa2 = beastWithin();
        AiOverrideProfile.setActive("abzan-armor");
        check(willing(sa2), "priority: casts with an equipped creature present");
        check(decidedTarget(sa2) == small, "priority: equipped creature outranks a plain 4/5");

        // An enchanted creature also outranks a plain big creature.
        newGame();
        Card big3 = add("Leatherback Baloth", opp(1), ZoneType.Battlefield);
        Card small3 = add("Grizzly Bears", opp(1), ZoneType.Battlefield);
        attach("Rancor", small3, opp(1));
        check(!small3.getAttachedCards().isEmpty(), "setup: the aura is attached");
        SpellAbility sa3 = beastWithin();
        AiOverrideProfile.setActive("abzan-armor");
        check(decidedTarget(sa3) == small3, "priority: enchanted creature outranks a plain 4/5");

        // A big creature outranks a big enchantment, which outranks other big permanents.
        newGame();
        Card enchant = add("Doubling Season", opp(1), ZoneType.Battlefield); // enchantment, mana value 5
        Card artifact = add("Icy Manipulator", opp(1), ZoneType.Battlefield); // artifact, mana value 4
        Card big4 = add("Leatherback Baloth", opp(1), ZoneType.Battlefield);
        SpellAbility sa4 = beastWithin();
        AiOverrideProfile.setActive("abzan-armor");
        check(decidedTarget(sa4) == big4, "priority: creature over enchantment over other permanents");

        newGame();
        Card enchant5 = add("Doubling Season", opp(1), ZoneType.Battlefield);
        Card artifact5 = add("Icy Manipulator", opp(1), ZoneType.Battlefield);
        SpellAbility sa5 = beastWithin();
        AiOverrideProfile.setActive("abzan-armor");
        check(decidedTarget(sa5) == enchant5, "priority: enchantment costing 4+ over other permanents costing 4+");

        // An enchanted land is a target even though it has no mana value.
        newGame();
        Card land = add("Forest", opp(1), ZoneType.Battlefield);
        attach("Wild Growth", land, opp(1));
        SpellAbility sa6 = beastWithin();
        AiOverrideProfile.setActive("abzan-armor");
        check(willing(sa6), "priority: an enchanted land is a castable target");
        check(decidedTarget(sa6) == land, "priority: the enchanted land is the chosen target");

        // Nothing on the list: do not hand out a 3/3 for free.
        newGame();
        add("Grizzly Bears", opp(1), ZoneType.Battlefield); // 2/2
        Card ring = add("Sol Ring", opp(1), ZoneType.Battlefield); // mana value 1
        Card plain = add("Forest", opp(1), ZoneType.Battlefield); // unechanted land
        SpellAbility sa7 = beastWithin();
        AiOverrideProfile.setActive("abzan-armor");
        check(!willing(sa7), "no qualifying target: does not cast");

        AiOverrideProfile.setActive("");
        System.out.println("DONE " + passed + " checks");
    }
}
