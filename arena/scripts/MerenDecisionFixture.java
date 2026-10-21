import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.LobbyPlayerAi;
import forge.ai.ability.ChangeZoneAi;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.AbilityFactory;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

class MerenDecisionFixture {
    static Card add(String name, Player player, ZoneType zone) {
        Card card = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), player);
        card.setGameTimestamp(player.getGame().getNextTimestamp());
        player.getZone(zone).add(card);
        return card;
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        MyRandom.setRandom(new Random(3001));
        List<RegisteredPlayer> registrations = new ArrayList<>();
        for (String name : List.of("classic", "opponent")) {
            LobbyPlayerAi lobby = new LobbyPlayerAi(name, null);
            lobby.setAiProfile("Default");
            registrations.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Match match = new Match(rules, registrations, "Controlled diagnostic fixture");
        Game game = new Game(registrations, rules, match);
        game.setAge(GameStage.Play);
        Player ai = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, ai);
        ai.setLife(3, null);
        ai.setCounters(CounterType.getType("Experience"), 2);
        Card meren = add("Meren of Clan Nel Toth", ai, ZoneType.Battlefield);
        meren.setTapped(true);
        for (String name : List.of("Forest", "Forest", "Swamp", "Swamp")) add(name, ai, ZoneType.Battlefield).setTapped(true);
        Card wall = add("Wall of Blossoms", ai, ZoneType.Graveyard);
        Card force = add("Verdant Force", ai, ZoneType.Graveyard);
        add("Phyrexian Rager", opponent, ZoneType.Battlefield);
        add("Forest", ai, ZoneType.Library);
        SpellAbility ability = AbilityFactory.getAbility(meren, "TrigChangeZone");
        ability.setActivatingPlayer(ai);
        ability.setTrigger(meren.getTriggers().stream().filter(t -> "TrigChangeZone".equals(t.getParam("Execute"))).findFirst().orElseThrow());
        System.out.println("FIXTURE seed=3001 classic=unchanged Default; own end step; life=3; experience=2; Meren tapped; four tapped lands; hand empty; opponent Phyrexian Rager 2/2; library Forest");
        for (Card candidate : List.of(wall, force)) {
            if (!ability.canTarget(candidate)) throw new IllegalStateException("Expected legal candidate");
            ability.resetTargets();
            ability.getTargets().add(candidate);
            boolean battlefield = ability.getConditions().areMet(ability);
            boolean hand = ability.getSubAbility().getConditions().areMet(ability.getSubAbility());
            System.out.println("OPTION " + candidate.getName() + " mv=" + candidate.getCMC() + " legal=true engineBattlefieldCondition=" + battlefield + " engineHandCondition=" + hand);
            if (battlefield != (candidate == wall) || hand != (candidate == force)) throw new IllegalStateException("Unexpected Meren branch eligibility");
        }
        ability.resetTargets();
        boolean accepted = new ChangeZoneAi().doTrigger(ai, ability, true);
        System.out.println("DECISION accepted=" + accepted + " targets=" + ability.getTargets());
        if (!accepted || !ability.getTargets().getTargetCards().contains(force)) throw new IllegalStateException("Weak decision no longer reproduced");
        System.out.println("REPRODUCED: selects eight-mana Verdant Force for hand instead of free Wall of Blossoms battlefield return with draw trigger and available blocker; four lands cannot cast Verdant Force next turn.");
        System.out.println("This is a controlled choice diagnostic, not a played counterfactual or evidence of win-rate improvement.");
        System.exit(0);
    }
}
