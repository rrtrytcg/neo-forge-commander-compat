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
 * Bounded fixture for the hybrid filter mana ability capability, opted in by the NeoForge override
 * profile key {@code hybridFilterMana}.
 *
 * The default AI only knows how to pay a *generic* mana cost on a mana ability, so the Shadowmoor /
 * Eventide filter lands ("{X/Y}, {T}: Add two of X/Y") are invisible to it and the land only ever makes
 * {C}. This asserts the whole cycle is recognised once opted in, and unchanged when not.
 *
 * Consumes no game RNG.
 */
class FilterLandsTests {
    // The ten filter lands of the Shadowmoor/Eventide cycle.
    static final String[] LANDS = {
        "Cascade Bluffs", "Fetid Heath", "Fire-Lit Thicket", "Flooded Grove", "Graven Cairns",
        "Mystic Gate", "Rugged Prairie", "Sunken Ruins", "Twilight Mire", "Wooded Bastion"
    };

    static Game game;
    static Player ai;
    static int passed;

    static Card add(String name, Player p, ZoneType zone) {
        Card card = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), p);
        card.setGameTimestamp(p.getGame().getNextTimestamp());
        p.getZone(zone).add(card);
        return card;
    }

    static void newGame() {
        List<RegisteredPlayer> regs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LobbyPlayerAi l = new LobbyPlayerAi("Ai(" + (i + 1) + ")-test", null);
            l.setAiProfile("Default");
            regs.add(new RegisteredPlayer(new Deck()).setPlayer(l));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        Match match = new Match(rules, regs, "filter-land fixtures");
        game = new Game(regs, rules, match);
        game.setAge(GameStage.Play);
        ai = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
    }

    /** The mana ability that costs a mana symbol (the filter half), or null. */
    static SpellAbility filterAbility(Card land) {
        for (SpellAbility a : land.getManaAbilities()) {
            if (a.getPayCosts() != null && a.getPayCosts().hasManaCost()) {
                return a;
            }
        }
        return null;
    }

    static boolean hasFilter(Collection<SpellAbility> abilities) {
        for (SpellAbility a : abilities) {
            if (a.getPayCosts() != null && a.getPayCosts().hasManaCost()) {
                return true;
            }
        }
        return false;
    }

    /** A castable spell in the AI's hand, with the activating player set. */
    static SpellAbility spellInHand(String name) {
        Card card = add(name, ai, ZoneType.Hand);
        SpellAbility sa = card.getSpellAbilities().get(0);
        sa.setActivatingPlayer(ai);
        if (sa.getSubAbility() != null) {
            sa.getSubAbility().setActivatingPlayer(ai);
        }
        return sa;
    }

    static boolean canPay(SpellAbility sa) {
        return ComputerUtilMana.canPayManaCost(sa, ai, 0, false);
    }

    static void check(boolean cond, String name) {        if (!cond) {
            throw new AssertionError(name);
        }
        passed++;
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(6101));

        // ---- no profile: unchanged (the {C} ability only) ----
        AiOverrideProfile.setActive("");
        newGame();
        int offOnly = 0;
        int offCost = 0;
        for (String n : LANDS) {
            Card land = add(n, ai, ZoneType.Battlefield);
            List<SpellAbility> aiMana = ComputerUtilMana.getAIPlayableMana(land);
            if (aiMana.size() == 1 && !hasFilter(aiMana)) {
                offOnly++;
            }
            SpellAbility filter = filterAbility(land);
            if (filter != null && ComputerUtilMana.getGenericCostToActivate(filter) == 0) {
                offCost++;
            }
        }
        check(offOnly == LANDS.length, "off: every filter land offers only its {C} ability to the AI");
        check(offCost == LANDS.length, "off: the hybrid symbol still counts as free to activate");

        // ---- profile on: the whole family is recognised ----
        AiOverrideProfile.setActive("abzan-armor");
        newGame();
        int onBoth = 0;
        int onCost = 0;
        int onColor = 0;
        for (String n : LANDS) {
            Card land = add(n, ai, ZoneType.Battlefield);
            List<SpellAbility> aiMana = ComputerUtilMana.getAIPlayableMana(land);
            SpellAbility filter = filterAbility(land);
            if (aiMana.size() == 2 && hasFilter(aiMana)) {
                onBoth++;
            }
            if (filter != null && ComputerUtilMana.getGenericCostToActivate(filter) == 1) {
                onCost++;
            }
            // the filter half must be able to produce its two colours
            if (filter != null && filter.getManaPart() != null) {
                String produced = filter.getManaPart().getComboColors(filter);
                if (produced.split(" ").length == 2) {
                    onColor++;
                }
            }
        }
        check(onBoth == LANDS.length, "on: every filter land exposes its hybrid filter ability");
        check(onCost == LANDS.length, "on: the hybrid symbol counts as one mana to activate");
        check(onColor == LANDS.length, "on: every filter half offers both of its colours");

        // ---- loader ----
        AiOverrideProfile.setActive("");
        Card mire = add("Twilight Mire", ai, ZoneType.Hand);
        check(!AiOverrideProfile.get().allowHybridFilterMana(mire), "loader: no hybrid opt-in without a profile");
        AiOverrideProfile.setActive("abzan-armor");
        check(AiOverrideProfile.get().allowHybridFilterMana(mire), "loader: profile opts Twilight Mire in");

        // ---- runtime payment: a coloured source plus a filter land pays a double shard ----
        AiOverrideProfile.setActive("");
        newGame();
        add("Forest", ai, ZoneType.Battlefield);
        add("Twilight Mire", ai, ZoneType.Battlefield);
        check(!canPay(spellInHand("Kalonian Tusker")),
                "off: a Forest plus a filter land cannot pay {G}{G}");

        AiOverrideProfile.setActive("abzan-armor");
        newGame();
        add("Forest", ai, ZoneType.Battlefield);
        add("Twilight Mire", ai, ZoneType.Battlefield);
        check(canPay(spellInHand("Kalonian Tusker")),
                "on: a Forest plus a filter land pays {G}{G} through the filter");

        newGame();
        add("Swamp", ai, ZoneType.Battlefield);
        add("Twilight Mire", ai, ZoneType.Battlefield);
        check(canPay(spellInHand("Hymn to Tourach")),
                "on: a Swamp plus a filter land pays {B}{B} through the filter");

        AiOverrideProfile.setActive("");

        System.out.println("DONE " + passed + " checks");
    }
}
