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
 * Bounded fixture for Psychosis Crawler, opted in by the NeoForge override profile.
 *
 * The requested rule - cast when you can, but only with at least three cards in hand counting
 * Psychosis Crawler itself, otherwise hold - is already in the card script as
 * SVar:NeedsToPlayVar:X GE3 with X:Count$ValidHand Card.YouOwn, and AiController enforces it through
 * ComputerUtilCard.checkNeedsToPlayReqs. The profile's only job is to stop AI:RemoveDeck:All from
 * removing the card from the AI's play list, so this asserts both the script rule and the un-blocking.
 *
 * Consumes no game RNG.
 */
class PsychosisCrawlerTests {
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
        Match match = new Match(rules, regs, "psychosis-crawler fixtures");
        game = new Game(regs, rules, match);
        game.setAge(GameStage.Play);
        ai = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        for (int i = 0; i < 5; i++) {
            add("Forest", ai, ZoneType.Battlefield); // Psychosis Crawler costs {5}
        }
    }

    /** Psychosis Crawler plus (total - 1) filler cards, so the hand holds exactly `total`. */
    static Card crawlerInHand(int total) {
        Card crawler = add("Psychosis Crawler", ai, ZoneType.Hand);
        for (int i = 1; i < total; i++) {
            add("Grizzly Bears", ai, ZoneType.Hand);
        }
        SpellAbility sa = crawler.getSpellAbilities().get(0);
        sa.setActivatingPlayer(ai);
        return crawler;
    }

    static AiPlayDecision needsToPlay(Card crawler) {
        return ComputerUtilCard.checkNeedsToPlayReqs(crawler, crawler.getSpellAbilities().get(0));
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
        MyRandom.setRandom(new Random(4401));
        AiOverrideProfile.setActive("");

        // The scripted rule, independent of the profile.
        newGame();
        check(needsToPlay(crawlerInHand(3)) == AiPlayDecision.WillPlay,
                "rule: three cards in hand including the Crawler is enough");
        newGame();
        check(needsToPlay(crawlerInHand(4)) == AiPlayDecision.WillPlay, "rule: four cards is enough");
        newGame();
        check(needsToPlay(crawlerInHand(2)) != AiPlayDecision.WillPlay,
                "rule: two cards in hand including the Crawler holds it");

        // A single card in hand means the Crawler would enter as a 1/1 and die; hold it.
        newGame();
        check(needsToPlay(crawlerInHand(1)) != AiPlayDecision.WillPlay, "rule: an empty hand holds it");

        // Un-blocking: the profile must stop AI:RemoveDeck:All from removing the card.
        newGame();
        Card crawler = crawlerInHand(3);
        AiOverrideProfile.setActive("");
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(crawler),
                "loader: no un-blocking without a profile");
        AiOverrideProfile.setActive("abzan-armor");
        check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(crawler),
                "loader: the profile un-blocks Psychosis Crawler");

        // Whether the card actually gets cast once un-blocked is shown by the sim
        // (arena/psychosis-crawler/decks/PsychosisCrawlerProbe.dck); a creature spell's play decision
        // comes from AiController.canPlayCard, which ends in the checkNeedsToPlayReqs asserted above.

        AiOverrideProfile.setActive("");
        System.out.println("DONE " + passed + " checks");
    }
}
